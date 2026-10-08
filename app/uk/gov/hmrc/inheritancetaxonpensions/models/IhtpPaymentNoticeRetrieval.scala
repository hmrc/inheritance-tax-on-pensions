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

case class IhtpPaymentNoticeRetrievalResponse(ihtNoticeResponse: IhtNoticeResponse)

object IhtpPaymentNoticeRetrievalResponse {
  implicit val successResponseFormat: OFormat[IhtpPaymentNoticeRetrievalResponse] =
    Json.format[IhtpPaymentNoticeRetrievalResponse]
}

case class IhtNoticeResponse(
  schemeDetails: SchemeDetails,
  reportDetails: ReportDetailsResponse,
  deceased: Deceased,
  personalRep: PrDetails,
  ihTaxInformation: IhTaxInformation,
  beneficiary: Option[Beneficiaries],
  declarations: Declarations
)

object IhtNoticeResponse {
  implicit val ihtNoticeResponseFormat: OFormat[IhtNoticeResponse] =
    Json.format[IhtNoticeResponse]
}

case class SchemeDetails(
  pstr: String,
  schemeName: String,
  schemeStartDate: String,
  schemeEndDate: String
)

object SchemeDetails {
  implicit val schemeDetailsFormat: OFormat[SchemeDetails] =
    Json.format[SchemeDetails]
}

case class ReportDetailsResponse(
  submissionDate: String,
  ihtPaymentReference: String,
  ihtVersion: String,
  ihtPaid: Boolean
)

object ReportDetailsResponse {
  implicit val reportDetailsFormat: OFormat[ReportDetailsResponse] =
    Json.format[ReportDetailsResponse]
}
