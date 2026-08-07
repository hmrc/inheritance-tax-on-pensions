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
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo

import java.time.Instant

// TODO - build out as the journey matures
// TODO - Review this model against the IHTP EPIDs when we get them!
case class IhtpReportSubmission(
  reportDetails: ReportDetails,
  deceasedDetails: DeceasedDetails,
  prDetails: PrDetails,
  ihtTaxInformation: IhtTaxInformation,
  beneficiaries: Option[Seq[BeneficiaryDetails]]
)

object IhtpReportSubmission {
  implicit val ihtpReportSubmissionFormat: OFormat[IhtpReportSubmission] =
    Json.format[IhtpReportSubmission]
}

case class ReportDetails(
  pstr: String
)

object ReportDetails {
  implicit val ihtpReportDetailsFormat: OFormat[ReportDetails] =
    Json.format[ReportDetails]
}

case class DeceasedDetails(
  inheritanceTaxReference: String,
  title: Option[String],
  firstForename: String,
  secondForename: Option[String],
  surname: String,
  dateOfBirth: String,
  dateOfDeath: String,
  ninoExist: YesNo,
  nino: Option[String],
  reasonNoNINO: Option[String]
)

object DeceasedDetails {
  implicit val deceasedDetailsFormat: OFormat[DeceasedDetails] =
    Json.format[DeceasedDetails]
}

case class PrDetails(individual: Option[IndividualDetails], organisation: Option[OrganisationDetails])

object PrDetails {
  implicit val prDetailsFormat: OFormat[PrDetails] =
    Json.format[PrDetails]
}

case class AddressDetails(
  addressLine1: String,
  addressLine2: String,
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  ukPostcode: Option[String] = None,
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

case class BeneficiaryDetails(individual: Option[IndividualName])

object BeneficiaryDetails {
  implicit val BeneficiaryDetailsFormat: OFormat[BeneficiaryDetails] =
    Json.format[BeneficiaryDetails]
}

case class BirthDeathDates(dateOfBirth: String, dateOfDeath: String)

object BirthDeathDates {
  implicit val birthDeathDatesFormat: OFormat[BirthDeathDates] =
    Json.format[BirthDeathDates]
}

case class IhtpReportSubmissionResponse(processingDate: Instant, formBundleNumber: String, paymentReference: String)

object IhtpReportSubmissionResponse {
  implicit val ihtpReportSubmissionResponseFormat: OFormat[IhtpReportSubmissionResponse] =
    Json.format[IhtpReportSubmissionResponse]
}

case class IhtTaxInformation(
  dateThePensionSchemeReceivedNoticeToPay: String,
  didThePersonalRepresentativeSubmitTheNotice: YesNo
)

object IhtTaxInformation {
  implicit val ihtTaxInformationFormat: OFormat[IhtTaxInformation] =
    Json.format[IhtTaxInformation]
}
