/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.inheritancetaxonpensions.connectors.SchemeDetailsConnector
import play.api.http.Status
import play.api.inject.bind
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import models.{IhtpOverviewResponse, IhtpOverviewSuccess}
import uk.gov.hmrc.inheritancetaxonpensions.repositories.SessionSchemeDetailsRepository
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models.ErrorCodes
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import play.api.test.Helpers._
import org.mockito.Mockito._
import utils.BaseSpec
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import uk.gov.hmrc.inheritancetaxonpensions.services.ReportRetrievalService
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments}
import play.api.Application
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class GetSubmissionListControllerSpec extends BaseSpec:

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("GET", "/")
  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val mockReportRetrievalService: ReportRetrievalService = mock[ReportRetrievalService]
  private val mockSessionSchemeDetailsRepository: SessionSchemeDetailsRepository = mock[SessionSchemeDetailsRepository]

  override def beforeEach(): Unit = {
    reset(
      mockAuthConnector,
      mockSchemeDetailsConnector,
      mockReportRetrievalService,
      mockSessionSchemeDetailsRepository
    )

    when(mockSessionSchemeDetailsRepository.get(any())).thenReturn(Future.successful(None))
  }
  private val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector),
      bind[ReportRetrievalService].toInstance(mockReportRetrievalService),
      bind[SessionSchemeDetailsRepository].toInstance(mockSessionSchemeDetailsRepository)
    )

  private val application: Application = new GuiceApplicationBuilder()
    .configure(
      conf = "auditing.enabled" -> false,
      "metrics.enabled" -> false,
      "metrics.jvm" -> false,
      "mongodb.encryption.key" -> "test-key-for-local-development-only"
    )
    .overrides(modules*)
    .build()

  private val controller = application.injector.instanceOf[GetSubmissionListController]
  private def requestWithRequiredHeaders(path: String = "/") = FakeRequest("GET", path).withHeaders(
    newHeaders = HEADER_KEY_SRN -> srn,
    HEADER_KEY_SCHEME_NAME -> schemeName,
    HEADER_KEY_USER_NAME -> userName,
    HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
  )

  private val requestWithRequiredHeadersAndDates =
    requestWithRequiredHeaders("/?dateFrom=2026-01-01&dateTo=2026-12-31")

  "GET submission list" must {
    "should return 200" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockReportRetrievalService.getOverview(any(), any(), any(), any(), any())(any(), any()))
        .thenReturn(
          Future.successful(
            Right(IhtpOverviewResponse(IhtpOverviewSuccess(Seq())))
          )
        )

      val result = controller.getSubmissionList(pstr)(
        requestWithRequiredHeadersAndDates
      )

      status(result) mustEqual Status.OK
      contentAsJson(result) mustEqual Json.obj(
        "success" -> Json.obj(
          "ihtpOverview" -> Json.arr()
        )
      )
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      verify(mockReportRetrievalService, times(1)).getOverview(
        eqTo(pstr),
        eqTo(srn),
        eqTo("2026-01-01"),
        eqTo("2026-12-31"),
        eqTo(None)
      )(
        any[HeaderCarrier](),
        any[ExecutionContext]()
      )
    }

    "should return 400 when dateFrom is missing" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      intercept[BadRequestException] {
        await(
          controller.getSubmissionList(pstr)(
            requestWithRequiredHeaders("/?dateTo=2026-12-31")
          )
        )
      }

      verify(mockReportRetrievalService, never).getOverview(any(), any(), any(), any(), any())(any(), any())
    }

    "should return 400 when dateTo is missing" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      intercept[BadRequestException] {
        await(
          controller.getSubmissionList(pstr)(
            requestWithRequiredHeaders("/?dateFrom=2026-01-01")
          )
        )
      }

      verify(mockReportRetrievalService, never).getOverview(any(), any(), any(), any(), any())(any(), any())
    }

    "should return 400 when non of required headers exist" in {
      intercept[BadRequestException] {
        await(controller.getSubmissionList(pstr)(fakeRequest))
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }
    "should return 400 when some of required headers don't exist" in {
      intercept[BadRequestException] {
        await(
          controller.getSubmissionList(pstr)(
            fakeRequest.withHeaders(newHeaders = HEADER_KEY_SRN -> srn, HEADER_KEY_SCHEME_NAME -> schemeName)
          )
        )
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }

    "should return 500 when service returns unexpected error" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockReportRetrievalService.getOverview(any(), any(), any(), any(), any())(any(), any()))
        .thenReturn(
          Future.successful(
            Left(ErrorCodes.unexpectedResponse)
          )
        )

      val result = controller.getSubmissionList(pstr)(
        requestWithRequiredHeadersAndDates
      )

      status(result) mustEqual Status.INTERNAL_SERVER_ERROR
      contentAsJson(result) mustEqual Json.obj(
        "message" -> "Unexpected Response"
      )
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      verify(mockReportRetrievalService, times(1)).getOverview(
        eqTo(pstr),
        eqTo(srn),
        eqTo("2026-01-01"),
        eqTo("2026-12-31"),
        eqTo(None)
      )(
        any[HeaderCarrier](),
        any[ExecutionContext]()
      )
    }

  }
