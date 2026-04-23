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

import play.api.libs.json.{Json, OFormat}

import java.time.Instant

// TODO - build out as the journey matures
// TODO - Review this model against the IHTP EPIDs when we get them!
case class IhtpReportSubmission(reportDetails: ReportDetails, deceasedDetails: DeceasedDetails)

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
  nino: Option[String],
  reasonForNoNino: Option[String]
)

object DeceasedDetails {
  implicit val deceasedDetailsFormat: OFormat[DeceasedDetails] =
    Json.format[DeceasedDetails]
}

case class NinoOrReasonAnswers(nino: Option[String], reasonForNoNino: Option[String])

object NinoOrReasonAnswers {
  implicit val ninoOrReasonAnswersFormat: OFormat[NinoOrReasonAnswers] =
    Json.format[NinoOrReasonAnswers]
}

case class IhtpReportSubmissionResponse(processingDateTime: Instant, formBundleNumber: String, paymentReference: String)

object IhtpReportSubmissionResponse {
  implicit val ihtpReportSubmissionResponseFormat: OFormat[IhtpReportSubmissionResponse] =
    Json.format[IhtpReportSubmissionResponse]
}
