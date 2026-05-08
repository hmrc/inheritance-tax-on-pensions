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
import uk.gov.hmrc.inheritancetaxonpensions.models._
import org.mockito.ArgumentMatchers._
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import org.mockito.ArgumentCaptor
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

import java.time.Instant

class ReportSubmissionServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with TestValues {

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
    "return Right when submission is successful with a nino in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        Json.obj(
          "inheritanceTaxReference" -> "A123459/25A",
          "nameOfDeceased" -> Json.obj(
            "title" -> "Mr",
            "firstForename" -> "John",
            "secondForename" -> "William",
            "surname" -> "Doe"
          ),
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "lprType" -> "individual",
          "lprDetails" -> Json.obj(
            "individual" -> Json.obj(
              "title" -> "Mr",
              "firstForename" -> "John",
              "secondForename" -> "William",
              "surname" -> "Doe"
            )
          ),
          "ninoOrReason" -> Json.obj(
            "nino" -> testNino
          )
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpReportSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpReportSubmission] = ArgumentCaptor.forClass(classOf[IhtpReportSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpReportSubmission(
        ReportDetails(
          pstr = testPstr
        ),
        DeceasedDetails(
          inheritanceTaxReference = "A123459/25A",
          title = Some("Mr"),
          firstForename = "John",
          secondForename = Some("William"),
          surname = "Doe",
          dateOfBirth = testDateOfBirth,
          dateOfDeath = testDateOfDeath,
          nino = Some(testNino),
          reasonForNoNino = None
        ),
        LprDetails(
          individual = Some(
            IndividualName(
              title = Some("Mr"),
              firstForename = "John",
              secondForename = Some("William"),
              surname = "Doe"
            )
          ),
          organisation = None
        )
      )
    }

    "return Right when submission is successful with organisation lprType" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        Json.obj(
          "inheritanceTaxReference" -> "A123459/25A",
          "nameOfDeceased" -> Json.obj(
            "title" -> "Mr",
            "firstForename" -> "John",
            "surname" -> "Doe"
          ),
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "lprType" -> "organisation",
          "lprDetails" -> Json.obj(
            "organisation" -> Json.obj(
              "organisationName" -> "Test Organisation"
            )
          ),
          "ninoOrReason" -> Json.obj(
            "nino" -> testNino
          )
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpReportSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpReportSubmission] = ArgumentCaptor.forClass(classOf[IhtpReportSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue.lprDetails mustBe LprDetails(
        individual = None,
        organisation = Some(OrganisationName("Test Organisation"))
      )
    }

    "return Left when the user answers are not found" in {
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(None))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result mustBe Left(ErrorCodes.entityNotFound)
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpReportSubmission]())(any[HeaderCarrier]())
    }

    "return Left when connector returns an error and send the no nino reason in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        Json.obj(
          "inheritanceTaxReference" -> "A123459/25A",
          "nameOfDeceased" -> Json.obj(
            "title" -> "Mrs",
            "firstForename" -> "Jane",
            "secondForename" -> None,
            "surname" -> "Doe"
          ),
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "lprType" -> "individual",
          "lprDetails" -> Json.obj(
            "individual" -> Json.obj(
              "title" -> "Mr",
              "firstForename" -> "John",
              "secondForename" -> "William",
              "surname" -> "Doe"
            )
          ),
          "ninoOrReason" -> Json.obj(
            "reasonForNoNino" -> "The deceased was not a UK citizen"
          )
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpReportSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Left(ErrorCodes.badRequest)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isLeft mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpReportSubmission] = ArgumentCaptor.forClass(classOf[IhtpReportSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpReportSubmission(
        ReportDetails(
          pstr = testPstr
        ),
        DeceasedDetails(
          inheritanceTaxReference = "A123459/25A",
          title = Some("Mrs"),
          firstForename = "Jane",
          secondForename = None,
          surname = "Doe",
          dateOfBirth = testDateOfBirth,
          dateOfDeath = testDateOfDeath,
          nino = None,
          reasonForNoNino = Some("The deceased was not a UK citizen")
        ),
        LprDetails(
          individual = Some(
            IndividualName(
              title = Some("Mr"),
              firstForename = "John",
              secondForename = Some("William"),
              surname = "Doe"
            )
          ),
          organisation = None
        )
      )
    }
  }
}
