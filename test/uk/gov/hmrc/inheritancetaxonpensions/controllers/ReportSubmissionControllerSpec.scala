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
import play.api.libs.json.Json
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models._
import org.mockito.ArgumentMatchers.any
import utils.{BaseSpec, TestValues}
import play.api.test.Helpers._
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.services.{ReportSubmissionService, SessionService}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, Future}

class ReportSubmissionControllerSpec extends BaseSpec with TestValues:

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val fakeRequest = FakeRequest("POST", "/")
  private val mockReportSubmissionService: ReportSubmissionService = mock[ReportSubmissionService]

  private val controller = new ReportSubmissionController(
    reportSubmissionService = mockReportSubmissionService,
    cc = stubControllerComponents(),
    authConnector = mock[AuthConnector],
    schemeDetailsConnector = mock[SchemeDetailsConnector],
    sessionService = mock[SessionService]
  )

  override def beforeEach(): Unit =
    reset(mockReportSubmissionService)

  "submitReport" must {
    "return OK (200) when report submission is successful" in {
      when(mockReportSubmissionService.submitReport(any(), any())(any()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = controller.submitReport(testPstr, testUserAnswersId)(
        fakeRequest.withHeaders(
          HEADER_KEY_SCHEME_NAME -> schemeName,
          HEADER_KEY_USER_NAME -> userName,
          HEADER_KEY_SRN -> "24000086IN",
          HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
        )
      )

      status(result) mustEqual Status.OK
      contentAsJson(result) mustBe Json.toJson(testSubmissionResponse)
      verify(mockReportSubmissionService, times(1)).submitReport(any(), any())(any())
    }

    "return error response when service returns Left" in {
      when(mockReportSubmissionService.submitReport(any(), any())(any()))
        .thenReturn(Future.successful(Left(ErrorCodes.badRequest)))

      val result = controller.submitReport(testPstr, testUserAnswersId)(
        fakeRequest.withHeaders(
          HEADER_KEY_SCHEME_NAME -> schemeName,
          HEADER_KEY_USER_NAME -> userName,
          HEADER_KEY_SRN -> "24000086IN",
          HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
        )
      )

      status(result) mustEqual Status.BAD_REQUEST
      verify(mockReportSubmissionService, times(1)).submitReport(any(), any())(any())
    }

    "return BAD_REQUEST (400) when none of the headers exist" in {
      intercept[BadRequestException] {
        await(controller.submitReport(testPstr, testUserAnswersId)(fakeRequest))
      }
      verify(mockReportSubmissionService, never).submitReport(any(), any())(any())
    }
  }
