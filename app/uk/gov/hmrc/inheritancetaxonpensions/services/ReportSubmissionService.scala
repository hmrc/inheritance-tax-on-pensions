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
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo
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

    val deceasedDetailsPath = Constants.deceasedDetailsPath

    val deceasedTitle = UserAnswersHelper.getOptional(
      userAnswers,
      s"$deceasedDetailsPath.${Constants.deceasedTitle}"
    )
    val deceasedFirstForename = UserAnswersHelper.getMandatory(
      userAnswers,
      s"$deceasedDetailsPath.${Constants.deceasedFirstForename}"
    )
    val deceasedSecondForename = UserAnswersHelper.getOptional(
      userAnswers,
      s"$deceasedDetailsPath.${Constants.deceasedSecondForename}"
    )
    val deceasedSurname = UserAnswersHelper.getMandatory(
      userAnswers,
      s"$deceasedDetailsPath.${Constants.deceasedSurname}"
    )
    val ninoOrReasonAnswers = UserAnswersHelper.getMandatoryAs[NinoOrReasonAnswers](
      userAnswers,
      Constants.ninoOrReasonPath
    )
    val birthDeathDates = UserAnswersHelper.getMandatoryAs[BirthDeathDates](
      userAnswers,
      Constants.birthDeathDatesPath
    )
    val lprType = UserAnswersHelper.getMandatory(userAnswers, "lprType")
    val lprDetails = buildLprDetails(userAnswers, lprType)

    val reportDetails = ReportDetails(
      pstr = pstr
    )

    val deceasedDetails = DeceasedDetails(
      inheritanceTaxReference = inheritanceTaxReferenceNumber,
      title = deceasedTitle,
      firstForename = deceasedFirstForename,
      secondForename = deceasedSecondForename,
      surname = deceasedSurname,
      dateOfBirth = birthDeathDates.dateOfBirth,
      dateOfDeath = birthDeathDates.dateOfDeath,
      nino = ninoOrReasonAnswers.nino,
      reasonForNoNino = ninoOrReasonAnswers.reasonForNoNino
    )

    IhtpReportSubmission(reportDetails, deceasedDetails, lprDetails, buildIhtTaxInformation(userAnswers))
  }

  private def buildLprDetails(userAnswers: UserAnswers, lprType: String): LprDetails =
    lprType match {
      case "organisation" =>
        val organisationDetails = UserAnswersHelper.getMandatoryAs[OrganisationDetails](
          userAnswers,
          "lprDetails.organisation"
        )
        LprDetails(None, Some(organisationDetails))
      case "individual" =>
        val individualDetails = UserAnswersHelper.getMandatoryAs[IndividualDetails](
          userAnswers,
          "lprDetails.individual"
        )
        LprDetails(Some(individualDetails), None)
    }

  private def buildIhtTaxInformation(userAnswers: UserAnswers): IhtTaxInformation =
    IhtTaxInformation(
      dateThePensionSchemeReceivedNoticeToPay = UserAnswersHelper.getMandatory(
        userAnswers,
        "ihtTaxInformation.dateThePensionSchemeReceivedNoticeToPay"
      ),
      didTheLegalPersonalRepresentativeSubmitTheNotice = YesNo(
        UserAnswersHelper.getMandatoryAs[Boolean](
          userAnswers,
          "didPrSubmit"
        )
      )
    )
}
