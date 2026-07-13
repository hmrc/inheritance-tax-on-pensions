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

import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.inheritancetaxonpensions.connectors.SchemeDetailsConnector
import play.api.http.Status
import uk.gov.hmrc.auth.core.retrieve.~
import play.api.libs.json.Json
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.inheritancetaxonpensions.repositories.SessionSchemeDetailsRepository
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models._
import org.mockito.ArgumentMatchers.any
import utils.{BaseSpec, TestValues}
import play.api.test.Helpers._
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.services.{ReportSubmissionService, SessionService}
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments, InsufficientEnrolments}

import scala.concurrent.{ExecutionContext, Future}

class ReportSubmissionControllerSpec extends BaseSpec with TestValues:

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val fakeRequest = FakeRequest("POST", "/")
  private val mockReportSubmissionService: ReportSubmissionService = mock[ReportSubmissionService]
  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val mockSessionSchemeDetailsRepository: SessionSchemeDetailsRepository = mock[SessionSchemeDetailsRepository]
  private val sessionService = new SessionService(mockSessionSchemeDetailsRepository)

  private val controller = new ReportSubmissionController(
    reportSubmissionService = mockReportSubmissionService,
    cc = stubControllerComponents(),
    authConnector = mockAuthConnector,
    schemeDetailsConnector = mockSchemeDetailsConnector,
    sessionService = sessionService
  )

  override def beforeEach(): Unit = {
    reset(
      mockReportSubmissionService,
      mockAuthConnector,
      mockSchemeDetailsConnector,
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

  private def requestWithRequiredHeaders =
    fakeRequest.withHeaders(
      HEADER_KEY_SCHEME_NAME -> schemeName,
      HEADER_KEY_USER_NAME -> userName,
      HEADER_KEY_SRN -> srn,
      HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
    )

  "submitReport" must {
    "return OK (200) when report submission is successful" in {
      authoriseUser()
      when(mockReportSubmissionService.submitReport(any(), any())(any()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = controller.submitReport(testPstr, testUserAnswersId)(
        requestWithRequiredHeaders
      )

      status(result) mustEqual Status.OK
      contentAsJson(result) mustBe Json.toJson(testSubmissionResponse)
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      verify(mockReportSubmissionService, times(1)).submitReport(any(), any())(any())
    }

    "return error response when service returns Left" in {
      authoriseUser()
      when(mockReportSubmissionService.submitReport(any(), any())(any()))
        .thenReturn(Future.successful(Left(ErrorCodes.badRequest)))

      val result = controller.submitReport(testPstr, testUserAnswersId)(
        requestWithRequiredHeaders
      )

      status(result) mustEqual Status.BAD_REQUEST
      verify(mockReportSubmissionService, times(1)).submitReport(any(), any())(any())
    }

    "not submit the report when the user is not authorised" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments()))

      intercept[InsufficientEnrolments] {
        await(controller.submitReport(testPstr, testUserAnswersId)(requestWithRequiredHeaders))
      }

      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
      verify(mockReportSubmissionService, never).submitReport(any(), any())(any())
    }

    "return BAD_REQUEST (400) when none of the headers exist" in {
      intercept[BadRequestException] {
        await(controller.submitReport(testPstr, testUserAnswersId)(fakeRequest))
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockReportSubmissionService, never).submitReport(any(), any())(any())
    }
  }
