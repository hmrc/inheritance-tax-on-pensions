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

import com.networknt.schema.{SchemaRegistry, SpecificationVersion}
import uk.gov.hmrc.inheritancetaxonpensions.validators.SchemaPaths.EPID1767_v0_2_adjusted
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.inheritancetaxonpensions.models.ReportDetails
import utils.BaseSpec
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.inject.bind
import com.fasterxml.jackson.databind.ObjectMapper
import play.api.Application
import play.api.libs.json.{JsValue, Json}
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo

import scala.util.Using

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

  private lazy val adjustedSchema: JsValue =
    Using.resource(getClass.getResourceAsStream(EPID1767_v0_2_adjusted))(stream => Json.parse(stream))

  private def validatesDefinition(definition: String, data: JsValue): Boolean = {
    val objectMapper = new ObjectMapper()
    val schema = Json.obj(
      "$ref" -> s"#/definitions/$definition",
      "definitions" -> (adjustedSchema \ "definitions").get
    )
    SchemaRegistry
      .withDefaultDialect(SpecificationVersion.DRAFT_4)
      .getSchema(objectMapper.readTree(schema.toString))
      .validate(objectMapper.readTree(data.toString))
      .isEmpty
  }

  "json schema validator" must {
    "should successfully validate json payload against EPID1767 working version 0.2 schema" in {
      for {
        declarations <- Seq(declarationsPayloadSection, declarationsPspPayloadSection)
        noticeSubmittedByPr <- Seq(YesNo.Yes, YesNo.No)
        knownBeneficiaries <- Seq(YesNo.Yes, YesNo.No)
      } {
        val json: JsValue = Json.toJson(
          testReportSubmissionRequestBody.copy(
            ihtNoticeRequest = testReportSubmissionRequestBody.ihtNoticeRequest.copy(
              declarations = declarations,
              ihTaxInformation = testReportSubmissionRequestBody.ihtNoticeRequest.ihTaxInformation.copy(
                noticeSubmittedByPr = noticeSubmittedByPr,
                knownBeneficiaries = Some(knownBeneficiaries)
              )
            )
          )
        )
        val result = jsonPayloadSchemaValidator.validatePayload(EPID1767_v0_2_adjusted, json)
        result.hasErrors mustBe false
      }
    }

    "should successfully validate json payload with organisation against EPID1767 working version 0.2 schema" in {
      val json: JsValue = Json.toJson(testReportSubmissionRequestBodyOrganisation)
      val result = jsonPayloadSchemaValidator.validatePayload(EPID1767_v0_2_adjusted, json)
      result.hasErrors mustBe false
    }

    "allow optional Yes/No NINO answers but reject other values" in {
      val person = Json.obj("firstForename" -> "Test", "surname" -> "Person")
      validatesDefinition("personType", person) mustBe true
      Seq("Yes" -> true, "No" -> true, "Str" -> false).foreach { case (answer, valid) =>
        withClue(s"ninoExist=$answer: ") {
          validatesDefinition("personType", person ++ Json.obj("ninoExist" -> answer)) mustBe valid
        }
      }
    }

    "validate retrieval submission dates as timestamps of at most 20 characters" in {
      Seq(
        "2026-07-14T16:42:20Z" -> true,
        "2026-07-14" -> false,
        "2026-07-14T99:42:20Z" -> false,
        "2026-07-14T16:42:20.123Z" -> false
      ).foreach { case (date, valid) =>
        withClue(s"submissionDate=$date: ") {
          validatesDefinition(
            "reportDetailsGet",
            Json.obj("submissionDate" -> date, "ihtVersion" -> "001")
          ) mustBe valid
        }
      }
    }

    "should identify invalid inputs" in {
      val json: JsValue = Json.toJson(
        testReportSubmissionRequestBody.copy(
          ihtNoticeRequest = testReportSubmissionRequestBody.ihtNoticeRequest.copy(
            reportDetails = ReportDetails(pstr = "Invalid", ihtPaymentReference = Some("a".repeat(18)))
          )
        )
      )
      val result = jsonPayloadSchemaValidator.validatePayload(EPID1767_v0_2_adjusted, json)
      result.hasErrors mustBe true

      val actualErrors = result.errors.map(_.toString)

      val expectedErrors = Set(
        "/ihtNoticeRequest/reportDetails/pstr: does not match the regex pattern ^[0-9]{8}[A-Z]{2}$",
        "/ihtNoticeRequest/reportDetails/ihtPaymentReference: must be at most 17 characters long",
        "/ihtNoticeRequest/reportDetails/ihtPaymentReference: does not match the regex pattern ^[AF][0-9]{6}/[0-9]{2}[A-Z][0-9]{6}$"
      )

      actualErrors mustEqual expectedErrors
    }

  }
}
