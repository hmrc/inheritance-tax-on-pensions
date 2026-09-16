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

package uk.gov.hmrc.inheritancetaxonpensions.validators

import uk.gov.hmrc.inheritancetaxonpensions.validators.SchemaPaths.EPID1767_v0_1_adjusted
import play.api.inject.bind
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.inheritancetaxonpensions.models.ReportDetails
import org.scalatestplus.mockito.MockitoSugar.mock
import utils.BaseSpec
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.Application
import play.api.libs.json.{JsValue, Json}

class JSONSchemaValidatorSpec extends BaseSpec {

  private val mockAuthConnector = mock[AuthConnector]

  val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(mockAuthConnector)
    )
  val app: Application = new GuiceApplicationBuilder()
    .overrides(modules*)
    .build()

  private lazy val jsonPayloadSchemaValidator: JSONSchemaValidator = app.injector.instanceOf[JSONSchemaValidator]

  "json schema validator" must {
    "should successfully validate json payload against EPIC1767 version 0.1 - adjusted schema" in {
      val json: JsValue = Json.toJson(testReportSubmissionRequestBody)
      val result = jsonPayloadSchemaValidator.validatePayload(EPID1767_v0_1_adjusted, json)
      result.hasErrors mustBe false
    }

    "should successfully validate json payload with organisation against EPIC1767 version 0.1 - adjusted schema" in {
      val json: JsValue = Json.toJson(testReportSubmissionRequestBodyOrganisation)
      val result = jsonPayloadSchemaValidator.validatePayload(EPID1767_v0_1_adjusted, json)
      result.hasErrors mustBe false
    }

    "should identify invalid inputs" in {
      val json: JsValue = Json.toJson(
        testReportSubmissionRequestBody.copy(
          ihtNoticeRequest = testReportSubmissionRequestBody.ihtNoticeRequest.copy(
            reportDetails = ReportDetails(pstr = "Invalid", ihtPaymentReference = Some("a".repeat(18)))
          )
        )
      )
      val result = jsonPayloadSchemaValidator.validatePayload(EPID1767_v0_1_adjusted, json)
      result.hasErrors mustBe true

      val actualErrors = result.errors.map(_.toString)

      val expectedErrors = Set(
        "/ihtNoticeRequest/reportDetails/pstr: does not match the regex pattern ^[0-9]{8}[A-Z]{2}$",
        "/ihtNoticeRequest/reportDetails/ihtPaymentReference: must be at most 17 characters long"
      )

      actualErrors mustEqual expectedErrors
    }
  }
}
