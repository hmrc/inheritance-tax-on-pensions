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
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers
import org.mockito.ArgumentMatchers.any
import play.api.test.Helpers._
import org.mockito.Mockito._
import utils.BaseSpec
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.Application
import play.api.libs.json.Json

import scala.concurrent.Future

class UserAnswersControllerSpec extends BaseSpec:

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("GET", "/")
  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]
  private val mockUserAnswersRepository: UserAnswersRepository = mock[UserAnswersRepository]
  val emptyUserAnswers: UserAnswers = UserAnswers("id")

  override def beforeEach(): Unit =
    reset(mockAuthConnector, mockSchemeDetailsConnector, mockUserAnswersRepository)
  private val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector),
      bind[UserAnswersRepository].toInstance(mockUserAnswersRepository)
    )

  private val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false)
    .overrides(modules*)
    .build()

  private val controller = application.injector.instanceOf[UserAnswersController]

  "Fetch user answers" must {

    "return OK (200) when the user answers are found" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockUserAnswersRepository.get("id")).thenReturn(Future.successful(Some(emptyUserAnswers)))

      val result = controller.fetch("id")(
        fakeRequest.withHeaders(
          newHeaders = HEADER_KEY_SRN -> srn,
          HEADER_KEY_SCHEME_NAME -> schemeName,
          HEADER_KEY_USER_NAME -> userName,
          HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
        )
      )

      status(result) mustEqual Status.OK
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
    }

    "return NOT FOUND (422) when the user answers where not found" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockUserAnswersRepository.get(any())).thenReturn(Future.successful(None))

      val result = controller.fetch("id")(
        fakeRequest.withHeaders(
          newHeaders = HEADER_KEY_SRN -> srn,
          HEADER_KEY_SCHEME_NAME -> schemeName,
          HEADER_KEY_USER_NAME -> userName,
          HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
        )
      )

      status(result) mustEqual Status.NOT_FOUND
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
    }

    "return BAD_REQUEST (400) when non of required headers exist" in {
      intercept[BadRequestException] {
        await(controller.fetch("id")(fakeRequest))
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }

    "return BAD_REQUEST (400) when some of required headers don't exist" in {
      intercept[BadRequestException] {
        await(
          controller.fetch("id")(
            fakeRequest.withHeaders(newHeaders = HEADER_KEY_SRN -> srn, HEADER_KEY_SCHEME_NAME -> schemeName)
          )
        )
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }
  }

  "Set user answers" must {

    "return OK (200) when the operation is successful" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockUserAnswersRepository.set(any())).thenReturn(Future.successful(true))

      val result = controller.set()(
        fakeRequest
          .withJsonBody(Json.toJson(emptyUserAnswers))
          .withHeaders(
            newHeaders = HEADER_KEY_SRN -> srn,
            HEADER_KEY_SCHEME_NAME -> schemeName,
            HEADER_KEY_USER_NAME -> userName,
            HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
          )
      )

      status(result) mustEqual Status.OK
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
    }

    "return INTERNAL_SERVER_ERROR (500) when the operation is not successful" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))
      when(mockUserAnswersRepository.set(any())).thenReturn(Future.successful(false))

      val result = controller.set()(
        fakeRequest
          .withJsonBody(Json.toJson(emptyUserAnswers))
          .withHeaders(
            newHeaders = HEADER_KEY_SRN -> srn,
            HEADER_KEY_SCHEME_NAME -> schemeName,
            HEADER_KEY_USER_NAME -> userName,
            HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
          )
      )

      status(result) mustEqual Status.INTERNAL_SERVER_ERROR
      verify(mockAuthConnector, times(1)).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
    }

    "return BAD_REQUEST (400) when the request body is missing" in {
      intercept[BadRequestException] {
        await(
          controller.set()(
            fakeRequest
              .withHeaders(
                newHeaders = HEADER_KEY_SRN -> srn,
                HEADER_KEY_SCHEME_NAME -> schemeName,
                HEADER_KEY_USER_NAME -> userName,
                HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA
              )
          )
        )
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
      verify(mockUserAnswersRepository, never).set(any())
    }

    "return BAD_REQUEST (400) when non of required headers exist" in {
      intercept[BadRequestException] {
        await(controller.set()(fakeRequest))
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }

    "return BAD_REQUEST (400) when some of required headers don't exist" in {
      intercept[BadRequestException] {
        await(
          controller.set()(
            fakeRequest.withHeaders(newHeaders = HEADER_KEY_SRN -> srn, HEADER_KEY_SCHEME_NAME -> schemeName)
          )
        )
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }
  }
