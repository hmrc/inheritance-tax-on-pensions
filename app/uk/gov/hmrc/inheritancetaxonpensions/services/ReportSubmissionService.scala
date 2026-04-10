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
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.utils.UserAnswersHelper
import uk.gov.hmrc.inheritancetaxonpensions.models._
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants
import com.google.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportSubmissionService @Inject() (
  userAnswersRepository: UserAnswersRepository,
  ihtpReportConnector: IhtpReportConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def submitReport(userAnswersId: String, pstr: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[ErrorResponse, IhtpReportSubmissionResponse]] =
    for {
      userAnswersOpt <- userAnswersRepository.get(userAnswersId)
      result <- userAnswersOpt match {
        case Some(userAnswers) =>
          val submissionPayLoad = buildSubmissionPayload(userAnswers, pstr)
          ihtpReportConnector.submitReport(submissionPayLoad)

        case None =>
          logger.warn(s"[ReportSubmissionService][submitReport] User answers not found for id: $userAnswersId")
          Future.successful(Left(ErrorCodes.entityNotFound))
      }
    } yield result

  private def buildSubmissionPayload(userAnswers: UserAnswers, pstr: String): IhtpReportSubmission = {
    val inheritanceTaxReferenceNumber = UserAnswersHelper.getMandatory(
      userAnswers,
      Constants.inheritanceTaxReferenceNumberPath
    )

    val reportDetails = ReportDetails(
      pstr = pstr,
      inheritanceTaxReference = inheritanceTaxReferenceNumber
    )

    IhtpReportSubmission(reportDetails)
  }
}
