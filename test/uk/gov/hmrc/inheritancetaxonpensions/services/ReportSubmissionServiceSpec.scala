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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import uk.gov.hmrc.inheritancetaxonpensions.models.{ErrorCodes, IhtpReportSubmissionResponse, UserAnswers}
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers._
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json

import scala.concurrent.Future

import java.time.Instant

class ReportSubmissionServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  override def beforeEach(): Unit = reset(mockUserAnswersRepository, mockIhtpReportConnector)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val testUserAnswersId = "testUserAnswersId"
  private val testPstr = "12345678"
  private val testSubmissionResponse = IhtpReportSubmissionResponse(Instant.now(), "910000000000", "123456781")

  private val mockUserAnswersRepository: UserAnswersRepository = mock[UserAnswersRepository]
  private val mockIhtpReportConnector: IhtpReportConnector = mock[IhtpReportConnector]
  private val service = new ReportSubmissionService(mockUserAnswersRepository, mockIhtpReportConnector)

  "submitReport" - {
    "return Right when submission is successful" in {
      val testUserAnswers = UserAnswers(testUserAnswersId, Json.obj("inheritanceTaxReferenceNumber" -> "A123459/25A"))
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any())(any()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isRight mustBe true
      verify(mockIhtpReportConnector).submitReport(any())(any())
    }

    "return Left when the user answers are not found" in {
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(None))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result mustBe Left(ErrorCodes.entityNotFound)
      verify(mockIhtpReportConnector, never).submitReport(any())(any())
    }

    "return Left when connector returns an error" in {
      val testUserAnswers = UserAnswers(testUserAnswersId, Json.obj("inheritanceTaxReferenceNumber" -> "A123459/25A"))
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any())(any()))
        .thenReturn(Future.successful(Left(ErrorCodes.badRequest)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isLeft mustBe true
      verify(mockIhtpReportConnector).submitReport(any())(any())
    }

    "throw an exception when mandatory tax reference is missing" in {
      val testUserAnswers = UserAnswers(testUserAnswersId, Json.obj())
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      an[RuntimeException] must be thrownBy service.submitReport(testUserAnswersId, testPstr).futureValue

      verify(mockIhtpReportConnector, never).submitReport(any())(any())
    }
  }
}
