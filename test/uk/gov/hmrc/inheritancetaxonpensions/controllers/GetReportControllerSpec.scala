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
import play.api.libs.json.Json
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.inheritancetaxonpensions.repositories.SessionSchemeDetailsRepository
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models.ErrorCodes
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

  "getReport" must {
    "return OK and fetch a report by form bundle number" in {
      authoriseUser()
      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Right(reportResponse)))

      val result = controller.getReport()(
        requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=119000004320")
      )

      status(result) mustEqual Status.OK
      contentAsJson(result) mustEqual reportResponse
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
        .thenReturn(Future.successful(Right(reportResponse)))

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

    "return the status from an upstream error" in {
      authoriseUser()
      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(Left(ErrorCodes.unprocessableEntity)))

      val result = controller.getReport()(
        requestWithRequiredHeaders("/ihtp?pstr=24000001IN&fbNumber=000000000000")
      )

      status(result) mustEqual Status.UNPROCESSABLE_ENTITY
      contentAsJson(result) mustEqual Json.obj("message" -> ErrorCodes.unprocessableEntity.message)
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
