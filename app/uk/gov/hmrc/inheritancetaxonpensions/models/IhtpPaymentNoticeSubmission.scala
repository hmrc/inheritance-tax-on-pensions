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

package uk.gov.hmrc.inheritancetaxonpensions.models

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.{IndividualOrOrg, IndividualOrTrust, YesNo}

// TODO - Review this model against the IHTP EPIDs when we get them!
case class IhtpPaymentNoticeSubmission(
  reportDetails: ReportDetails,
  deceased: Deceased,
  personalRep: PrDetails,
  ihTaxInformation: IhTaxInformation,
  beneficiaries: Option[Seq[BeneficiaryDetails]],
  declarations: Declarations
)

object IhtpPaymentNoticeSubmission {
  implicit val ihtpPaymentNoticeSubmissionFormat: OFormat[IhtpPaymentNoticeSubmission] =
    Json.format[IhtpPaymentNoticeSubmission]
}

case class ReportDetails(
  pstr: String,
  ihtPaymentReference: String
)

object ReportDetails {
  implicit val ihtpReportDetailsFormat: OFormat[ReportDetails] =
    Json.format[ReportDetails]
}

case class Deceased(
  deceasedChangeFlag: Option[YesNo] = None,
  deceasedPersonalDetails: DeceasedPersonalDetails,
  deceasedDetails: DeceasedDetails
)

object Deceased {
  implicit val deceasedFormat: OFormat[Deceased] =
    Json.format[Deceased]
}

case class DeceasedPersonalDetails(
  title: Option[String],
  firstForename: String,
  secondForename: Option[String],
  surname: String,
  ninoExist: YesNo,
  nino: Option[String],
  reasonNoNINO: Option[String]
)

object DeceasedPersonalDetails {
  implicit val deceasedPersonalDetailsFormat: OFormat[DeceasedPersonalDetails] =
    Json.format[DeceasedPersonalDetails]
}

case class DeceasedDetails(
  deceasedsDOB: String,
  deceasedsDOD: String,
  ihtRefNumber: String
)

object DeceasedDetails {
  implicit val deceasedDetailsFormat: OFormat[DeceasedDetails] =
    Json.format[DeceasedDetails]
}

case class PrDetails(
  prChangeFlag: Option[YesNo],
  typeOfPR: IndividualOrOrg,
  prContactDetails: PrContactDetails,
  prAddress: AddressDetails
)

object PrDetails {
  implicit val prDetailsFormat: OFormat[PrDetails] =
    Json.format[PrDetails]
}

case class PrContactDetails(
  orgName: Option[String] = None,
  title: Option[String] = None,
  firstForename: String,
  secondForename: Option[String] = None,
  surname: String
)

object PrContactDetails {
  implicit val prContactDetails: OFormat[PrContactDetails] =
    Json.format[PrContactDetails]
}

case class AddressDetails(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postcode: Option[String] = None,
  country: String
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

case class BeneficiaryDetails(
  beneficiaryChangeFlag: Option[YesNo] = None,
  beneficiaryType: IndividualOrTrust,
  beneficiaryContactDetails: BeneficiaryContactDetails,
  beneficiaryPaymentDetails: BeneficiaryPaymentDetails
)

object BeneficiaryDetails {
  implicit val BeneficiaryDetailsFormat: OFormat[BeneficiaryDetails] =
    Json.format[BeneficiaryDetails]
}

case class BeneficiaryContactDetails(
  beneficiaryTrstName: Option[String] = None,
  beneficiaryPersonalDetails: BeneficiaryPersonalDetails,
  beneficiaryAddress: AddressDetails
)

object BeneficiaryContactDetails {
  implicit val BeneficiaryContactDetailsFormat: OFormat[BeneficiaryContactDetails] =
    Json.format[BeneficiaryContactDetails]
}

case class BeneficiaryPersonalDetails(
  title: Option[String],
  firstForename: String,
  secondForename: Option[String],
  surname: String,
  ninoExist: YesNo,
  nino: Option[String],
  reasonNoNINO: Option[String]
)

object BeneficiaryPersonalDetails {
  implicit val BeneficiaryPersonalDetailsFormat: OFormat[BeneficiaryPersonalDetails] =
    Json.format[BeneficiaryPersonalDetails]
}

case class BeneficiaryPaymentDetails(
  beneficiaryIHTPayable: String,
  beneficiaryInterestPayable: String,
  beneficiaryTotal: String
)

object BeneficiaryPaymentDetails {
  implicit val BeneficiaryPaymentDetailsFormat: OFormat[BeneficiaryPaymentDetails] =
    Json.format[BeneficiaryPaymentDetails]
}

case class NinoOrReasonAnswers(nino: Option[String], reasonNoNINO: Option[String])

object NinoOrReasonAnswers {
  implicit val ninoOrReasonAnswersFormat: OFormat[NinoOrReasonAnswers] =
    Json.format[NinoOrReasonAnswers]
}

case class BirthDeathDates(dateOfBirth: String, dateOfDeath: String)

object BirthDeathDates {
  implicit val birthDeathDatesFormat: OFormat[BirthDeathDates] =
    Json.format[BirthDeathDates]
}

case class IhtpPaymentNoticeResponse(formBundleNo: String, ihtPaymentReference: String)

object IhtpPaymentNoticeResponse {
  implicit val ihtpPaymentNoticeResponseFormat: OFormat[IhtpPaymentNoticeResponse] =
    Json.format[IhtpPaymentNoticeResponse]
}

case class IhTaxInformation(
  ihTaxChangeFlag: Option[YesNo] = None,
  dateNoticeReceived: String,
  noticeSubmittedByPR: YesNo,
  knownBeneficiaries: Option[YesNo],
  totalIHTPayable: Option[String],
  totalInterestPayable: Option[String],
  total: Option[String]
)

object IhTaxInformation {
  implicit val ihTaxInformationFormat: OFormat[IhTaxInformation] =
    Json.format[IhTaxInformation]
}

case class Declarations(
  submittedBy: String,
  submitterID: String,
  psaDeclaration: Option[PsaDeclaration],
  pspDeclaration: Option[PspDeclaration]
)

object Declarations {
  implicit val declarationsFormat: OFormat[Declarations] =
    Json.format[Declarations]
}

case class PsaDeclaration(
  psaDeclaration1: String,
  psaDeclaration2: String
)

object PsaDeclaration {
  implicit val psaDeclarationFormat: OFormat[PsaDeclaration] =
    Json.format[PsaDeclaration]
}

case class PspDeclaration(
  pspDeclaration1: String,
  pspDeclaration2: String,
  psaid: String
)

object PspDeclaration {
  implicit val pspDeclarationFormat: OFormat[PspDeclaration] =
    Json.format[PspDeclaration]
}
