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

import uk.gov.hmrc.inheritancetaxonpensions.auth.IhtpAuthContext
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.utils.UserAnswersHelper
import uk.gov.hmrc.inheritancetaxonpensions.models._
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.{IndividualOrOrg, IndividualOrTrust, YesNo}
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import uk.gov.hmrc.inheritancetaxonpensions.validators.{JSONSchemaValidator, SchemaPaths}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants
import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportSubmissionService @Inject() (
  userAnswersRepository: UserAnswersRepository,
  jsonPayloadSchemaValidator: JSONSchemaValidator,
  ihtpReportConnector: IhtpReportConnector
)(implicit ec: ExecutionContext)
    extends Logging {

  def submitReport(userAnswersId: String, pstr: String, ihtpAuthContext: IhtpAuthContext[Any])(implicit
    hc: HeaderCarrier
  ): Future[Either[ErrorResponse, IhtpPaymentNoticeResponse]] =
    for {
      userAnswersOpt <- userAnswersRepository.get(userAnswersId)
      result <- userAnswersOpt match {
        case Some(userAnswers) =>
          val submissionPayLoad = buildSubmissionPayload(userAnswers, pstr, ihtpAuthContext)
          val payloadAsJson = Json.toJson(submissionPayLoad)
          val schema = SchemaPaths.INTERNAL_v0_16
          val validationResult = jsonPayloadSchemaValidator.validatePayload(schema, payloadAsJson)
          if (validationResult.hasErrors) {
            throw SchemaValidationFailureException(
              s"Invalid payload - validationErrors:" +
                s"${validationResult.toString}"
            )
          } else {
            ihtpReportConnector.submitReport(submissionPayLoad)
          }
        case None =>
          logger.warn(s"[ReportSubmissionService][submitReport] User answers not found for id: $userAnswersId")
          Future.successful(Left(ErrorCodes.entityNotFound))
      }
    } yield result

  private def buildSubmissionPayload(
    userAnswers: UserAnswers,
    pstr: String,
    ihtpAuthContext: IhtpAuthContext[Any]
  ): IhtpPaymentNoticeSubmission = {
    val inheritanceTaxReferenceNumber =
      UserAnswersHelper.getMandatory(userAnswers, Constants.inheritanceTaxReferenceNumberPath)
    val deceasedChangeFlag = UserAnswersHelper.getOptionalAs[YesNo](userAnswers, Constants.deceasedChangeFlag)
    val deceasedTitle = UserAnswersHelper.getOptional(
      userAnswers,
      s"${Constants.nameOfDeceasedPath}.${Constants.deceasedTitle}"
    )
    val deceasedFirstForename = UserAnswersHelper.getMandatory(
      userAnswers,
      s"${Constants.nameOfDeceasedPath}.${Constants.deceasedFirstForename}"
    )
    val deceasedSecondForename = UserAnswersHelper.getOptional(
      userAnswers,
      s"${Constants.nameOfDeceasedPath}.${Constants.deceasedSecondForename}"
    )
    val deceasedSurname = UserAnswersHelper.getMandatory(
      userAnswers,
      s"${Constants.nameOfDeceasedPath}.${Constants.deceasedSurname}"
    )

    val hasNino = UserAnswersHelper.getMandatoryAs[Boolean](userAnswers, Constants.hasNinoPath)
    val (nino, reasonForNoNino) =
      if (hasNino) {
        (Some(UserAnswersHelper.getMandatory(userAnswers, Constants.ninoPath)), None)
      } else {
        (None, Some(UserAnswersHelper.getMandatory(userAnswers, Constants.reasonForNoNinoPath)))
      }

    val birthDeathDates = UserAnswersHelper.getMandatoryAs[BirthDeathDates](
      userAnswers,
      Constants.birthDeathDatesPath
    )
    val prDetails = buildPrDetails(userAnswers)

    val beneficiaryList = buildBeneficiaryList(userAnswers)
    val declarations = buildDeclarations(ihtpAuthContext)

    val reportDetails = ReportDetails(
      pstr = pstr,
      ihtPaymentReference = None
    )

    val deceased = Deceased(
      deceasedChangeFlag = deceasedChangeFlag match {
        case Some(changeFlag) => Some(changeFlag)
        case _ => None
      },
      deceasedPersonalDetails = DeceasedPersonalDetails(
        title = deceasedTitle,
        firstForename = deceasedFirstForename,
        secondForename = deceasedSecondForename,
        surname = deceasedSurname,
        ninoExist = YesNo(hasNino),
        nino = nino match {
          case Some(ni) => Some(ni)
          case _ => None
        },
        reasonNoNINO = reasonForNoNino match {
          case Some(reason) => Some(reason)
          case _ => None
        }
      ),
      deceasedDetails = DeceasedDetails(
        deceasedsDOB = birthDeathDates.dateOfBirth,
        deceasedsDOD = birthDeathDates.dateOfDeath,
        ihtRefNumber = inheritanceTaxReferenceNumber
      )
    )

    IhtpPaymentNoticeSubmission(
      reportDetails,
      deceased,
      prDetails,
      buildIhTaxInformation(userAnswers),
      beneficiaryList,
      declarations
    )
  }

  private def buildPrDetails(userAnswers: UserAnswers): PrDetails =
    val prChangeFlag = UserAnswersHelper.getOptionalAs[YesNo](userAnswers, "prDetails.prChangeFlag")
    val prType = UserAnswersHelper.getMandatory(userAnswers, "prType")
    val (prContactDetails, prAddress) = prType match {
      case "organisation" =>
        val organisationDetails =
          UserAnswersHelper.getMandatoryAs[OrganisationDetails](userAnswers, "prDetails.organisation")
        (
          PrContactDetails(
            orgName = Some(organisationDetails.info.organisationName),
            title = organisationDetails.info.title,
            firstForename = organisationDetails.info.firstForename,
            secondForename = organisationDetails.info.secondForename,
            surname = organisationDetails.info.surname
          ),
          organisationDetails.address.copy(postcode =
            UserAnswersHelper.getOptionalAs[String](userAnswers, "prDetails.organisation.ukPostcode")
          )
        )
      case "individual" =>
        val individualDetails = UserAnswersHelper.getMandatoryAs[IndividualDetails](
          userAnswers,
          "prDetails.individual"
        )
        (
          PrContactDetails(
            title = individualDetails.name.title,
            firstForename = individualDetails.name.firstForename,
            secondForename = individualDetails.name.secondForename,
            surname = individualDetails.name.surname
          ),
          individualDetails.address.copy(postcode =
            UserAnswersHelper.getOptionalAs[String](userAnswers, "prDetails.individual.ukPostcode")
          )
        )
    }

    PrDetails(prChangeFlag, IndividualOrOrg(prType), prContactDetails, prAddress)

  private def buildIhTaxInformation(userAnswers: UserAnswers): IhTaxInformation =
    IhTaxInformation(
      ihTaxChangeFlag = UserAnswersHelper.getOptionalAs[YesNo](userAnswers, "ihtTaxInformation.ihTaxChangeFlag"),
      dateNoticeReceived = UserAnswersHelper.getMandatory(
        userAnswers,
        "ihtTaxInformation.dateThePensionSchemeReceivedNoticeToPay"
      ),
      noticeSubmittedByPR = YesNo(
        UserAnswersHelper.getMandatoryAs[Boolean](
          userAnswers,
          "didPrSubmit"
        )
      ),
      knownBeneficiaries = UserAnswersHelper
        .getOptionalAs[Boolean](
          userAnswers,
          "areBeneficiariesKnown"
        )
        .map(YesNo.apply),
      totalIHTPayable = UserAnswersHelper.getOptionalAs[Double](
        userAnswers,
        "ihtTaxInformation.totalIHTPayable"
      ),
      totalInterestPayable = UserAnswersHelper.getOptionalAs[Double](
        userAnswers,
        "ihtTaxInformation.totalInterestPayable"
      ),
      total = UserAnswersHelper.getOptionalAs[Double](
        userAnswers,
        "ihtTaxInformation.total"
      )
    )

  private def buildBeneficiaryList(userAnswers: UserAnswers): Option[Seq[BeneficiaryDetails]] = {
    val beneficiariesArray = userAnswers.data \ "beneficiaries"
    if (beneficiariesArray.isDefined) {
      Some(beneficiariesArray.as[Seq[JsObject]].flatMap { beneficiary =>
        val beneficiaryType = (beneficiary \ "beneficiaryType").as[String]
        beneficiaryType match {
          case "individual" =>
            val individualName = (beneficiary \ "beneficiaryDetails" \ "individual").as[IndividualName]
            Some(
              buildBeneficiaryDetails(
                beneficiaryType = beneficiaryType,
                beneficiaryTrstName = None,
                personalDetails = BeneficiaryPersonalDetails(
                  title = individualName.title,
                  firstForename = individualName.firstForename,
                  secondForename = individualName.secondForename,
                  surname = individualName.surname,
                  ninoExist = YesNo.No,
                  // TODO update once beneficiary nino or reason for no nino are captured
                  nino = None,
                  reasonNoNINO = Some("TODO")
                )
              )
            )
          case "trust" =>
            val beneficiaryTrstName =
              (beneficiary \ "beneficiaryDetails" \ "trust" \ "beneficiaryTrstName").as[String]
            Some(
              buildBeneficiaryDetails(
                beneficiaryType = beneficiaryType,
                beneficiaryTrstName = Some(beneficiaryTrstName),
                // TODO update once the trust beneficiary contact details are captured
                personalDetails = BeneficiaryPersonalDetails(
                  title = None,
                  firstForename = "TODO",
                  secondForename = None,
                  surname = "TODO",
                  ninoExist = YesNo.No,
                  nino = None,
                  reasonNoNINO = Some("TODO")
                )
              )
            )
          case _ => None
        }
      })
    } else {
      None
    }
  }

  private def buildBeneficiaryDetails(
    beneficiaryType: String,
    beneficiaryTrstName: Option[String],
    personalDetails: BeneficiaryPersonalDetails
  ): BeneficiaryDetails =
    BeneficiaryDetails(
      beneficiaryChangeFlag = None,
      beneficiaryType = IndividualOrTrust(beneficiaryType),
      beneficiaryContactDetails = BeneficiaryContactDetails(
        beneficiaryTrstName = beneficiaryTrstName,
        beneficiaryPersonalDetails = personalDetails,
        beneficiaryAddress = AddressDetails(
          // TODO update once beneficiary address details are captured
          addressLine1 = "1 ABCDE Street",
          addressLine2 = "FGHIJ Town",
          postcode = Some("ZZ99 1AA"),
          country = "GB"
        )
      ),
      beneficiaryPaymentDetails = BeneficiaryPaymentDetails(
        // TODO update once beneficiary payment details are captured
        beneficiaryIHTPayable = 99.99,
        beneficiaryInterestPayable = 99.99,
        beneficiaryTotal = 99.99
      )
    )

  private def buildDeclarations(ihtpAuthContext: IhtpAuthContext[Any]): Declarations =
    ihtpAuthContext.credentialRole match {
      case Constants.pspId =>
        Declarations(
          submittedBy = Constants.HEADER_VALUE_PSP,
          submitterID = ihtpAuthContext.psaPspId,
          psaDeclaration = None,
          pspDeclaration = Some(
            PspDeclaration(
              pspDeclaration1 = "true",
              pspDeclaration2 = "true",
              psaid = "TODO"
            )
          )
        )
      case _ =>
        Declarations(
          submittedBy = Constants.HEADER_VALUE_PSA,
          submitterID = ihtpAuthContext.psaPspId,
          psaDeclaration = Some(
            PsaDeclaration(
              psaDeclaration1 = "true",
              psaDeclaration2 = "true"
            )
          ),
          pspDeclaration = None
        )
    }
}
