/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.inheritancetaxonpensions.services

import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import uk.gov.hmrc.http.HeaderCarrier
import models.{IhtpOverviewReport, IhtpOverviewResponse, IhtpOverviewSuccess}
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants
import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.utils.UserAnswersHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.Right

import java.time._

@Singleton
class ReportRetrievalService @Inject() (
  userAnswersRepository: UserAnswersRepository,
  ihtpReportConnector: IhtpReportConnector
) {

  def getOverview(pstr: String, srn: String, dateFrom: String, dateTo: String, status: Option[String])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[ErrorResponse, IhtpOverviewResponse]] =
    val downstreamEitherF: Future[Either[ErrorResponse, IhtpOverviewResponse]] =
      ihtpReportConnector.getOverview(pstr, dateFrom, dateTo, status)

    val inProgressF: Future[Seq[UserAnswers]] =
      userAnswersRepository.findBySrn(srn).recover { case e =>
        Seq.empty
      }

    for {
      dsEither <- downstreamEitherF
      inProgress <- inProgressF
    } yield dsEither match {
      case Right(ds) =>
        val filtered = inProgress
          .filter(ua =>
            val draftPaymentReference = UserAnswersHelper.getOptional(ua, "ihtPaymentReference").getOrElse("fail")
            val draftLastUpdated = ua.lastUpdated
            !ds.success.ihtpOverview.exists(item =>
              (item.paymentReference.getOrElse("test") == draftPaymentReference) &&
                item.submissionDate
                  .getOrElse(Instant.EPOCH)
                  .isAfter(draftLastUpdated.atZone(ZoneId.of("Europe/Paris")).toInstant)
            )
          )
        val mappedInProgress = filtered
          .map(ua =>
            IhtpOverviewReport(
              uuid = Some(ua.uuid),
              fbNumber = None,
              submissionDate = None,
              paymentDueDate = Some(
                UserAnswersHelper
                  .getOptionalAs[LocalDate](ua, s"${ihtTaxInformationPath}.${noticeToPayDatePath}")
                  .getOrElse(LocalDate.now())
                  .plusDays(dueDateDifferenceInDays)
              ),
              ihtpVersion = "000", // fixed value
              inheritanceTaxReference = UserAnswersHelper.getMandatoryAs[String](ua, inheritanceTaxReferenceNumberPath),
              paymentReference = None,
              title = None, // likely not in final version of ETMP payload
              firstForename = UserAnswersHelper.getOptional(ua, s"${deceasedDetailsPath}.${deceasedFirstForename}"),
              secondForename = None, // likely not in final version of ETMP payload
              surname = Some(
                UserAnswersHelper
                  .getOptional(ua, s"${deceasedDetailsPath}.${deceasedSurname}")
                  .getOrElse(
                    // fallback if only the first page of the journey was saved
                    UserAnswersHelper.getOptional(ua, Constants.inheritanceTaxReferenceNumberPath).getOrElse("")
                  )
              ),
              nino = None,
              ihtpStatus = "In progress" // fixed value
            )
          )
        Right(IhtpOverviewResponse(IhtpOverviewSuccess(mappedInProgress ++ ds.success.ihtpOverview)))
      case Left(e) =>
        Left(e)
    }
}
