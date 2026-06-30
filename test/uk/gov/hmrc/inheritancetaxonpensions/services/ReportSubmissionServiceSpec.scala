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
import uk.gov.hmrc.inheritancetaxonpensions.models._
import org.mockito.ArgumentMatchers._
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo.Yes
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.mockito.ArgumentCaptor
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

class ReportSubmissionServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with TestValues {

  override def beforeEach(): Unit = reset(mockUserAnswersRepository, mockIhtpReportConnector)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val mockUserAnswersRepository: UserAnswersRepository = mock[UserAnswersRepository]
  private val mockIhtpReportConnector: IhtpReportConnector = mock[IhtpReportConnector]
  private val service = new ReportSubmissionService(mockUserAnswersRepository, mockIhtpReportConnector)

  "submitReport" - {
    "return Right when submission is successful with a nino in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
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
              "surname" -> "Doe",
              "addressLine1" -> "1 ABCDE Street",
              "addressLine2" -> "FGHIJ Town",
              "ukPostcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          ),
          "ninoOrReason" -> Json.obj(
            "nino" -> testNino
          ),
          "didPrSubmit" -> true
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
            IndividualDetails(
              name = IndividualName(
                title = Some("Mr"),
                firstForename = "John",
                secondForename = Some("William"),
                surname = "Doe"
              ),
              address = AddressDetails(
                addressLine1 = "1 ABCDE Street",
                addressLine2 = "FGHIJ Town",
                ukPostcode = Some("ZZ99 1AA"),
                country = "GB"
              )
            )
          ),
          organisation = None
        ),
        IhtTaxInformation(
          didTheLegalPersonalRepresentativeSubmitTheNotice = Yes
        )
      )
      Json.toJson(payloadCaptor.getValue.lprDetails.individual.get) mustBe Json.obj(
        "title" -> "Mr",
        "firstForename" -> "John",
        "secondForename" -> "William",
        "surname" -> "Doe",
        "addressLine1" -> "1 ABCDE Street",
        "addressLine2" -> "FGHIJ Town",
        "ukPostcode" -> "ZZ99 1AA",
        "country" -> "GB"
      )
    }

    "return Right when submission is successful with organisation lprType" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
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
              "organisationName" -> "Test Organisation",
              "title" -> "Ms",
              "firstForename" -> "Jane",
              "secondForename" -> "Ann",
              "surname" -> "Doe"
            )
          ),
          "ninoOrReason" -> Json.obj(
            "nino" -> testNino
          ),
          "ihtTaxInformation" -> Json.obj(
            "didTheLegalPersonalRepresentativeSubmitTheNotice" -> "Yes"
          ),
          "didPrSubmit" -> true
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
        organisation = Some(
          OrganisationDetails(
            organisationName = "Test Organisation",
            title = Some("Ms"),
            firstForename = "Jane",
            secondForename = Some("Ann"),
            surname = "Doe"
          )
        )
      )
      Json.toJson(payloadCaptor.getValue.lprDetails.organisation.get) mustBe Json.obj(
        "organisationName" -> "Test Organisation",
        "title" -> "Ms",
        "firstForename" -> "Jane",
        "secondForename" -> "Ann",
        "surname" -> "Doe"
      )
    }

    "fail before submission when mandatory organisation PR name fields are missing" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
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

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue
      result mustBe a[IllegalArgumentException]
      result.getMessage must include("lprDetails.organisation")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpReportSubmission]())(any[HeaderCarrier]())
    }

    "return Left when the user answers are not found" in {
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(None))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result mustBe Left(ErrorCodes.entityNotFound)
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpReportSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when a mandatory LPR individual address field is missing" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
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
          "lprType" -> "individual",
          "lprDetails" -> Json.obj(
            "individual" -> Json.obj(
              "title" -> "Mr",
              "firstForename" -> "John",
              "surname" -> "Doe",
              "addressLine1" -> "1 ABCDE Street",
              "country" -> "GB"
            )
          ),
          "ninoOrReason" -> Json.obj(
            "nino" -> testNino
          )
        )
      )

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue
      result mustBe a[IllegalArgumentException]
      result.getMessage must include("lprDetails.individual")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpReportSubmission]())(any[HeaderCarrier]())
    }

    "return Left when connector returns an error and send the no nino reason in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
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
              "surname" -> "Doe",
              "addressLine1" -> "1 ABCDE Street",
              "addressLine2" -> "FGHIJ Town",
              "ukPostcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          ),
          "ninoOrReason" -> Json.obj(
            "reasonForNoNino" -> "The deceased was not a UK citizen"
          ),
          "didPrSubmit" -> true
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
            IndividualDetails(
              name = IndividualName(
                title = Some("Mr"),
                firstForename = "John",
                secondForename = Some("William"),
                surname = "Doe"
              ),
              address = AddressDetails(
                addressLine1 = "1 ABCDE Street",
                addressLine2 = "FGHIJ Town",
                ukPostcode = Some("ZZ99 1AA"),
                country = "GB"
              )
            )
          ),
          organisation = None
        ),
        IhtTaxInformation(
          didTheLegalPersonalRepresentativeSubmitTheNotice = Yes
        )
      )
    }
  }
}
