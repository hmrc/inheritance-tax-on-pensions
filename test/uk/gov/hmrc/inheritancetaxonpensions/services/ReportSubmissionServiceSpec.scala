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
import play.api.mvc.AnyContentAsEmpty
import uk.gov.hmrc.http.HeaderCarrier
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
    "postCode" -> "ZZ99 1AA",
    "country" -> "GB"
  )

  private val internationalBeneficiaryAddress = beneficiaryAddressPayloadSection.copy(
    addressLine1 = "2 ABCDE Street",
    addressLine2 = None,
    addressLine5 = Some("75001"),
    postCode = None,
    country = "FR"
  )

  private val deceasedPersonalDetailsUaJson = Json.obj(
    "title" -> "Mr",
    "firstForename" -> "Firstname",
    "secondForename" -> "Middlenametwo",
    "surname" -> "Surname"
  )

  private val individualPersonalRepResponseJson = Json.obj(
    "typeOfPr" -> "01",
    "prContactDetails" -> Json.obj(
      "title" -> "Mr",
      "firstForename" -> "Firstname",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname",
      "prAddress" -> testAddress
    )
  )

  private val individualPrDetailsUaJson = Json.obj(
    "individual" -> Json.obj(
      "title" -> "Mr",
      "firstForename" -> "Firstname",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname",
      "addressLine1" -> "1 ABCDE Street",
      "addressLine2" -> "FGHIJ Town",
      "postCode" -> "ZZ99 1AA",
      "country" -> "GB"
    )
  )

  private val organisationPersonalRepResponseJson = Json.obj(
    "typeOfPr" -> "02",
    "prContactDetails" -> Json.obj(
      "orgName" -> "Test Organisation",
      "title" -> "Ms",
      "firstForename" -> "Firstnametwo",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname",
      "prAddress" -> testAddress
    )
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
      "postCode" -> "ZZ99 1AA",
      "country" -> "GB"
    )
  )

  private val ihTaxInformationUaJson = Json.obj(
    "dateThePensionSchemeReceivedNoticeToPay" -> testPaymentNoticeDate,
    "totalIhtPayable" -> 1000.00,
    "totalInterestPayable" -> 50.00,
    "total" -> 1050.00
  )

  private val declarationUaJson = Json.obj(
    "submittedBy" -> "PSA",
    "submitterId" -> "TODO",
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
        "inheritanceTaxReference" -> testIhtReference,
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
      "inheritanceTaxReference" -> testIhtReference,
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
        IhtNoticeRequest(
          ReportDetails(
            pstr = testPstr,
            ihtPaymentReference = None
          ),
          deceasedPayloadSection,
          prDetailsIndividualPayloadSection,
          IhTaxInformation(
            ihTaxChangeFlag = None,
            dateNoticeReceived = testPaymentNoticeDate,
            noticeSubmittedByPr = Yes,
            knownBeneficiaries = Some(No),
            totalIhtPayable = Some(1000.00),
            totalInterestPayable = Some(50.00),
            total = Some(1050.00)
          ),
          beneficiary = None,
          declarations = declarationsPayloadSection
        )
      )
      Json.toJson(payloadCaptor.getValue.ihtNoticeRequest.personalRep) mustBe individualPersonalRepResponseJson
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
        IhtNoticeRequest(
          ReportDetails(
            pstr = testPstr,
            ihtPaymentReference = None
          ),
          deceasedPayloadSection,
          prDetailsIndividualPayloadSection,
          IhTaxInformation(
            ihTaxChangeFlag = None,
            dateNoticeReceived = testPaymentNoticeDate,
            noticeSubmittedByPr = Yes,
            knownBeneficiaries = Some(No),
            totalIhtPayable = Some(1000.00),
            totalInterestPayable = Some(50.00),
            total = Some(1050.00)
          ),
          beneficiary = None,
          declarations = declarationsPspPayloadSection
        )
      )
      Json.toJson(payloadCaptor.getValue.ihtNoticeRequest.personalRep) mustBe individualPersonalRepResponseJson
    }

    "return Right when submission is successful with organisation prType" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "inheritanceTaxReference" -> testIhtReference,
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
              "postCode" -> "ZZ99 1AA",
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
      when(mockJSONSchemaValidator.validatePayload(any(), any()))
        .thenReturn(SchemaValidationResult(Set.empty))

      val result = service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue
      result.isRight mustBe true
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue.ihtNoticeRequest.personalRep mustBe prDetailsOrganisationPayloadSection
      Json.toJson(payloadCaptor.getValue.ihtNoticeRequest.personalRep) mustBe organisationPersonalRepResponseJson
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
      result.getMessage must include("reasonNoNino")
      verify(mockIhtpReportConnector, never).submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]())
    }

    "fail before submission when mandatory pr firstForename field is missing" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "inheritanceTaxReference" -> testIhtReference,
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
              "postCode" -> "ZZ99 1AA",
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
          "inheritanceTaxReference" -> testIhtReference,
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
              "postCode" -> "ZZ99 1AA",
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
          "inheritanceTaxReference" -> testIhtReference,
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
          "inheritanceTaxReference" -> testIhtReference,
          "nameOfDeceased" -> Json.obj(
            "title" -> "Mrs",
            "firstForename" -> "Firstnametwo",
            "surname" -> "Surname"
          ),
          "hasNino" -> false,
          "reasonNoNino" -> "The deceased was not a UK citizen",
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
          reasonNoNino = Some("The deceased was not a UK citizen")
        )
      )
      val payloadCaptor: ArgumentCaptor[IhtpPaymentNoticeSubmission] =
        ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(payloadCaptor.capture())(any[HeaderCarrier]())
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        IhtNoticeRequest(
          ReportDetails(
            pstr = testPstr,
            ihtPaymentReference = None // as it is not submitted yet
          ),
          deceasedPerson,
          prDetailsIndividualPayloadSection,
          IhTaxInformation(
            ihTaxChangeFlag = None,
            dateNoticeReceived = testPaymentNoticeDate,
            noticeSubmittedByPr = Yes,
            knownBeneficiaries = Some(No),
            totalIhtPayable = Some(1000.00),
            totalInterestPayable = Some(50.00),
            total = Some(1050.00)
          ),
          beneficiary = None,
          declarations = declarationsPayloadSection
        )
      )
    }

    "return Right when submission is successful with a beneficiary in the payload" in {
      val testUserAnswers = UserAnswers(
        testUserAnswersId,
        srn,
        uuid,
        Json.obj(
          "nameOfDeceased" -> deceasedPersonalDetailsUaJson,
          "inheritanceTaxReference" -> testIhtReference,
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
              "beneficiaryIhtPayable" -> testMonetaryValue,
              "beneficiaryInterestPayable" -> testMonetaryValue,
              "beneficiaryAddress" -> Json.toJson(beneficiaryAddressPayloadSection),
              "beneficiaryDetails" -> Json.obj(
                "individual" -> Json.obj(
                  "title" -> "Mr",
                  "firstForename" -> "Firstnamethree",
                  "secondForename" -> "Middlenametwo",
                  "surname" -> "Surname"
                )
              ),
              "hasNino" -> false,
              "reasonNoNino" -> "Beneficiary does not have a NINO"
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
      payloadCaptor.getValue mustBe IhtpPaymentNoticeSubmission(
        IhtNoticeRequest(
          ReportDetails(
            pstr = testPstr,
            ihtPaymentReference = None
          ),
          deceasedPayloadSection,
          prDetailsOrganisationPayloadSection,
          IhTaxInformation(
            ihTaxChangeFlag = None,
            dateNoticeReceived = testPaymentNoticeDate,
            noticeSubmittedByPr = Yes,
            knownBeneficiaries = Some(Yes),
            totalIhtPayable = Some(1234.56),
            totalInterestPayable = Some(1234.56),
            total = Some(2469.12)
          ),
          Some(
            Beneficiaries(
              Seq(
                BeneficiaryDetails(
                  beneficiaryType = IndividualOrTrust.Individual,
                  beneficiaryPersonalDetails = beneficiaryPersonalDetailsPayloadSection,
                  beneficiaryAddress = beneficiaryAddressPayloadSection,
                  beneficiaryPaymentDetails = beneficiaryPaymentDetailsPayloadSection.copy(
                    beneficiaryIhtPayable = 1234.56,
                    beneficiaryInterestPayable = 1234.56,
                    beneficiaryTotal = 2469.12
                  )
                )
              )
            )
          ),
          declarations = declarationsPayloadSection
        )
      )
      Json.toJson(
        payloadCaptor.getValue.ihtNoticeRequest.beneficiary.get.beneficiaryDetails.head.beneficiaryPersonalDetails
      ) mustBe Json.obj(
        "title" -> "Mr",
        "firstForename" -> "Firstnamethree",
        "secondForename" -> "Middlenametwo",
        "surname" -> "Surname",
        "ninoExist" -> "No",
        "reasonNoNino" -> "Beneficiary does not have a NINO"
      )
    }

    "calculate ihTaxInformation totals as the sum of each beneficiary's payment amounts" in {
      val beneficiary = (ihtPayable: BigDecimal, interestPayable: BigDecimal, firstForename: String) =>
        Json.obj(
          "beneficiaryType" -> "individual",
          "beneficiaryIhtPayable" -> ihtPayable,
          "beneficiaryInterestPayable" -> interestPayable,
          "beneficiaryAddress" -> Json.toJson(beneficiaryAddressPayloadSection),
          "beneficiaryDetails" -> Json.obj(
            "individual" -> Json.obj(
              "title" -> "Mr",
              "firstForename" -> firstForename,
              "surname" -> "Surname"
            )
          ),
          "hasNino" -> true,
          "nino" -> testNino
        )

      val testUserAnswers = validUserAnswers.copy(data =
        validUserAnswers.data ++ Json.obj(
          "areBeneficiariesKnown" -> true,
          "beneficiaries" -> Json.arr(
            beneficiary(BigDecimal("1000.00"), BigDecimal("50.00"), "Firstnamethree"),
            beneficiary(BigDecimal("2000.50"), BigDecimal("25.25"), "Firstnamefour")
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
      payloadCaptor.getValue.ihtNoticeRequest.ihTaxInformation mustBe IhTaxInformation(
        ihTaxChangeFlag = None,
        dateNoticeReceived = testPaymentNoticeDate,
        noticeSubmittedByPr = Yes,
        knownBeneficiaries = Some(Yes),
        totalIhtPayable = Some(3000.50),
        totalInterestPayable = Some(75.25),
        total = Some(3075.75)
      )
    }

    "map each individual beneficiary's saved amount and UK or international address" in {
      val addresses = Seq(beneficiaryAddressPayloadSection, internationalBeneficiaryAddress)
      val beneficiaries = Seq(BigDecimal("0"), BigDecimal("999999999.99")).zip(addresses).map { (amount, address) =>
        Json.obj(
          "beneficiaryType" -> "individual",
          "beneficiaryIhtPayable" -> amount,
          "beneficiaryInterestPayable" -> amount,
          "beneficiaryAddress" -> Json.toJson(address),
          "beneficiaryDetails" -> Json.obj("individual" -> deceasedPersonalDetailsUaJson),
          "hasNino" -> true,
          "nino" -> testNino
        )
      }
      val answers = validUserAnswers.copy(data =
        validUserAnswers.data ++ Json.obj(
          "areBeneficiariesKnown" -> true,
          "beneficiaries" -> beneficiaries
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(answers)))
      when(mockIhtpReportConnector.submitReport(any[IhtpPaymentNoticeSubmission]())(any[HeaderCarrier]()))
        .thenReturn(Future.successful(Right(testSubmissionResponse)))

      service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).futureValue.isRight mustBe true
      val captured = ArgumentCaptor.forClass(classOf[IhtpPaymentNoticeSubmission])
      verify(mockIhtpReportConnector).submitReport(captured.capture())(any[HeaderCarrier]())
      captured.getValue.ihtNoticeRequest.beneficiary.get.beneficiaryDetails
        .map(_.beneficiaryPaymentDetails.beneficiaryIhtPayable) mustBe Seq(0.0, 999999999.99)
      captured.getValue.ihtNoticeRequest.beneficiary.get.beneficiaryDetails.map(_.beneficiaryAddress) mustBe addresses
    }

    "reject an individual beneficiary without a saved amount rather than submitting a placeholder" in {
      val answers = validUserAnswers.copy(data =
        validUserAnswers.data ++ Json.obj(
          "areBeneficiariesKnown" -> true,
          "beneficiaries" -> Json.arr(
            Json.obj(
              "beneficiaryType" -> "individual",
              "beneficiaryDetails" -> Json.obj("individual" -> deceasedPersonalDetailsUaJson)
            )
          )
        )
      )
      when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(answers)))

      service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue mustBe
        a[play.api.libs.json.JsResultException]
      verifyNoInteractions(mockIhtpReportConnector)
    }

    "reject beneficiaries without a saved address rather than submitting a placeholder" in {
      Seq("individual", "trust").foreach { beneficiaryType =>
        reset(mockUserAnswersRepository, mockIhtpReportConnector)
        val details =
          if (beneficiaryType == "trust") deceasedPersonalDetailsUaJson ++ Json.obj("beneficiaryTrustName" -> trustName)
          else deceasedPersonalDetailsUaJson
        val beneficiary = Json.obj(
          "beneficiaryType" -> beneficiaryType,
          "beneficiaryIhtPayable" -> BigDecimal("12.34"),
          "beneficiaryInterestPayable" -> BigDecimal("0"),
          "beneficiaryDetails" -> Json.obj(beneficiaryType -> details),
          "hasNino" -> true,
          "nino" -> testNino
        )
        val answers =
          validUserAnswers.copy(data = validUserAnswers.data ++ Json.obj("beneficiaries" -> Json.arr(beneficiary)))
        when(mockUserAnswersRepository.get(testUserAnswersId)).thenReturn(Future.successful(Some(answers)))

        service.submitReport(testUserAnswersId, testPstr, testIhtpAuthContext(rq)).failed.futureValue mustBe
          a[play.api.libs.json.JsResultException]
        verifyNoInteractions(mockIhtpReportConnector)
      }
    }

    "include a trust beneficiary name and saved international address in the payload" in {
      val testUserAnswers = validUserAnswers.copy(
        data = validUserAnswers.data ++ Json.obj(
          "areBeneficiariesKnown" -> true,
          "beneficiaries" -> Json.arr(
            Json.obj(
              "beneficiaryType" -> "trust",
              "beneficiaryAddress" -> Json.toJson(internationalBeneficiaryAddress),
              "beneficiaryDetails" -> Json.obj(
                "trust" -> Json.obj(
                  "beneficiaryTrustName" -> trustName,
                  "title" -> "Mr",
                  "firstForename" -> "Firstnamethree",
                  "secondForename" -> "Middlenametwo",
                  "surname" -> "Surname"
                )
              ),
              "beneficiaryIhtPayable" -> testMonetaryValue,
              "beneficiaryInterestPayable" -> testMonetaryValue
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

      val beneficiary = payloadCaptor.getValue.ihtNoticeRequest.beneficiary.get.beneficiaryDetails.head
      beneficiary.beneficiaryType mustBe IndividualOrTrust.Trust
      beneficiary.beneficiaryTrustName mustBe Some(trustName)
      beneficiary.beneficiaryPaymentDetails.beneficiaryIhtPayable mustBe testMonetaryValue.doubleValue
      beneficiary.beneficiaryPaymentDetails.beneficiaryInterestPayable mustBe testMonetaryValue.doubleValue
      beneficiary.beneficiaryAddress mustBe internationalBeneficiaryAddress
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
