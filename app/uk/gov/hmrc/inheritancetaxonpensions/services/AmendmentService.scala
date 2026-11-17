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
import play.api.http.Status
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.inheritancetaxonpensions.models._
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants
import com.google.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmendmentService @Inject() (
  ihtpReportConnector: IhtpReportConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def applyChangeFlags(
    submission: IhtpPaymentNoticeSubmission,
    pstr: String,
    userAnswers: UserAnswers
  )(implicit hc: HeaderCarrier): Future[Either[ErrorResponse, IhtpPaymentNoticeSubmission]] =
    fetchPreviousVersion(pstr, userAnswers).map {
      case Left(errorResponse) => Left(errorResponse)
      case Right(previousVersion) => Right(setChangeFlags(submission, previousVersion))
    }

  private def fetchPreviousVersion(pstr: String, userAnswers: UserAnswers)(implicit
    hc: HeaderCarrier
  ): Future[Either[ErrorResponse, Option[IhtNoticeResponse]]] =
    (
      (userAnswers.data \ Constants.ihtVersionPath).asOpt[String],
      (userAnswers.data \ Constants.ihtPaymentReferencePath).asOpt[String]
    ) match {
      case (Some(version), Some(ihtPaymentReference)) if version.toIntOption.exists(_ > 1) =>
        val previousVersionNumber = f"${version.toInt - 1}%03d"
        ihtpReportConnector
          .getReport(pstr, None, Some(ihtPaymentReference), Some(previousVersionNumber))
          .map { response =>
            if (response.status == Status.OK) {
              (response.json \ "ihtNoticeResponse").asOpt[IhtNoticeResponse] match {
                case Some(previous) => Right(Some(previous))
                case None => Left(ErrorCodes.unexpectedResponse)
              }
            } else {
              logger.warn(
                s"[AmendmentService][fetchPreviousVersion] Previous report version fetch failed, status: ${response.status}"
              )
              Left(ErrorCodes.unexpectedResponse)
            }
          }
      case _ => Future.successful(Right(None))
    }

  private def setChangeFlags(
    submission: IhtpPaymentNoticeSubmission,
    previousVersion: Option[IhtNoticeResponse]
  ): IhtpPaymentNoticeSubmission =
    previousVersion match {
      case Some(previous) =>
        val request = submission.ihtNoticeRequest
        val deceasedChangeFlag =
          if (request.deceased.copy(deceasedChangeFlag = None) != previous.deceased.copy(deceasedChangeFlag = None))
            Some(YesNo.Yes)
          else None
        val prChangeFlag =
          if (request.personalRep.copy(prChangeFlag = None) != previous.personalRep.copy(prChangeFlag = None))
            Some(YesNo.Yes)
          else None
        val ihTaxChangeFlag =
          if (
            request.ihTaxInformation.copy(ihTaxChangeFlag = None) != previous.ihTaxInformation
              .copy(ihTaxChangeFlag = None)
          )
            Some(YesNo.Yes)
          else None
        val beneficiariesChangeFlag =
          if (beneficiariesForComparison(request.beneficiary) != beneficiariesForComparison(previous.beneficiary))
            Some(YesNo.Yes)
          else None
        submission.copy(
          ihtNoticeRequest = request.copy(
            deceased = request.deceased.copy(deceasedChangeFlag = deceasedChangeFlag),
            personalRep = request.personalRep.copy(prChangeFlag = prChangeFlag),
            ihTaxInformation = request.ihTaxInformation.copy(ihTaxChangeFlag = ihTaxChangeFlag),
            beneficiary = request.beneficiary.map(_.copy(beneficiariesChangeFlag = beneficiariesChangeFlag))
          )
        )
      case None => submission
    }

  private def beneficiariesForComparison(beneficiaries: Option[Beneficiaries]): Option[Beneficiaries] =
    beneficiaries.map(block => block.copy(beneficiariesChangeFlag = None))
}
