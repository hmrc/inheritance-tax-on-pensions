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
import play.api.inject.bind
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.inheritancetaxonpensions.repositories.{SessionSchemeDetailsRepository, UserAnswersRepository}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models._
import org.mockito.ArgumentMatchers.any
import play.api.test.Helpers._
import org.mockito.Mockito._
import utils.BaseSpec
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import uk.gov.hmrc.inheritancetaxonpensions.services.ReportSubmissionService
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments}
import play.api.Application
import play.api.libs.json.Json

import scala.concurrent.Future

import java.time.Instant

class ReportSubmissionControllerSpec extends BaseSpec:

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("POST", "/")
  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val mockUserAnswersRepository: UserAnswersRepository = mock[UserAnswersRepository]
  private val mockSessionSchemeDetailsRepository: SessionSchemeDetailsRepository = mock[SessionSchemeDetailsRepository]
  private val mockReportSubmissionService: ReportSubmissionService = mock[ReportSubmissionService]

  private val testSubmissionResponse = IhtpReportSubmissionResponse(
    processingDateTime = Instant.now(),
    formBundleNumber = "910000000000",
    paymentReference = "123456789"
  )

  override def beforeEach(): Unit = {
    reset(
      mockAuthConnector,
      mockSchemeDetailsConnector,
      mockUserAnswersRepository,
      mockSessionSchemeDetailsRepository,
      mockReportSubmissionService
    )

    when(mockSessionSchemeDetailsRepository.get(any())).thenReturn(Future.successful(None))
  }

  private val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector),
      bind[UserAnswersRepository].toInstance(mockUserAnswersRepository),
      bind[SessionSchemeDetailsRepository].toInstance(mockSessionSchemeDetailsRepository),
      bind[ReportSubmissionService].toInstance(mockReportSubmissionService)
    )

  private val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false)
    .overrides(modules*)
    .build()

  private val controller = application.injector.instanceOf[ReportSubmissionController]

  "submitReport" must {

    "return OK (200) when report submission is successful" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockReportSubmissionService.submitReport(any(), any())(any()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = controller.submitReport()(
        fakeRequest
          .withJsonBody(Json.obj("userAnswersId" -> "testUserAnswersId"))
          .withHeaders(
            newHeaders = HEADER_KEY_SRN -> srn,
            HEADER_KEY_SCHEME_NAME -> schemeName,
            HEADER_KEY_USER_NAME -> userName,
            HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
          )
      )

      status(result) mustEqual Status.OK
      contentAsJson(result) mustBe Json.toJson(testSubmissionResponse)
      verify(mockReportSubmissionService, times(1)).submitReport(any(), any())(any())
    }

    "return OK (200) when request body is missing (uses a default userAnswersId)" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockReportSubmissionService.submitReport(any(), any())(any()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = controller.submitReport()(
        fakeRequest.withHeaders(
          newHeaders = HEADER_KEY_SRN -> srn,
          HEADER_KEY_SCHEME_NAME -> schemeName,
          HEADER_KEY_USER_NAME -> userName,
          HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
        )
      )

      status(result) mustEqual Status.OK
      verify(mockReportSubmissionService, times(1)).submitReport(any(), any())(any())
    }

    "return BAD_REQUEST (400) when none of the required headers exist" in {
      intercept[BadRequestException] {
        await(controller.submitReport()(fakeRequest))
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
      verify(mockReportSubmissionService, never).submitReport(any(), any())(any())
    }
  }
