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
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo.{No, Yes}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.mockito.ArgumentCaptor
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers._
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.IndividualOrTrust

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

  private val testAddress = Json.obj(
    "addressLine1" -> "1 ABCDE Street",
    "addressLine2" -> "FGHIJ Town",
    "postcode" -> "ZZ99 1AA",
    "country" -> "GB"
  )

  private val deceasedPersonalDetailsJohnDoeJson = Json.obj(
    "title" -> "Mr",
    "firstForename" -> "John",
    "secondForename" -> "William",
    "surname" -> "Doe"
  )

  private val individualPersonalRepResponseJson = Json.obj(
    "typeOfPR" -> "01",
    "prContactDetails" -> Json.obj(
      "title" -> "Mr",
      "firstForename" -> "John",
      "secondForename" -> "William",
      "surname" -> "Doe"
    ),
    "prAddress" -> testAddress
  )

  private val individualPrDetailsJson = Json.obj(
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
  )

  private val organisationPersonalRepResponseJson = Json.obj(
    "typeOfPR" -> "02",
    "prContactDetails" -> Json.obj(
      "orgName" -> "Test Organisation",
      "title" -> "Ms",
      "firstForename" -> "Jane",
      "secondForename" -> "Ann",
      "surname" -> "Doe"
    ),
    "prAddress" -> testAddress
  )

  private val organisationPrDetailsJson = Json.obj(
    "organisation" -> Json.obj(
      "organisationName" -> "Test Organisation",
      "title" -> "Ms",
      "firstForename" -> "Jane",
      "secondForename" -> "Ann",
      "surname" -> "Doe",
      "addressLine1" -> "1 ABCDE Street",
      "addressLine2" -> "FGHIJ Town",
      "ukPostcode" -> "ZZ99 1AA",
      "country" -> "GB"
    )
  )

  private val ihTaxInformationJson = Json.obj(
    "dateThePensionSchemeReceivedNoticeToPay" -> testPaymentNoticeDate,
    "totalIHTPayable" -> "1000.00",
    "totalInterestPayable" -> "50.00",
    "total" -> "1050.00"
  )

  private val declarationJson = Json.obj(
    "submittedBy" -> "PSA",
    "submitterID" -> "TODO",
    "psaDeclaration" -> Json.obj(
      "psaDeclaration1" -> true,
      "psaDeclaration2" -> true
    ),
    "pspDeclaration" -> Json.obj(
      "pspDeclaration1" -> true,
      "pspDeclaration2" -> true,
      "psaid" -> "TODO"
    )
  )

  private def userAnswersWithNinoData(ninoData: JsObject): UserAnswers =
    UserAnswers(
      testUserAnswersId,
      srn,
      uuid,
      Json.obj(
        "inheritanceTaxReference" -> "A123459/25A",
        "nameOfDeceased" -> Json.obj(
          "firstForename" -> "John",
          "surname" -> "Doe"
        )
      ) ++ ninoData
    )

  "submitReport" - {
    "return Right when submission is successful with a nino in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "inheritanceTaxReference" -> "A123459/25A",
          "nameOfDeceased" -> deceasedPersonalDetailsJohnDoeJson,
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "individual",
          "didPrSubmit" -> true,
          "prDetails" -> individualPrDetailsJson,
          "areBeneficiariesKnown" -> false,
          "ihtTaxInformation" -> ihTaxInformationJson,
          "declarations" -> declarationJson
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        ReportDetails(
          pstr = testPstr,
          ihtPaymentReference = "A123459/25A"
        ),
        deceased,
        prDetailsIndividual,
        IhTaxInformation(
          ihTaxChangeFlag = None,
          dateNoticeReceived = testPaymentNoticeDate,
          noticeSubmittedByPR = Yes,
          knownBeneficiaries = Some(No),
          totalIHTPayable = Some("1000.00"),
          totalInterestPayable = Some("50.00"),
          total = Some("1050.00")
        ),
        beneficiaries = None,
        declarations = declarations
      )
      Json.toJson(payloadCaptor.getValue.personalRep) mustBe individualPersonalRepResponseJson
    }

    "return Right when submission is successful with organisation prType" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "inheritanceTaxReference" -> "A123459/25A",
          "nameOfDeceased" -> deceasedPersonalDetailsJohnDoeJson,
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "organisation",
          "prDetails" -> Json.obj(
            "organisation" -> Json.obj(
              "organisationName" -> "Test Organisation",
              "title" -> "Ms",
              "firstForename" -> "Jane",
              "secondForename" -> "Ann",
              "surname" -> "Doe",
              "addressLine1" -> "1 ABCDE Street",
              "addressLine2" -> "FGHIJ Town",
              "ukPostcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          ),
          "didPrSubmit" -> true,
          "areBeneficiariesKnown" -> false,
          "ihtTaxInformation" -> ihTaxInformationJson,
          "declarations" -> declarationJson
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue.personalRep mustBe prDetailsOrganisation
      Json.toJson(payloadCaptor.getValue.personalRep) mustBe organisationPersonalRepResponseJson
    }

    "fail before submission when the deceased NINO answer is missing" in {
      val testUserAnswers = userAnswersWithNinoData(Json.obj())

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue

      result mustBe a[IllegalArgumentException]
      result.getMessage must include("hasNino")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when NINO is selected but the NINO is missing" in {
      val testUserAnswers = userAnswersWithNinoData(Json.obj("hasNino" -> true))

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue

      result mustBe a[IllegalArgumentException]
      result.getMessage must include("nino")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when no NINO is selected but the reason is missing" in {
      val testUserAnswers = userAnswersWithNinoData(Json.obj("hasNino" -> false))

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue

      result mustBe a[IllegalArgumentException]
      result.getMessage must include("reasonForNoNino")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when mandatory pr firstForename field is missing" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "inheritanceTaxReference" -> "A123459/25A",
          "nameOfDeceased" -> deceasedPersonalDetailsJohnDoeJson,
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "organisation",
          "didPrSubmit" -> true,
          "prDetails" -> Json.obj(
            "organisation" -> Json.obj(
              "organisationName" -> "Test Organisation",
              "title" -> "Ms",
              "secondForename" -> "Ann",
              "surname" -> "Doe",
              "addressLine1" -> "1 ABCDE Street",
              "addressLine2" -> "FGHIJ Town",
              "postcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          )
        )
      )

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue
      result mustBe a[IllegalArgumentException]
      result.getMessage must include("prDetails.organisation")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "return Left when the user answers are not found" in {
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(None))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result mustBe Left(ErrorCodes.entityNotFound)
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when a mandatory PR address field is missing" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "nameOfDeceased" -> deceasedPersonalDetailsJohnDoeJson,
          "inheritanceTaxReference" -> "A123459/25A",
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "individual",
          "didPrSubmit" -> true,
          "prDetails" -> Json.obj(
            "individual" -> Json.obj(
              "title" -> "Mr",
              "firstForename" -> "John",
              "secondForename" -> "William",
              "surname" -> "Doe",
              "addressLine2" -> "FGHIJ Town",
              "postcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          ),
          "ihtTaxInformation" -> ihTaxInformationJson
        )
      )

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue
      result mustBe a[IllegalArgumentException]
      result.getMessage must include("prDetails.individual")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when the payment notice date is missing" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "inheritanceTaxReference" -> "A123459/25A",
          "nameOfDeceased" -> deceasedPersonalDetailsJohnDoeJson,
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "individual",
          "didPrSubmit" -> true,
          "areBeneficiariesKnown" -> false,
          "prDetails" -> individualPrDetailsJson,
          "ihtTaxInformation" -> Json.obj(
          ),
          "declarations" -> declarationJson
        )
      )

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr).failed.futureValue
      result mustBe a[IllegalArgumentException]
      result.getMessage must include("ihtTaxInformation.dateThePensionSchemeReceivedNoticeToPay")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
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
            "surname" -> "Doe"
          ),
          "hasNino" -> false,
          "reasonForNoNino" -> "The deceased was not a UK citizen",
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "individual",
          "prDetails" -> individualPrDetailsJson,
          "didPrSubmit" -> true,
          "areBeneficiariesKnown" -> false,
          "ihtTaxInformation" -> ihTaxInformationJson,
          "declarations" -> declarationJson
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Left(ErrorCodes.badRequest)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isLeft mustBe true
      val deceasedJaneDoe = deceased.copy(
        deceasedPersonalDetails = DeceasedPersonalDetails(
          title = Some("Mrs"),
          firstForename = "Jane",
          secondForename = None,
          surname = "Doe",
          ninoExist = No,
          nino = None,
          reasonNoNINO = Some("The deceased was not a UK citizen")
        )
      )
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        ReportDetails(
          pstr = testPstr,
          ihtPaymentReference = "A123459/25A"
        ),
        deceasedJaneDoe,
        prDetailsIndividual,
        IhTaxInformation(
          ihTaxChangeFlag = None,
          dateNoticeReceived = testPaymentNoticeDate,
          noticeSubmittedByPR = Yes,
          knownBeneficiaries = Some(No),
          totalIHTPayable = Some("1000.00"),
          totalInterestPayable = Some("50.00"),
          total = Some("1050.00")
        ),
        beneficiaries = None,
        declarations = declarations
      )
    }

    "return Right when submission is successful with a beneficiary in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "nameOfDeceased" -> deceasedPersonalDetailsJohnDoeJson,
          "inheritanceTaxReference" -> "A123459/25A",
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "organisation",
          "didPrSubmit" -> true,
          "prDetails" -> organisationPrDetailsJson,
          "ihtTaxInformation" -> Json.obj(
            "dateThePensionSchemeReceivedNoticeToPay" -> testPaymentNoticeDate
          ),
          "areBeneficiariesKnown" -> true,
          "beneficiaries" -> Json.arr(
            Json.obj(
              "beneficiaryType" -> "individual",
              "beneficiaryDetails" -> Json.obj(
                "individual" -> Json.obj(
                  "title" -> "Mr",
                  "firstForename" -> "Paul",
                  "secondForename" -> "William",
                  "surname" -> "Doe"
                )
              )
            )
          ),
          "hasNino" -> true,
          "nino" -> testNino
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        ReportDetails(
          pstr = testPstr,
          ihtPaymentReference = "A123459/25A"
        ),
        deceased,
        prDetailsOrganisation,
        IhTaxInformation(
          ihTaxChangeFlag = None,
          dateNoticeReceived = testPaymentNoticeDate,
          noticeSubmittedByPR = Yes,
          knownBeneficiaries = Some(Yes),
          totalIHTPayable = None,
          totalInterestPayable = None,
          total = None
        ),
        Some(
          Seq(
            BeneficiaryDetails(
              beneficiaryType = IndividualOrTrust.Individual,
              beneficiaryContactDetails = beneficiaryContactDetails,
              beneficiaryPaymentDetails = beneficiaryPaymentDetails
            )
          )
        ),
        declarations = declarations
      )
      Json.toJson(
        payloadCaptor.getValue.beneficiaries.get.head.beneficiaryContactDetails.beneficiaryPersonalDetails
      ) mustBe Json.obj(
        "title" -> "Mr",
        "firstForename" -> "Paul",
        "secondForename" -> "William",
        "surname" -> "Doe",
        "ninoExist" -> "Yes"
      )
    }
  }
}
