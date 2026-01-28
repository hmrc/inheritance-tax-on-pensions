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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.client.{ResponseDefinitionBuilder, WireMock}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.inheritancetaxonpensions.BaseConnectorSpec
import uk.gov.hmrc.inheritancetaxonpensions.models.Srn
import uk.gov.hmrc.inheritancetaxonpensions.utils.UnrecognisedHttpResponseException
import utils.TestValues

import scala.concurrent.ExecutionContext.Implicits.global

class SchemeDetailsConnectorSpec extends BaseConnectorSpec with TestValues {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit =
    super.beforeEach()

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(50, Millis)))

  val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.pensionsScheme.port" -> wireMockPort)
    .build()

  private lazy val connector: SchemeDetailsConnector = app.injector.instanceOf[SchemeDetailsConnector]

  val url = "/pensions-scheme/is-psa-associated"

  def stubGet(idValue: String, idType: String, srn: String, response: ResponseDefinitionBuilder): StubMapping =
    wireMockServer
      .stubFor(
        get(urlEqualTo(url))
          .withHeader(idType, equalTo(idValue))
          .withHeader("schemeReferenceNumber", equalTo(srn))
          .withHeader("Content-Type", equalTo("application/json"))
          .willReturn(response)
      )

  "checkAssociation" should {

    Seq(true, false).foreach { response =>
      s"return $response if psa is associated" in {
        stubGet(psaPspId, psaId, srn, ok(s"$response"))
        whenReady(connector.checkAssociation(psaPspId, psaId, Srn(srn).get)) { (result: Boolean) =>
          WireMock.verify(
            getRequestedFor(
              urlEqualTo("/pensions-scheme/is-psa-associated")
            )
          )
          result shouldMatchTo response
        }
      }
    }

    "throw error when 404 response is received" in {
      stubGet(psaPspId, psaId, srn, notFound)
      val thrown = intercept[NotFoundException] {
        await(connector.checkAssociation(psaPspId, psaId, Srn(srn).get))
      }
      WireMock.verify(
        getRequestedFor(urlEqualTo("/pensions-scheme/is-psa-associated"))
      )
      thrown.responseCode shouldMatchTo 404
    }

    "throw error when bad request response is received" in {
      stubGet(psaPspId, psaId, srn, badRequest)
      val thrown = intercept[BadRequestException] {
        await(connector.checkAssociation(psaPspId, psaId, Srn(srn).get))
      }
      WireMock.verify(
        getRequestedFor(urlEqualTo("/pensions-scheme/is-psa-associated"))
      )
      thrown.responseCode shouldMatchTo 400
    }

    "throw error when bad request with body is received" in {
      stubGet(psaPspId, psaId, srn, badRequest.withBody("INVALID_PAYLOAD"))
      val thrown = intercept[BadRequestException] {
        await(connector.checkAssociation(psaPspId, psaId, Srn(srn).get))
      }
      WireMock.verify(
        getRequestedFor(urlEqualTo("/pensions-scheme/is-psa-associated"))
      )
      thrown.responseCode shouldMatchTo 400
      thrown.message must include(s"Response body 'INVALID_PAYLOAD'")
    }

    "throw error when other 400 response is received" in {
      stubGet(psaPspId, psaId, srn, forbidden)
      val thrown = intercept[UpstreamErrorResponse] {
        await(connector.checkAssociation(psaPspId, psaId, Srn(srn).get))
      }
      WireMock.verify(
        getRequestedFor(urlEqualTo("/pensions-scheme/is-psa-associated"))
      )
      thrown.statusCode shouldMatchTo 403
    }

    "throw error when 500 response is received" in {
      stubGet(psaPspId, psaId, srn, serverError)
      val thrown = intercept[UpstreamErrorResponse] {
        await(connector.checkAssociation(psaPspId, psaId, Srn(srn).get))
      }
      WireMock.verify(
        getRequestedFor(urlEqualTo("/pensions-scheme/is-psa-associated"))
      )
      thrown.statusCode shouldMatchTo 500
    }

    "throw UnrecognisedHttpResponseException when return any other response is received" in {
      stubGet(psaPspId, psaId, srn, noContent)
      intercept[UnrecognisedHttpResponseException] {
        await(connector.checkAssociation(psaPspId, psaId, Srn(srn).get))
      }
      WireMock.verify(
        getRequestedFor(urlEqualTo("/pensions-scheme/is-psa-associated"))
      )
    }

  }
}
