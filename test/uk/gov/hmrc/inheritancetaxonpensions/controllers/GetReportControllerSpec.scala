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

package uk.gov.hmrc.inheritancetaxonpensions.controllers

import play.api.test.FakeRequest
import uk.gov.hmrc.inheritancetaxonpensions.connectors.{IhtpReportConnector, SchemeDetailsConnector}
import play.api.http.Status
import uk.gov.hmrc.auth.core.retrieve.~
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.inheritancetaxonpensions.repositories.SessionSchemeDetailsRepository
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import utils.{BaseSpec, TestValues}
import play.api.test.Helpers._
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.services.SessionService
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments, InsufficientEnrolments}

import scala.concurrent.{ExecutionContext, Future}

class GetReportControllerSpec extends BaseSpec with TestValues:

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val mockIhtpReportConnector: IhtpReportConnector = mock[IhtpReportConnector]
  private val mockSessionSchemeDetailsRepository: SessionSchemeDetailsRepository = mock[SessionSchemeDetailsRepository]
  private val sessionService = new SessionService(mockSessionSchemeDetailsRepository)

  private val controller = new GetReportController(
    cc = stubControllerComponents(),
    authConnector = mockAuthConnector,
    schemeDetailsConnector = mockSchemeDetailsConnector,
    sessionService = sessionService,
    ihtpReportConnector = mockIhtpReportConnector
  )

  override def beforeEach(): Unit = {
    reset(
      mockAuthConnector,
      mockSchemeDetailsConnector,
      mockIhtpReportConnector,
      mockSessionSchemeDetailsRepository
    )

    when(mockSessionSchemeDetailsRepository.get(any())).thenReturn(Future.successful(None))
  }

  private def authoriseUser(): Unit = {
    when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
      .thenReturn(Future.successful(new ~(Some(externalId), enrolments)))
    when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(true))
  }

  private def requestWithRequiredHeaders(path: String) =
    FakeRequest("GET", path).withHeaders(
      HEADER_KEY_SCHEME_NAME -> schemeName,
      HEADER_KEY_USER_NAME -> userName,
      HEADER_KEY_SRN -> srn,
      HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
    )

  private val reportResponse = Json.obj(
    "success" -> Json.obj(
      "pstr" -> "24000001IN",
      "ihtpDetails" -> Json.obj(
        "version" -> "001",
        "status" -> "In Progress"
      )
    )
  )

  private val correlationId = "e4946bba-23f1-4a75-9207-b20b7741cf40"

  private def httpResponse(status: Int, body: Option[JsValue]): HttpResponse =
    HttpResponse(
      status,
      body.fold("")(_.toString),
      Map(
        "Content-Type" -> Seq("application/json"),
        "correlationid" -> Seq(correlationId)
      )
    )

  "getReport" must {
    "return OK and fetch a report by form bundle number" in {
      authoriseUser()
      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(httpResponse(Status.OK, Some(reportResponse))))

      val result = controller.getReport()(
        requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=119000004320")
      )

      status(result) mustEqual Status.OK
      contentAsJson(result) mustEqual reportResponse
      header("correlationid", result) mustBe Some(correlationId)
      verify(mockIhtpReportConnector).getReport(
        eqTo("24000001IN"),
        eqTo(Some("119000004320")),
        eqTo(None),
        eqTo(None)
      )(any[HeaderCarrier]())
    }

    "fetch a report by payment reference number and version number" in {
      authoriseUser()
      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(httpResponse(Status.OK, Some(reportResponse))))

      val result = controller.getReport()(
        requestWithRequiredHeaders(
          "/ihtp?pstr=24000001IN&paymentReferenceNumber=PR000000001&versionNumber=001"
        )
      )

      status(result) mustEqual Status.OK
      verify(mockIhtpReportConnector).getReport(
        eqTo("24000001IN"),
        eqTo(None),
        eqTo(Some("PR000000001")),
        eqTo(Some("001"))
      )(any[HeaderCarrier]())
    }

    Seq(
      Status.BAD_REQUEST -> Some(
        Json.obj(
          "origin" -> "HoD",
          "response" -> Json.obj(
            "error" -> Json.obj(
              "code" -> "VR_001",
              "logID" -> "UUID-123",
              "message" -> "Invalid IHT Reference Pattern"
            )
          )
        )
      ),
      Status.UNPROCESSABLE_ENTITY -> Some(
        Json.obj(
          "errors" -> Json.obj(
            "processingDate" -> "2026-06-07T16:12:49Z",
            "code" -> "003",
            "text" -> "Request could not be processed"
          )
        )
      ),
      Status.INTERNAL_SERVER_ERROR -> Some(
        Json.obj(
          "origin" -> "HoD",
          "response" -> Json.obj(
            "error" -> Json.obj(
              "code" -> "500",
              "logID" -> "UUID-500",
              "message" -> "Internal server error"
            )
          )
        )
      ),
      Status.SERVICE_UNAVAILABLE -> Some(
        Json.obj(
          "origin" -> "HIP",
          "response" -> Json.obj(
            "failures" -> Json.arr(
              Json.obj(
                "type" -> "Service unavailable",
                "reason" -> "The downstream service is unavailable"
              )
            )
          )
        )
      )
    ).foreach { case (statusCode, responseBody) =>
      s"return upstream status $statusCode with its specified response body" in {
        authoriseUser()
        when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(httpResponse(statusCode, responseBody)))

        val result = controller.getReport()(
          requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=000000000000")
        )

        status(result) mustEqual statusCode
        contentAsString(result) mustEqual responseBody.fold("")(_.toString)
        header("correlationid", result) mustBe Some(correlationId)
      }
    }

    Seq(Status.UNAUTHORIZED, Status.FORBIDDEN, Status.NOT_FOUND, Status.UNSUPPORTED_MEDIA_TYPE).foreach { statusCode =>
      s"return upstream status $statusCode without a response body" in {
        authoriseUser()
        when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
          .thenReturn(
            Future.successful(
              httpResponse(statusCode, Some(Json.obj("message" -> "This upstream body must not be returned")))
            )
          )

        val result = controller.getReport()(
          requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=000000000000")
        )

        status(result) mustEqual statusCode
        contentAsString(result) mustBe empty
        header("correlationid", result) mustBe Some(correlationId)
      }
    }

    "return BAD_REQUEST when pstr is missing" in {
      authoriseUser()

      intercept[BadRequestException] {
        await(controller.getReport()(requestWithRequiredHeaders("/ihtp?fbNumber=119000004320")))
      }

      verify(mockIhtpReportConnector, never).getReport(any(), any(), any(), any())(any())
    }

    "not fetch a report when the user is not authorised" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments()))

      intercept[InsufficientEnrolments] {
        await(
          controller.getReport()(
            requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=119000004320")
          )
        )
      }

      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
      verify(mockIhtpReportConnector, never).getReport(any(), any(), any(), any())(any())
    }

    "return BAD_REQUEST when required headers are missing" in {
      intercept[BadRequestException] {
        await(controller.getReport()(FakeRequest("GET", "/ihtp?pstr=24000001IN&fbNumber=119000004320")))
      }

      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockIhtpReportConnector, never).getReport(any(), any(), any(), any())(any())
    }
  }
