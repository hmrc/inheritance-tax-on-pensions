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

package uk.gov.hmrc.inheritancetaxonpensions.connectors

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.Application
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.*
import uk.gov.hmrc.inheritancetaxonpensions.BaseConnectorSpec
import uk.gov.hmrc.inheritancetaxonpensions.models.{ErrorCodes, IhtpReportSubmissionResponse}
import utils.TestValues

class IhtpReportConnectorSpec extends BaseConnectorSpec with TestValues {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit =
    super.beforeEach()

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(50, Millis)))

  val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.ihtp-report.port" -> wireMockPort)
    .configure("microservice.services.ihtp-report.url.submitReport" -> "/etmp/RESTAdapter/pods/reports/ihtp")
    .configure("microservice.services.ihtp-report.url.getReport" -> "/etmp/RESTAdapter/pods/reports/ihtp")
    .configure("microservice.services.ihtp-report.url.getOverview" -> "/etmp/RESTAdapter/pods/reports/ihtp-overview")
    .configure("http-verbs.retries.intervals" -> Seq("10.millis", "20.millis", "30.millis", "40.millis", "50.millis"))
    .configure("mongodb.encryption.key" -> "test-key-for-integration-tests-only")
    .build()

  private lazy val connector: IhtpReportConnector = app.injector.instanceOf[IhtpReportConnector]

  val submitReturnUrl: String = "/etmp/RESTAdapter/pods/reports/ihtp"
  val reportUrl: String = "/etmp/RESTAdapter/pods/reports/ihtp"
  val overviewUrl: String = "/etmp/RESTAdapter/pods/reports/ihtp-overview"
  val correlationId: String = "e4946bba-23f1-4a75-9207-b20b7741cf40"
  val unexpectedResponse = Json.obj(
    "origin" -> "HIP",
    "response" -> Json.arr(
      Json.obj(
        "type" -> "Unexpected response",
        "reason" -> "An unexpected response was received from the downstream service"
      )
    )
  )

  "getReport" should {

    "return a report retrieved by form bundle number with the required HIP headers" in {
      val response = Json.obj(
        "success" -> Json.obj(
          "pstr" -> "24000001IN",
          "ihtpDetails" -> Json.obj("version" -> "001", "status" -> "In Progress")
        )
      )
      val url = s"$reportUrl?pstr=24000001IN&fbNumber=119000004320"

      wireMockServer.stubFor(
        get(urlEqualTo(url)).willReturn(
          ok(response.toString)
            .withHeader("Content-Type", "application/json")
            .withHeader("correlationid", correlationId)
        )
      )

      whenReady(connector.getReport("24000001IN", Some("119000004320"), None, None)) { result =>
        WireMock.verify(
          getRequestedFor(urlEqualTo(url))
            .withHeader("X-Message-Type", equalTo("Request"))
            .withHeader("X-Regime-Type", equalTo("IHTP"))
            .withHeader("X-Originating-System", equalTo("MDTP"))
            .withHeader("X-Transmitting-System", equalTo("MDTP"))
        )

        result.status mustBe OK
        result.json mustBe response
        result.header("correlationid") mustBe Some(correlationId)
      }
    }

    "return a report retrieved by an encoded payment reference number and version number" in {
      val response = Json.obj("success" -> Json.obj("pstr" -> "24000001IN"))
      val url = s"$reportUrl?pstr=24000001IN&paymentReferenceNumber=PR+000/001&versionNumber=001"

      wireMockServer.stubFor(get(urlEqualTo(url)).willReturn(ok(response.toString)))

      whenReady(connector.getReport("24000001IN", None, Some("PR 000/001"), Some("001"))) { result =>
        val generatedCorrelationId = result
          .header("correlationid")
          .getOrElse(fail("Expected the generated correlation ID on the response"))

        WireMock.verify(
          getRequestedFor(urlEqualTo(url)).withHeader("correlationid", equalTo(generatedCorrelationId))
        )
        result.status mustBe OK
        result.json mustBe response
      }
    }

    Seq("" -> "an empty", "not-json" -> "a malformed").foreach { case (body, description) =>
      s"return a standardised server error for $description successful response" in {
        val url = s"$reportUrl?pstr=24000001IN&fbNumber=119000004320"
        wireMockServer.stubFor(get(urlEqualTo(url)).willReturn(ok(body)))

        whenReady(connector.getReport("24000001IN", Some("119000004320"), None, None)) { result =>
          result.status mustBe INTERNAL_SERVER_ERROR
          result.json mustBe unexpectedResponse
          result.header("correlationid") must not be empty
        }
      }
    }

    "return the bad request response unchanged" in {
      val response = Json.obj(
        "origin" -> "HoD",
        "response" -> Json.obj(
          "error" -> Json.obj(
            "code" -> "VR_001",
            "logID" -> "UUID-123",
            "message" -> "Invalid IHT Reference Pattern"
          )
        )
      )
      val url = s"$reportUrl?pstr=24000001IN&fbNumber=119000004320"
      wireMockServer.stubFor(
        get(urlEqualTo(url)).willReturn(
          badRequest().withHeader("Content-Type", "application/json").withBody(response.toString)
        )
      )

      whenReady(connector.getReport("24000001IN", Some("119000004320"), None, None)) { result =>
        result.status mustBe BAD_REQUEST
        result.json mustBe response
      }
    }

    "return not found without manufacturing a response body" in {
      val url = s"$reportUrl?pstr=24000001IN&fbNumber=119000004320"
      wireMockServer.stubFor(get(urlEqualTo(url)).willReturn(notFound()))

      whenReady(connector.getReport("24000001IN", Some("119000004320"), None, None)) { result =>
        result.status mustBe NOT_FOUND
        result.body mustBe empty
        result.header("correlationid") must not be empty
      }
    }

    "return the unprocessable entity response unchanged" in {
      val response = Json.obj(
        "errors" -> Json.obj(
          "processingDate" -> "2026-06-07T16:12:49Z",
          "code" -> "003",
          "text" -> "Request could not be processed"
        )
      )
      val url = s"$reportUrl?pstr=24000001IN&fbNumber=000000000000"
      wireMockServer.stubFor(
        get(urlEqualTo(url)).willReturn(
          aResponse()
            .withStatus(UNPROCESSABLE_ENTITY)
            .withHeader("Content-Type", "application/json")
            .withBody(response.toString)
        )
      )

      whenReady(connector.getReport("24000001IN", Some("000000000000"), None, None)) { result =>
        result.status mustBe UNPROCESSABLE_ENTITY
        result.json mustBe response
      }
    }

    "normalise an error status not listed in the draft API specification" in {
      val url = s"$reportUrl?pstr=24000001IN&fbNumber=119000004320"
      wireMockServer.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(418)))

      whenReady(connector.getReport("24000001IN", Some("119000004320"), None, None)) { result =>
        result.status mustBe INTERNAL_SERVER_ERROR
        result.json mustBe unexpectedResponse
        result.header("correlationid") must not be empty
      }
    }

    Seq(INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { statusCode =>
      s"retry 5 times and then preserve a $statusCode response" in {
        val response = Json.obj(
          "origin" -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(
              Json.obj("type" -> "Upstream failure", "reason" -> "The downstream service is unavailable")
            )
          )
        )
        val url = s"$reportUrl?pstr=24000001IN&fbNumber=119000004320"
        wireMockServer.stubFor(
          get(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(statusCode)
              .withHeader("Content-Type", "application/json")
              .withBody(response.toString)
          )
        )

        whenReady(connector.getReport("24000001IN", Some("119000004320"), None, None)) { result =>
          WireMock.verify(6, getRequestedFor(urlEqualTo(url)))
          result.status mustBe statusCode
          result.json mustBe response
        }
      }
    }

    Seq(BAD_GATEWAY, GATEWAY_TIMEOUT).foreach { statusCode =>
      s"retry 5 times and then normalise a $statusCode response" in {
        val url = s"$reportUrl?pstr=24000001IN&fbNumber=119000004320"
        wireMockServer.stubFor(get(urlEqualTo(url)).willReturn(aResponse().withStatus(statusCode)))

        whenReady(connector.getReport("24000001IN", Some("119000004320"), None, None)) { result =>
          WireMock.verify(6, getRequestedFor(urlEqualTo(url)))
          result.status mustBe INTERNAL_SERVER_ERROR
          result.json mustBe unexpectedResponse
          result.header("correlationid") must not be empty
        }
      }
    }
  }

  "getOverview" should {

    "return a valid overview response for a successful call" in {
      val response = Json.obj(
        "success" -> Json.obj(
          "pstr" -> "24000001IN",
          "ihtpOverview" -> Json.arr()
        )
      )

      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31"))
          .willReturn(ok(response.toString))
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", None)) { result =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31")
          )
        )

        result mustBe Right(response)
      }
    }

    "include status when supplied" in {
      val response = Json.obj(
        "success" -> Json.obj(
          "pstr" -> "24000001IN",
          "ihtpOverview" -> Json.arr()
        )
      )

      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31&status=Submitted"))
          .willReturn(ok(response.toString))
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", Some("Submitted"))) { result =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31&status=Submitted")
          )
        )

        result mustBe Right(response)
      }
    }

    "url encode status when supplied" in {
      val response = Json.obj(
        "success" -> Json.obj(
          "pstr" -> "24000001IN",
          "ihtpOverview" -> Json.arr()
        )
      )

      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31&status=In+Progress"))
          .willReturn(ok(response.toString))
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", Some("In Progress"))) { result =>
        WireMock.verify(
          getRequestedFor(
            urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31&status=In+Progress")
          )
        )

        result mustBe Right(response)
      }
    }

    "return a bad request when the response from the server is a bad request" in {
      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31"))
          .willReturn(badRequest())
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", None)) { result =>
        result mustBe Left(ErrorCodes.badRequest)
      }
    }

    "return not found when the response from the server is not found" in {
      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31"))
          .willReturn(notFound())
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", None)) { result =>
        result mustBe Left(ErrorCodes.entityNotFound)
      }
    }

    "return unprocessable entity when the response from the server is unprocessable entity" in {
      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31"))
          .willReturn(badRequestEntity())
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", None)) { result =>
        result mustBe Left(ErrorCodes.unprocessableEntity)
      }
    }

    "return unexpected response when the response from the server is unrecognised" in {
      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31"))
          .willReturn(aResponse().withStatus(418))
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", None)) { result =>
        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }

    "retry 5 times when the response from the server is a 500" in {
      wireMockServer.stubFor(
        get(urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31"))
          .willReturn(serverError())
      )

      whenReady(connector.getOverview("24000001IN", "2026-01-01", "2026-12-31", None)) { result =>
        WireMock.verify(
          6,
          getRequestedFor(
            urlEqualTo(s"$overviewUrl?pstr=24000001IN&dateFrom=2026-01-01&dateTo=2026-12-31")
          )
        )

        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }
  }

  "submitReport" should {

    "return a valid report submission response for a successful call (OK)" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        ok(s"${Json.toJson(testReportSubmissionResponse)}")
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Right(testReportSubmissionResponse)
      }
    }

    "return an unexpected response where the response from the server cannot be parsed" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        ok(s"${Json.toJson("foo")}")
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }

    "return a bad request when the response from the server is a bad request (BAD_REQUEST)" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        badRequest()
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          1,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.badRequest)
      }
    }

    "return a not found when the response from the server is a not found (NOT_FOUND)" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        notFound()
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          1,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.entityNotFound)
      }
    }

    "return an unprocessable entity when the response from the server is a unprocessable entity (UNPROCESSABLE_ENTITY)" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        badRequestEntity()
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          1,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.unprocessableEntity)
      }
    }

    "retry 5 times when the response from the server is a 500" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        serverError()
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          6,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }

    "retry 5 times when the response from the server is a 502 (BAD_GATEWAY)" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        aResponse().withStatus(502)
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          6,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }

    "retry 5 times when the response from the server is a 503 (SERVICE_UNAVAILABLE)" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        serviceUnavailable()
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          6,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }

    "retry 5 times when the response from the server is a 504 (GATEWAY_TIMEOUT)" in {
      stubPost(
        submitReturnUrl,
        Json.toJson(testReportSubmissionRequestBody).toString,
        aResponse().withStatus(504)
      )

      whenReady(connector.submitReport(testReportSubmissionRequestBody)) { result =>
        WireMock.verify(
          6,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }
  }
}
