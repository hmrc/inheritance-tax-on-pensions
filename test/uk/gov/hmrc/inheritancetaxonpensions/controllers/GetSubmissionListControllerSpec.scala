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
import play.api.Application
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import org.mockito.ArgumentMatchers.any
import play.api.test.Helpers._
import org.mockito.Mockito._
import utils.BaseSpec
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}

import scala.concurrent.Future

class GetSubmissionListControllerSpec extends BaseSpec:

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("GET", "/")
  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector: SchemeDetailsConnector = mock[SchemeDetailsConnector]

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockSchemeDetailsConnector)
  }
  private val modules: Seq[GuiceableModule] =
    Seq(
      bind[AuthConnector].toInstance(mockAuthConnector),
      bind[SchemeDetailsConnector].toInstance(mockSchemeDetailsConnector)
    )

  private val application: Application = new GuiceApplicationBuilder()
    .configure(conf = "auditing.enabled" -> false, "metrics.enabled" -> false, "metrics.jvm" -> false)
    .overrides(modules*)
    .build()

  private val controller = application.injector.instanceOf[GetSubmissionListController]

  "GET submission list" must {
    "return 200" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(
          Future.successful(new ~(Some(externalId), enrolments))
        )
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(true))

      val result = controller.getSubmissionList(pstr)(
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

    "return 400 when non of required headers exist" in {
      intercept[BadRequestException] {
        await(controller.getSubmissionList(pstr)(fakeRequest))
      }
      verify(mockAuthConnector, never).authorise(any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }
    "return 400 when some of required headers don't exist" in {
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
  }
