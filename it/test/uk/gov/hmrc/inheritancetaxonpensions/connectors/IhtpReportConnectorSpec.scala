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
    .configure("http-verbs.retries.intervals" -> Seq("10.millis", "20.millis", "30.millis", "40.millis", "50.millis"))
    .build()

  private lazy val connector: IhtpReportConnector = app.injector.instanceOf[IhtpReportConnector]

  val submitReturnUrl: String = "/etmp/RESTAdapter/pods/reports/ihtp"

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
        WireMock.verify( 6,
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
        WireMock.verify(6,
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
        WireMock.verify( 6,
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
        WireMock.verify(6,
          postRequestedFor(
            urlEqualTo(submitReturnUrl)
          )
        )

        result mustBe Left(ErrorCodes.unexpectedResponse)
      }
    }
  }
}
