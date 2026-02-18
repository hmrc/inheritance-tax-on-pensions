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

package uk.gov.hmrc.inheritancetaxonpensions.services

import org.scalatest.concurrent.Futures.patienceConfig
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.inheritancetaxonpensions.repositories.SessionSchemeDetailsRepository
import uk.gov.hmrc.inheritancetaxonpensions.models.SessionSchemeDetails
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers._
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._

import scala.concurrent.{Await, ExecutionContext, Future}

import java.time.Instant

implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

class SessionServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  override def beforeEach(): Unit =
    reset(mockSessionSchemeDetailsRepository)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val sessionSchemeDetails: SessionSchemeDetails =
    SessionSchemeDetails("id", "idType", "srn01", false, Instant.ofEpochSecond(1))

  val callbackFunctionSchemeDetails: Future[Boolean] = Future.successful(true)

  val mockSessionSchemeDetailsRepository: SessionSchemeDetailsRepository = mock[SessionSchemeDetailsRepository]

  val sessionService = SessionService(mockSessionSchemeDetailsRepository)

  "checkAssociation" - {

    "return scheme details from the session when session data is present" in {
      when(mockSessionSchemeDetailsRepository.get(any())).thenReturn(Future.successful(Some(sessionSchemeDetails)))

      val result = Await.result(
        sessionService.checkAssociation("id", "idType", "srn01", callbackFunctionSchemeDetails),
        patienceConfig.timeout
      )

      result mustBe false
    }

    "return scheme details from the api when session data is not present" in {
      when(mockSessionSchemeDetailsRepository.get(any())).thenReturn(Future.successful(None))

      val result = Await.result(
        sessionService.checkAssociation("id", "idType", "srn01", callbackFunctionSchemeDetails),
        patienceConfig.timeout
      )

      result mustBe true
      verify(mockSessionSchemeDetailsRepository, times(1)).set(any())
    }
  }
}
