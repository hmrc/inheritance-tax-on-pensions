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
import uk.gov.hmrc.inheritancetaxonpensions.services.{ReportRetrievalService, SessionService}
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments, InsufficientEnrolments}

import scala.concurrent.{ExecutionContext, Future}

class GetReportControllerSpec extends BaseSpec with TestValues:

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val mockIhtpReportConnector: IhtpReportConnector = mock[IhtpReportConnector]
  private val mockSessionSchemeDetailsRepository: SessionSchemeDetailsRepository = mock[SessionSchemeDetailsRepository]
  private val sessionService = new SessionService(mockSessionSchemeDetailsRepository)
  private val mockReportRetrievalService: ReportRetrievalService = mock[ReportRetrievalService]

  private val controller = new GetReportController(
    cc = stubControllerComponents(),
    authConnector = mockAuthConnector,
    schemeDetailsConnector = mockSchemeDetailsConnector,
    sessionService = sessionService,
    reportRetrievalService = mockReportRetrievalService
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

  private val userAnswers = Json.obj(
    "inheritanceTaxReference" -> "inheritanceTaxReference",
    "nameOfDeceased" -> "John Doe",
    "hasNino" -> true,
    "nino" -> "AB123456C",
    "ihtPaymentReference" -> "ihtPaymentReference",
    "ihtVersion" -> "ihtVersion"
  )

  val result: JsValue = Json.toJson(userAnswers)

  private val correlationId = "e4946bba-23f1-4a75-9207-b20b7741cf40"

  "getReport" must {
    "return OK and fetch a report by form bundle number" in {
      authoriseUser()
      when(
        mockReportRetrievalService.getReport(
          any[String],
          any[String],
          any[Option[String]],
          any[Option[String]],
          any[Option[String]]
        )(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(Right(("uuid", correlationId, userAnswers))))

      val result = controller.getReport()(
        requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=119000004320")
      )

      status(result) mustEqual Status.OK
      contentAsJson(result) mustEqual userAnswers
      header("correlationid", result) mustBe Some(correlationId)
      verify(mockReportRetrievalService).getReport(
        eqTo("24000001IN"),
        eqTo("S2400000001"),
        eqTo(Some("119000004320")),
        eqTo(None),
        eqTo(None)
      )(any[HeaderCarrier](), any[ExecutionContext]())
    }

    "fetch a report by payment reference number and version number" in {
      authoriseUser()
      when(
        mockReportRetrievalService.getReport(
          any[String],
          any[String],
          any[Option[String]],
          any[Option[String]],
          any[Option[String]]
        )(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(Right(("uuid", correlationId, userAnswers))))

      val result = controller.getReport()(
        requestWithRequiredHeaders(
          "/ihtp?pstr=24000001IN&ihtPaymentReference=PR000000001&versionNumber=001"
        )
      )

      status(result) mustEqual Status.OK
      verify(mockReportRetrievalService).getReport(
        eqTo("24000001IN"),
        eqTo("S2400000001"),
        eqTo(None),
        eqTo(Some("PR000000001")),
        eqTo(Some("001"))
      )(any[HeaderCarrier](), any[ExecutionContext]())
    }

    Seq(
      Status.BAD_REQUEST -> HttpResponse(
        status = Status.BAD_REQUEST,
        body = Json
          .obj(
            "origin" -> "HoD",
            "response" -> Json.obj(
              "error" -> Json.obj(
                "code" -> "VR_001",
                "logID" -> "UUID-123",
                "message" -> "Invalid IHT Reference Pattern"
              )
            )
          )
          .toString,
        headers = Map("correlationid" -> Seq(correlationId))
      ),
      Status.UNPROCESSABLE_ENTITY -> HttpResponse(
        status = Status.UNPROCESSABLE_ENTITY,
        body = Json
          .obj(
            "errors" -> Json.obj(
              "processingDate" -> "2026-06-07T16:12:49Z",
              "code" -> "003",
              "text" -> "Request could not be processed"
            )
          )
          .toString,
        headers = Map("correlationid" -> Seq(correlationId))
      ),
      Status.INTERNAL_SERVER_ERROR -> HttpResponse(
        status = Status.INTERNAL_SERVER_ERROR,
        body = Json
          .obj(
            "origin" -> "HoD",
            "response" -> Json.obj(
              "error" -> Json.obj(
                "code" -> "500",
                "logID" -> "UUID-500",
                "message" -> "Internal server error"
              )
            )
          )
          .toString,
        headers = Map("correlationid" -> Seq(correlationId))
      ),
      Status.SERVICE_UNAVAILABLE -> HttpResponse(
        status = Status.SERVICE_UNAVAILABLE,
        body = Json
          .obj(
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
          .toString,
        headers = Map("correlationid" -> Seq(correlationId))
      )
    ).foreach { case (statusCode, errorResponse) =>
      s"return upstream status $statusCode with its specified response body" in {
        authoriseUser()
        when(
          mockReportRetrievalService.getReport(
            any[String],
            any[String],
            any[Option[String]],
            any[Option[String]],
            any[Option[String]]
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        ).thenReturn(Future.successful(Left(errorResponse)))

        val result = controller.getReport()(
          requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=000000000000")
        )

        status(result) mustEqual statusCode
        contentAsString(result) mustEqual errorResponse.body
        header("correlationid", result) mustBe Some(correlationId)
      }
    }

    Seq(Status.UNAUTHORIZED, Status.FORBIDDEN, Status.NOT_FOUND, Status.UNSUPPORTED_MEDIA_TYPE).foreach { statusCode =>
      s"return upstream status $statusCode without a response body" in {
        authoriseUser()
        when(
          mockReportRetrievalService.getReport(
            any[String],
            any[String],
            any[Option[String]],
            any[Option[String]],
            any[Option[String]]
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        ).thenReturn(
          Future.successful(
            Left(
              HttpResponse(
                status = statusCode,
                body = "",
                headers = Map(
                  "correlationid" -> Seq(correlationId)
                )
              )
            )
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
