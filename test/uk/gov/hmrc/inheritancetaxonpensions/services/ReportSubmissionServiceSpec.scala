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

import play.api.test.FakeRequest
import com.networknt.schema.Error
import play.api.mvc.AnyContentAsEmpty
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.models._
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.inheritancetaxonpensions.validators.{JSONSchemaValidator, SchemaValidationResult}
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
  private val mockJSONSchemaValidator: JSONSchemaValidator = mock[JSONSchemaValidator]
  private val mockIhtpReportConnector: IhtpReportConnector = mock[IhtpReportConnector]
  private val service =
    new ReportSubmissionService(mockUserAnswersRepository, mockJSONSchemaValidator, mockIhtpReportConnector)

  private val testAddress = Json.obj(
    "addressLine1" -> "1 ABCDE Street",
    "addressLine2" -> "FGHIJ Town",
    "postcode" -> "ZZ99 1AA",
    "country" -> "GB"
  )

  private val deceasedPersonalDetailsUaJson = Json.obj(
    "title" -> "Mr",
    "firstForename" -> "Firstname",
    "secondForename" -> "Middlenametwo",
    "surname" -> "Surname"
  )

  private val individualPersonalRepResponseJson = Json.obj(
    "typeOfPR" -> "01",
    "prContactDetails" -> Json.obj(
      "title" -> "Mr",
      "firstForename" -> "Firstname",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname"
    ),
    "prAddress" -> testAddress
  )

  private val individualPrDetailsUaJson = Json.obj(
    "individual" -> Json.obj(
      "title" -> "Mr",
      "firstForename" -> "Firstname",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname",
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
      "firstForename" -> "Firstnametwo",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname"
    ),
    "prAddress" -> testAddress
  )

  private val organisationPrDetailsUaJson = Json.obj(
    "organisation" -> Json.obj(
      "organisationName" -> "Test Organisation",
      "title" -> "Ms",
      "firstForename" -> "Firstnametwo",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname",
      "addressLine1" -> "1 ABCDE Street",
      "addressLine2" -> "FGHIJ Town",
      "ukPostcode" -> "ZZ99 1AA",
      "country" -> "GB"
    )
  )

  private val ihTaxInformationUaJson = Json.obj(
    "dateThePensionSchemeReceivedNoticeToPay" -> testPaymentNoticeDate,
    "totalIHTPayable" -> 1000.00,
    "totalInterestPayable" -> 50.00,
    "total" -> 1050.00
  )

  private val declarationUaJson = Json.obj(
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
          "firstForename" -> "Firstname",
          "surname" -> "Surname"
        )
      ) ++ ninoData
    )

  private val validUserAnswers = UserAnswers(
    testUserAnswersId,
    srn,
    uuid,
    Json.obj(
      "inheritanceTaxReference" -> "A123459/25A",
      "nameOfDeceased" -> deceasedPersonalDetailsUaJson,
      "hasNino" -> true,
      "nino" -> testNino,
      "birthDeathDates" -> Json.obj(
        "dateOfBirth" -> testDateOfBirth,
        "dateOfDeath" -> testDateOfDeath
      ),
      "prType" -> "individual",
      "didPrSubmit" -> true,
      "prDetails" -> individualPrDetailsUaJson,
      "areBeneficiariesKnown" -> false,
      "ihtTaxInformation" -> ihTaxInformationUaJson,
      "declarations" -> declarationUaJson
    )
  )

  private implicit val rq: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  "submitReport" - {
    "return Right when submission is successful with a nino in the payload" in {

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(validUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))
      when(mockJSONSchemaValidator.validatePayload(any(), any()))
        .thenReturn(SchemaValidationResult(Set.empty))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        ReportDetails(
          pstr = testPstr,
          ihtPaymentReference = None
        ),
        deceasedPayloadSection,
        prDetailsIndividualPayloadSection,
        IhTaxInformation(
          ihTaxChangeFlag = None,
          dateNoticeReceived = testPaymentNoticeDate,
          noticeSubmittedByPR = Yes,
          knownBeneficiaries = Some(No),
          totalIHTPayable = Some(1000.00),
          totalInterestPayable = Some(50.00),
          total = Some(1050.00)
        ),
        beneficiaries = None,
        declarations = declarationsPayloadSection
      )
      Json.toJson(payloadCaptor.getValue.personalRep) mustBe individualPersonalRepResponseJson
    }

    "return Right when submission is successful when PSP fills in the declaration" in {

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(validUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))
      when(mockJSONSchemaValidator.validatePayload(any(), any()))
        .thenReturn(SchemaValidationResult(Set.empty))

      // auth context as PSP:
      val result = service.submitReport(testUserAnswersId, testPstr, testPspIhtpAuthContext(rq)).futureValue

      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        ReportDetails(
          pstr = testPstr,
          ihtPaymentReference = None
        ),
        deceasedPayloadSection,
        prDetailsIndividualPayloadSection,
        IhTaxInformation(
          ihTaxChangeFlag = None,
          dateNoticeReceived = testPaymentNoticeDate,
          noticeSubmittedByPR = Yes,
          knownBeneficiaries = Some(No),
          totalIHTPayable = Some(1000.00),
          totalInterestPayable = Some(50.00),
          total = Some(1050.00)
        ),
        beneficiaries = None,
        declarations = declarationsPspPayloadSection
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
          "nameOfDeceased" -> deceasedPersonalDetailsUaJson,
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
              "firstForename" -> "Firstnametwo",
              "secondForename" -> "Middlenametwo",
              "surname" -> "Surname",
              "addressLine1" -> "1 ABCDE Street",
              "addressLine2" -> "FGHIJ Town",
              "ukPostcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          ),
          "didPrSubmit" -> true,
          "areBeneficiariesKnown" -> false,
          "ihtTaxInformation" -> ihTaxInformationUaJson,
          "declarations" -> declarationUaJson
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue.personalRep mustBe prDetailsOrganisationPayloadSection
      Json.toJson(payloadCaptor.getValue.personalRep) mustBe organisationPersonalRepResponseJson
    }

    "fail before submission when the deceased NINO answer is missing" in {
      val testUserAnswers = userAnswersWithNinoData(Json.obj())

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue

      result mustBe a[IllegalArgumentException]
      result.getMessage must include("hasNino")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when NINO is selected but the NINO is missing" in {
      val testUserAnswers = userAnswersWithNinoData(Json.obj("hasNino" -> true))

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue

      result mustBe a[IllegalArgumentException]
      result.getMessage must include("nino")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when no NINO is selected but the reason is missing" in {
      val testUserAnswers = userAnswersWithNinoData(Json.obj("hasNino" -> false))

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue

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
          "nameOfDeceased" -> deceasedPersonalDetailsUaJson,
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
              "secondForename" -> "Middlenametwo",
              "surname" -> "Surname",
              "addressLine1" -> "1 ABCDE Street",
              "addressLine2" -> "FGHIJ Town",
              "postcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          )
        )
      )

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue
      result mustBe a[IllegalArgumentException]
      result.getMessage must include("prDetails.organisation")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "return Left when the user answers are not found" in {
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(None))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue
      result mustBe Left(ErrorCodes.entityNotFound)
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when a mandatory PR address field is missing" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "nameOfDeceased" -> deceasedPersonalDetailsUaJson,
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
              "firstForename" -> "Firstname",
              "secondForename" -> "Middlenametwo",
              "surname" -> "Surname",
              "addressLine2" -> "FGHIJ Town",
              "postcode" -> "ZZ99 1AA",
              "country" -> "GB"
            )
          ),
          "ihtTaxInformation" -> ihTaxInformationUaJson
        )
      )

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue
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
          "nameOfDeceased" -> deceasedPersonalDetailsUaJson,
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "individual",
          "didPrSubmit" -> true,
          "areBeneficiariesKnown" -> false,
          "prDetails" -> individualPrDetailsUaJson,
          "ihtTaxInformation" -> Json.obj(
          ),
          "declarations" -> declarationUaJson
        )
      )

      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue
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
            "firstForename" -> "Firstnametwo",
            "surname" -> "Surname"
          ),
          "hasNino" -> false,
          "reasonForNoNino" -> "The deceased was not a UK citizen",
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "individual",
          "prDetails" -> individualPrDetailsUaJson,
          "didPrSubmit" -> true,
          "areBeneficiariesKnown" -> false,
          "ihtTaxInformation" -> ihTaxInformationUaJson,
          "declarations" -> declarationUaJson
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Left(ErrorCodes.badRequest)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue
      result.isLeft mustBe true
      val deceasedPerson = deceasedPayloadSection.copy(
        deceasedPersonalDetails = DeceasedPersonalDetails(
          title = Some("Mrs"),
          firstForename = "Firstnametwo",
          secondForename = None,
          surname = "Surname",
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
          ihtPaymentReference = None // as it is not submitted yet
        ),
        deceasedPerson,
        prDetailsIndividualPayloadSection,
        IhTaxInformation(
          ihTaxChangeFlag = None,
          dateNoticeReceived = testPaymentNoticeDate,
          noticeSubmittedByPR = Yes,
          knownBeneficiaries = Some(No),
          totalIHTPayable = Some(1000.00),
          totalInterestPayable = Some(50.00),
          total = Some(1050.00)
        ),
        beneficiaries = None,
        declarations = declarationsPayloadSection
      )
    }

    "return Right when submission is successful with a beneficiary in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "nameOfDeceased" -> deceasedPersonalDetailsUaJson,
          "inheritanceTaxReference" -> "A123459/25A",
          "hasNino" -> true,
          "nino" -> testNino,
          "birthDeathDates" -> Json.obj(
            "dateOfBirth" -> testDateOfBirth,
            "dateOfDeath" -> testDateOfDeath
          ),
          "prType" -> "organisation",
          "didPrSubmit" -> true,
          "prDetails" -> organisationPrDetailsUaJson,
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
                  "firstForename" -> "Firstnamethree",
                  "secondForename" -> "Middlenametwo",
                  "surname" -> "Surname"
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

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        ReportDetails(
          pstr = testPstr,
          ihtPaymentReference = None
        ),
        deceasedPayloadSection,
        prDetailsOrganisationPayloadSection,
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
              beneficiaryContactDetails = beneficiaryContactDetailsPayloadSection,
              beneficiaryPaymentDetails = beneficiaryPaymentDetailsPayloadSection
            )
          )
        ),
        declarations = declarationsPayloadSection
      )
      Json.toJson(
        payloadCaptor.getValue.beneficiaries.get.head.beneficiaryContactDetails.beneficiaryPersonalDetails
      ) mustBe Json.obj(
        "title" -> "Mr",
        "firstForename" -> "Firstnamethree",
        "secondForename" -> "Middlenametwo",
        "surname" -> "Surname",
        "ninoExist" -> "No",
        "reasonNoNINO" -> "TODO"
      )
    }

    "include an organisation or trust beneficiary name in the payload" in {
      val testUserAnswers = validUserAnswers.copy(
        data = validUserAnswers.data ++ Json.obj(
          "areBeneficiariesKnown" -> true,
          "beneficiaries" -> Json.arr(
            Json.obj(
              "beneficiaryType" -> "organisation",
              "beneficiaryDetails" -> Json.obj(
                "organisation" -> Json.obj(
                  "beneficiaryTrstName" -> trustName
                )
              )
            )
          )
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(testUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue

      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())

      val beneficiary = payloadCaptor.getValue.beneficiaries.get.head
      beneficiary.beneficiaryType mustBe IndividualOrTrust.Trust
      beneficiary.beneficiaryContactDetails.beneficiaryTrstName mustBe Some(trustName)
      (Json.toJson(beneficiary).toString must not).include("hmrcReferenceNumber")
    }

    "should throw SchemaValidationFailureException with validation error details when schema validation fails" in {
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(validUserAnswers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))
      when(mockJSONSchemaValidator.validatePayload(any(), any()))
        .thenReturn(SchemaValidationResult(Set(testSchemaValidationError)))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue

      result mustBe a[SchemaValidationFailureException]
      result.getMessage must include("testPath: customMessage")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

  }
}
