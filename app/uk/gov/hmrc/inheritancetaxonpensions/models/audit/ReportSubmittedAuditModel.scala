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

package uk.gov.hmrc.inheritancetaxonpensions.models.audit

import play.api.libs.json._
import uk.gov.hmrc.inheritancetaxonpensions.models.IhtpPaymentNoticeSubmission
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo.Yes
import play.api.libs.functional.syntax._

import java.time.Instant

case class ReportSubmittedAuditModel(submissionPayLoad: IhtpPaymentNoticeSubmission, maybeVersion: Option[String])
    extends JsonAuditModel {
  override val auditType: String = "SuccessfulSubmission"

  private val etmpReportDetails = submissionPayLoad.ihtNoticeRequest.reportDetails
  private val etmpDeceased = submissionPayLoad.ihtNoticeRequest.deceased
  private val etmpDeceasedPersonalDetails = submissionPayLoad.ihtNoticeRequest.deceased.deceasedPersonalDetails
  private val etmpDeceasedDetails = submissionPayLoad.ihtNoticeRequest.deceased.deceasedDetails
  private val etmpPrDetails = submissionPayLoad.ihtNoticeRequest.personalRep
  private val etmpPrContactDetails = submissionPayLoad.ihtNoticeRequest.personalRep.prContactDetails
  private val etmpPrAddress = submissionPayLoad.ihtNoticeRequest.personalRep.prContactDetails.prAddress
  private val etmpIhTaxInformation = submissionPayLoad.ihtNoticeRequest.ihTaxInformation
  private val etmpBeneficiaries = submissionPayLoad.ihtNoticeRequest.beneficiary
  private val etmpDeclarations = submissionPayLoad.ihtNoticeRequest.declarations

  private def fromYesNo(value: String): Boolean = value.toLowerCase == "yes"
  private def fromYesNoOption(maybeValue: Option[YesNo]): Option[Boolean] =
    maybeValue.map(yesNo => fromYesNo(yesNo.value))

  // this is with names and values how it is expected to be in the audit event:
  private val payload = Payload(
    reportDetails = ReportDetails(
      pensionSchemeTaxReference = etmpReportDetails.pstr,
      ihtPaymentReference = etmpReportDetails.ihtPaymentReference
    ),
    deceased = Deceased(
      haveDeceasedDetailsChanged = etmpDeceased.deceasedChangeFlag.contains(YesNo.Yes),
      deceasedPersonalDetails = DeceasedPersonalDetails(
        etmpDeceasedPersonalDetails.title,
        etmpDeceasedPersonalDetails.firstForename,
        etmpDeceasedPersonalDetails.secondForename,
        etmpDeceasedPersonalDetails.surname,
        fromYesNo(etmpDeceasedPersonalDetails.ninoExist.value),
        etmpDeceasedPersonalDetails.nino,
        etmpDeceasedPersonalDetails.reasonNoNino
      ),
      deceasedDetails = DeceasedDetails(
        etmpDeceasedDetails.deceasedsDob,
        etmpDeceasedDetails.deceasedsDod,
        etmpDeceasedDetails.ihtRefNumber
      )
    ),
    personalRepresentativeDetails = PersonalRepresentativeDetails(
      etmpPrDetails.prChangeFlag.contains(YesNo.Yes),
      etmpPrDetails.typeOfPr.name,
      PersonalRepresentativeContactDetails(
        etmpPrContactDetails.orgName,
        etmpPrContactDetails.title,
        etmpPrContactDetails.firstForename,
        etmpPrContactDetails.secondForename,
        etmpPrContactDetails.surname,
        AddressDetails(
          etmpPrAddress.addressLine1,
          etmpPrAddress.addressLine2,
          etmpPrAddress.addressLine3,
          etmpPrAddress.addressLine4,
          etmpPrAddress.addressLine5,
          etmpPrAddress.postCode,
          etmpPrAddress.country,
          Some("UPRN") // TODO
        )
      )
    ),
    ihTaxInformation = IhTaxInformation(
      etmpIhTaxInformation.ihTaxChangeFlag.contains(YesNo.Yes),
      etmpIhTaxInformation.dateNoticeReceived,
      fromYesNo(etmpIhTaxInformation.noticeSubmittedByPr.value),
      fromYesNoOption(etmpIhTaxInformation.knownBeneficiaries),
      etmpIhTaxInformation.totalIhtPayable,
      etmpIhTaxInformation.totalInterestPayable,
      etmpIhTaxInformation.total
    ),
    beneficiaries = if (!etmpIhTaxInformation.knownBeneficiaries.contains(Yes)) {
      None
    } else {
      etmpBeneficiaries.map(etmpBens =>
        Beneficiaries(
          beneficiaryDetails = etmpBens.beneficiaryDetails.map { etmpBenDetails =>
            BeneficiaryDetails(
              etmpBenDetails.beneficiaryType.name,
              etmpBenDetails.beneficiaryTrustName,
              BeneficiaryPersonalDetails(
                etmpBenDetails.beneficiaryPersonalDetails.title,
                etmpBenDetails.beneficiaryPersonalDetails.firstForename,
                etmpBenDetails.beneficiaryPersonalDetails.secondForename,
                etmpBenDetails.beneficiaryPersonalDetails.surname,
                fromYesNoOption(etmpBenDetails.beneficiaryPersonalDetails.ninoExist).contains(true),
                etmpBenDetails.beneficiaryPersonalDetails.nino,
                etmpBenDetails.beneficiaryPersonalDetails.reasonNoNino
              ),
              AddressDetails(
                etmpBenDetails.beneficiaryAddress.addressLine1,
                etmpBenDetails.beneficiaryAddress.addressLine2,
                etmpBenDetails.beneficiaryAddress.addressLine3,
                etmpBenDetails.beneficiaryAddress.addressLine4,
                etmpBenDetails.beneficiaryAddress.addressLine5,
                etmpBenDetails.beneficiaryAddress.postCode,
                etmpBenDetails.beneficiaryAddress.country,
                Some("UPRN") // TODO
              ),
              BeneficiaryPaymentDetails(
                etmpBenDetails.beneficiaryPaymentDetails.beneficiaryIhtPayable,
                etmpBenDetails.beneficiaryPaymentDetails.beneficiaryInterestPayable,
                etmpBenDetails.beneficiaryPaymentDetails.beneficiaryTotal
              )
            )
          },
          haveBeneficiaryDetailsChanged = etmpBens.beneficiariesChangeFlag.contains(YesNo.Yes)
        )
      )
    },
    declarations = Declarations(
      etmpDeclarations.submittedBy,
      etmpDeclarations.submitterId,
      etmpDeclarations.psaDeclaration.map(psaDeclaration =>
        PsaDeclaration(
          psaDeclaration.psaDeclaration1,
          psaDeclaration.psaDeclaration2
        )
      ),
      etmpDeclarations.pspDeclaration.map(pspDeclaration =>
        PspDeclaration(
          pspDeclaration.pspDeclaration1,
          pspDeclaration.pspDeclaration2,
          pspDeclaration.psaid
        )
      )
    )
  )

  val beneficiaries: JsObject =
    payload.beneficiaries match {
      case Some(value) =>
        Json.obj(
          "beneficiaries" -> payload.beneficiaries
        )
      case None => Json.obj()
    }

  val declaration: JsObject = Json.obj(
    "declaration" -> payload.declarations
  )
  override val detail: JsValue = Json.obj(
    "submissionVersion" -> maybeVersion,
    "reportDetails" -> payload.reportDetails,
    "deceased" -> payload.deceased,
    "personalRepresentativeDetails" -> payload.personalRepresentativeDetails,
    "ihTaxInformation" -> payload.ihTaxInformation
  ) ++ beneficiaries ++ declaration
}

object ReportSubmittedAuditModel {
  def build(submissionPayLoad: IhtpPaymentNoticeSubmission, maybeVersion: Option[String]): ReportSubmittedAuditModel =
    ReportSubmittedAuditModel(submissionPayLoad, maybeVersion)
}

case class Payload(
  reportDetails: ReportDetails,
  deceased: Deceased,
  personalRepresentativeDetails: PersonalRepresentativeDetails,
  ihTaxInformation: IhTaxInformation,
  beneficiaries: Option[Beneficiaries] = None,
  declarations: Declarations
)

object Payload {
  implicit val payloadFormat: OFormat[Payload] =
    Json.format[Payload]
}

case class ReportDetails(
  pensionSchemeTaxReference: String,
  ihtPaymentReference: Option[String]
)

object ReportDetails {
  implicit val ihtpReportDetailsFormat: OFormat[ReportDetails] =
    Json.format[ReportDetails]
}

case class Deceased(
  haveDeceasedDetailsChanged: Boolean,
  deceasedPersonalDetails: DeceasedPersonalDetails,
  deceasedDetails: DeceasedDetails
)

object Deceased {
  implicit val deceasedFormat: OFormat[Deceased] =
    Json.format[Deceased]
}

case class DeceasedPersonalDetails(
  title: Option[String],
  forename: String,
  middleName: Option[String],
  surname: String,
  ninoExist: Boolean,
  nino: Option[String],
  reasonForNoNino: Option[String]
)

object DeceasedPersonalDetails {
  implicit val deceasedPersonalDetailsFormat: OFormat[DeceasedPersonalDetails] =
    Json.format[DeceasedPersonalDetails]
}

case class DeceasedDetails(
  deceasedsDateOfBirth: String,
  deceasedsDateOfDeath: String,
  ihtReferenceNumber: String
)

object DeceasedDetails {
  implicit val deceasedDetailsFormat: OFormat[DeceasedDetails] =
    Json.format[DeceasedDetails]
}

case class PersonalRepresentativeDetails(
  havePersonalRepresentativeDetailsChanged: Boolean,
  typeOfPersonalRepresentative: String,
  personalRepresentativeContactDetails: PersonalRepresentativeContactDetails
)

object PersonalRepresentativeDetails {
  implicit val personalRepresentativeDetailsFormat: OFormat[PersonalRepresentativeDetails] =
    Json.format[PersonalRepresentativeDetails]
}

case class PersonalRepresentativeContactDetails(
  organisationName: Option[String] = None,
  title: Option[String] = None,
  forename: String,
  middleName: Option[String] = None,
  surname: String,
  address: AddressDetails
)

object PersonalRepresentativeContactDetails {
  implicit val personalRepresentativeDetails: OFormat[PersonalRepresentativeContactDetails] =
    Json.format[PersonalRepresentativeContactDetails]
}

case class AddressDetails(
  addressLine1: String,
  addressLine2: Option[String] = None,
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  addressLine5: Option[String] = None,
  postCode: Option[String] = None,
  countryCode: String,
  uprn: Option[String] = None
)

object AddressDetails {
  implicit val addressDetailsFormat: OFormat[AddressDetails] =
    Json.format[AddressDetails]
}

case class IndividualName(
  title: Option[String],
  firstForename: String,
  secondForename: Option[String],
  surname: String
)

object IndividualName {
  implicit val individualNameFormat: OFormat[IndividualName] =
    Json.format[IndividualName]
}

case class IndividualDetails(name: IndividualName, address: AddressDetails)

object IndividualDetails {
  implicit val individualDetailsReads: Reads[IndividualDetails] =
    JsPath.read[IndividualName].and(JsPath.read[AddressDetails])(IndividualDetails.apply)

  implicit val individualDetailsWrites: OWrites[IndividualDetails] = individualDetails =>
    Json.toJsObject(individualDetails.name) ++ Json.toJsObject(individualDetails.address)
}

case class OrganisationInfo(
  organisationName: String,
  title: Option[String],
  firstForename: String,
  secondForename: Option[String],
  surname: String
)

object OrganisationInfo {
  implicit val organisationDetailsFormat: OFormat[OrganisationInfo] =
    Json.format[OrganisationInfo]
}

case class OrganisationDetails(info: OrganisationInfo, address: AddressDetails)

object OrganisationDetails {
  implicit val organisationDetailsReads: Reads[OrganisationDetails] =
    JsPath.read[OrganisationInfo].and(JsPath.read[AddressDetails])(OrganisationDetails.apply)

  implicit val organisationDetailsWrites: OWrites[OrganisationDetails] = organisationDetails =>
    Json.toJsObject(organisationDetails.info) ++ Json.toJsObject(organisationDetails.address)
}

case class Beneficiaries(
  beneficiaryDetails: Seq[BeneficiaryDetails],
  haveBeneficiaryDetailsChanged: Boolean
)

object Beneficiaries {
  implicit val beneficiariesFormat: OFormat[Beneficiaries] = Json.format[Beneficiaries]
}

case class BeneficiaryDetails(
  beneficiaryType: String,
  beneficiaryTrustName: Option[String] = None,
  beneficiaryPersonalDetails: BeneficiaryPersonalDetails,
  beneficiaryAddress: AddressDetails,
  beneficiaryPaymentDetails: BeneficiaryPaymentDetails
)

object BeneficiaryDetails {
  implicit val BeneficiaryDetailsFormat: OFormat[BeneficiaryDetails] =
    Json.format[BeneficiaryDetails]
}

case class BeneficiaryPersonalDetails(
  title: Option[String],
  forename: String,
  middleName: Option[String],
  surname: String,
  ninoExist: Boolean,
  nino: Option[String],
  reasonNoNino: Option[String]
)

object BeneficiaryPersonalDetails {
  implicit val BeneficiaryPersonalDetailsFormat: OFormat[BeneficiaryPersonalDetails] =
    Json.format[BeneficiaryPersonalDetails]
}

case class BeneficiaryPaymentDetails(
  ihtPayable: Double,
  interestPayable: Double,
  totalPayable: Double
)

object BeneficiaryPaymentDetails {
  implicit val BeneficiaryPaymentDetailsFormat: OFormat[BeneficiaryPaymentDetails] =
    Json.format[BeneficiaryPaymentDetails]
}

case class NinoOrReasonAnswers(nino: Option[String], reasonNoNino: Option[String])

object NinoOrReasonAnswers {
  implicit val ninoOrReasonAnswersFormat: OFormat[NinoOrReasonAnswers] =
    Json.format[NinoOrReasonAnswers]
}

case class BirthDeathDates(dateOfBirth: String, dateOfDeath: String)

object BirthDeathDates {
  implicit val birthDeathDatesFormat: OFormat[BirthDeathDates] =
    Json.format[BirthDeathDates]
}

case class IhtpPaymentNoticeResponse(success: SuccessResponse)

object IhtpPaymentNoticeResponse {
  implicit val successResponseFormat: OFormat[IhtpPaymentNoticeResponse] =
    Json.format[IhtpPaymentNoticeResponse]
}

case class SuccessResponse(ihtResponse: IhtResponse)

object SuccessResponse {
  implicit val successResponseFormat: OFormat[SuccessResponse] =
    Json.format[SuccessResponse]
}

case class IhtResponse(formBundleNo: String, ihtPaymentReference: String, processingDate: Instant)

object IhtResponse {
  implicit val ihtResponseFormat: OFormat[IhtResponse] =
    Json.format[IhtResponse]
}

case class IhTaxInformation(
  haveTaxInformationChanged: Boolean,
  datePaymentNoticeReceived: String,
  paymentNoticeSubmittedByPersonalRepresentative: Boolean,
  areBeneficiariesKnown: Option[Boolean],
  totalIhtPayable: Option[Double],
  totalInterestPayable: Option[Double],
  totalAmountPayable: Option[Double]
)

object IhTaxInformation {
  implicit val ihTaxInformationFormat: OFormat[IhTaxInformation] =
    Json.format[IhTaxInformation]
}

case class Declarations(
  submittedBy: String,
  submitterId: String,
  psaDeclaration: Option[PsaDeclaration],
  pspDeclaration: Option[PspDeclaration]
)

object Declarations {
  implicit val declarationsFormat: OFormat[Declarations] =
    Json.format[Declarations]
}

case class PsaDeclaration(
  psaDeclaration1: Boolean,
  psaDeclaration2: Boolean
)

object PsaDeclaration {
  implicit val psaDeclarationFormat: OFormat[PsaDeclaration] =
    Json.format[PsaDeclaration]
}

case class PspDeclaration(
  pspDeclaration1: Boolean,
  pspDeclaration2: Boolean,
  authorisingSchemeAdministratorID: String
)

object PspDeclaration {
  implicit val pspDeclarationFormat: OFormat[PspDeclaration] =
    Json.format[PspDeclaration]
}
