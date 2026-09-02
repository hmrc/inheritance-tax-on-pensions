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

package utils

import play.api.mvc.Request
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.IndividualOrOrg.{Individual => IorOIndividual, Organisation}
import generators.Generators
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import play.api.libs.json.{JsArray, JsObject, Json}
import com.networknt.schema
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo.{No, Yes}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants.psaEnrolmentKey
import uk.gov.hmrc.inheritancetaxonpensions.models._
import com.networknt.schema.path.{NodePath, PathType}
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.IndividualOrTrust.{Individual => IorTIndividual}
import uk.gov.hmrc.inheritancetaxonpensions.auth.IhtpAuthContext

import java.time._

trait TestValues extends Generators {
  val clockMillis: Long = 1718118467838L
  val clock: Clock = Clock.fixed(Instant.ofEpochMilli(clockMillis), ZoneId.of("UTC"))

  val externalId: String = "externalId"
  val enrolments: Enrolments = Enrolments(
    Set(
      Enrolment(
        psaEnrolmentKey,
        Seq(
          EnrolmentIdentifier("PSAID", "A0000000")
        ),
        "Activated",
        None
      )
    )
  )
  val pstr = "testPstr"
  val srn = "S2400000001"
  val uuid = "ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
  val psrVersion = "001"
  val psrFormBundleNumber = "1234567890"
  val schemeName = "SchemeName"
  val userName = "userName"
  val psaPspId = "psaPspId"
  val credentialRole = "credentialRole"
  val sampleToday: LocalDate = LocalDate.of(2023, 10, 19)
  val psaId = "A0000000"
  val pspId = "21000005"
  val testNino: String = ninoGen.sample.get
  val testDateOfBirth = "1950-01-01"
  val testDateOfDeath = "2026-01-01"
  val testDateFrom = "2025-01-01"
  val testDateTo = "2026-01-01"
  val testPaymentNoticeDate = "2026-03-27"
  val testAddressLine1 = "1 ABCDE Street"
  val testAddressLine2 = "FGHIJ Town"
  val testUkPostcode = "ZZ99 1AA"
  val testCountry = "GB"
  val testUserAnswersId = "testUserAnswersId"
  val testPstr = "12345678"
  val emptyUserAnswers: UserAnswers = UserAnswers(s"$srn-$uuid", srn, uuid)
  val testIhtPaymentReference: String = "A123456/25A629671"

  val testSubmissionResponse: IhtpPaymentNoticeResponse = IhtpPaymentNoticeResponse(
    formBundleNo = "910000000000",
    ihtPaymentReference = testIhtPaymentReference
  )

  private val deceasedPersonalDetailsPayloadSection = DeceasedPersonalDetails(
    title = Some("Mr"),
    firstForename = "Firstname",
    secondForename = Some("Middlenametwo"),
    surname = "Surname",
    ninoExist = Yes,
    nino = Some(testNino),
    reasonNoNINO = None
  )

  private val deceasedDetailsPayloadSection = DeceasedDetails(
    deceasedsDOB = testDateOfBirth,
    deceasedsDOD = testDateOfDeath,
    ihtRefNumber = "A123459/25A"
  )

  val deceasedPayloadSection: Deceased = Deceased(
    deceasedPersonalDetails = deceasedPersonalDetailsPayloadSection,
    deceasedDetails = deceasedDetailsPayloadSection
  )

  private val prContactDetailsIndividualPayloadSection = PrContactDetails(
    title = Some("Mr"),
    firstForename = "Firstname",
    secondForename = Some("Middlenametwo"),
    surname = "Surname"
  )

  private val prContactDetailsOrganisationPayloadSection = PrContactDetails(
    orgName = Some("Test Organisation"),
    title = Some("Ms"),
    firstForename = "Firstnametwo",
    secondForename = Some("Middlenametwo"),
    surname = "Surname"
  )

  val prDetailsIndividualPayloadSection: PrDetails = PrDetails(
    prChangeFlag = None,
    typeOfPR = IorOIndividual,
    prContactDetails = prContactDetailsIndividualPayloadSection,
    prAddress = AddressDetails(
      addressLine1 = testAddressLine1,
      addressLine2 = testAddressLine2,
      postcode = Some(testUkPostcode),
      country = testCountry
    )
  )
  val prDetailsOrganisationPayloadSection: PrDetails = PrDetails(
    prChangeFlag = None,
    typeOfPR = Organisation,
    prContactDetails = prContactDetailsOrganisationPayloadSection,
    prAddress = AddressDetails(
      addressLine1 = "1 ABCDE Street",
      addressLine2 = "FGHIJ Town",
      postcode = Some("ZZ99 1AA"),
      country = "GB"
    )
  )

  private val beneficiaryPersonalDetails = BeneficiaryPersonalDetails(
    title = Some("Mr"),
    firstForename = "Paul",
    secondForename = Some("Middlenametwo"),
    surname = "Surname",
    ninoExist = No,
    nino = None,
    reasonNoNINO = Some("TODO")
  )

  val beneficiaryContactDetailsPayloadSection: BeneficiaryContactDetails = BeneficiaryContactDetails(
    beneficiaryPersonalDetails = beneficiaryPersonalDetails,
    beneficiaryAddress = AddressDetails(
      addressLine1 = testAddressLine1,
      addressLine2 = testAddressLine2,
      postcode = Some(testUkPostcode),
      country = testCountry
    )
  )

  val beneficiaryPaymentDetailsPayloadSection: BeneficiaryPaymentDetails = BeneficiaryPaymentDetails(
    beneficiaryIHTPayable = 99.99,
    beneficiaryInterestPayable = 99.99,
    beneficiaryTotal = 99.99
  )

  val declarationsPayloadSection: Declarations = Declarations(
    submittedBy = "PSA",
    submitterID = psaId,
    psaDeclaration = Some(PsaDeclaration("true", "true")),
    pspDeclaration = None
  )

  val declarationsPspPayloadSection: Declarations = Declarations(
    submittedBy = "PSP",
    submitterID = pspId,
    psaDeclaration = None,
    pspDeclaration = Some(PspDeclaration("true", "true", "TODO"))
  )

  val testReportSubmissionRequestBody: IhtpPaymentNoticeSubmission = IhtpPaymentNoticeSubmission(
    ReportDetails(
      pstr = "24000001IN",
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

  val testReportSubmissionRequestBodyOrganisation: IhtpPaymentNoticeSubmission = IhtpPaymentNoticeSubmission(
    ReportDetails(
      pstr = "24000001IN",
      ihtPaymentReference = None
    ),
    deceasedPayloadSection,
    prDetailsOrganisationPayloadSection,
    IhTaxInformation(
      ihTaxChangeFlag = None,
      dateNoticeReceived = testPaymentNoticeDate,
      noticeSubmittedByPR = Yes,
      knownBeneficiaries = Some(No),
      totalIHTPayable = Some(1000.00),
      totalInterestPayable = Some(50.00),
      total = Some(1050.00)
    ),
    Some(
      Seq(
        BeneficiaryDetails(
          beneficiaryType = IorTIndividual,
          beneficiaryContactDetails = beneficiaryContactDetailsPayloadSection,
          beneficiaryPaymentDetails = beneficiaryPaymentDetailsPayloadSection
        )
      )
    ),
    declarations = declarationsPayloadSection
  )

  val testReportSubmissionResponse: IhtpPaymentNoticeResponse = IhtpPaymentNoticeResponse("910000000000", "123456789")

  val testSchemaValidationError: schema.Error = schema.Error
    .builder()
    .instanceLocation(NodePath(PathType.JSON_PATH).append("testPath"))
    .message("customMessage")
    .build()

  def testIhtpAuthContext[A](req: Request[A]): IhtpAuthContext[A] = IhtpAuthContext[A](
    externalId = externalId,
    psaPspId = psaId,
    credentialRole = Constants.psaId,
    request = req
  )

  def testPspIhtpAuthContext[A](req: Request[A]): IhtpAuthContext[A] = IhtpAuthContext[A](
    externalId = externalId,
    psaPspId = pspId,
    credentialRole = Constants.pspId,
    request = req
  )

  val testUserAnswerJson: JsObject = Json.obj(
    "inheritanceTaxReference" -> "A123459/25A",
    "nameOfDeceased" -> Json.obj(
      "title" -> "Mr",
      "firstForename" -> "Firstname",
      "secondForename" -> "Middlenametwo",
      "surname" -> "Surname"
    ),
    "birthDeathDates" -> Json.obj(
      "dateOfBirth" -> "1950-01-01",
      "dateOfDeath" -> "2026-01-01"
    ),
    "prType" -> "individual",
    "prDetails" -> Json.obj(
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
    ),
    "hasNino" -> true,
    "nino" -> testNino,
    "ihtTaxInformation" -> Json.obj(
      "dateThePensionSchemeReceivedNoticeToPay" -> "2026-03-27"
    ),
    "didPrSubmit" -> true,
    "ihtPaymentReference" -> testIhtPaymentReference,
    "formBundleNo" -> "000012345678",
    "processingDateTime" -> "2026-08-12T16:26:37"
  )

  val testOverviewResponse: JsArray = Json.arr(
    Json.obj(
      "fbNumber" -> "100000000000",
      "submissionDate" -> "2026-04-10T16:12:49Z",
      "paymentDueDate" -> "2026-10-10",
      "ihtVersion" -> "001",
      "inheritanceTaxReference" -> "A123456/25A",
      "paymentReference" -> testIhtPaymentReference,
      "title" -> "Dr",
      "firstForename" -> "Firstname",
      "secondForename" -> "E",
      "surname" -> "Surname",
      "ihtpStatus" -> "Paid"
    ),
    Json.obj(
      "fbNumber" -> "100000000000",
      "submissionDate" -> "2026-04-10T16:12:49Z",
      "paymentDueDate" -> "2026-10-10",
      "ihtVersion" -> "002",
      "inheritanceTaxReference" -> "A123456/25A",
      "paymentReference" -> testIhtPaymentReference,
      "title" -> "Dr",
      "firstForename" -> "Firstname",
      "secondForename" -> "E",
      "surname" -> "Surname",
      "ihtpStatus" -> "Not reconciled"
    ),
    Json.obj(
      "fbNumber" -> "200000000000",
      "submissionDate" -> "2026-04-10T16:12:49Z",
      "paymentDueDate" -> "2026-10-10",
      "ihtVersion" -> "001",
      "inheritanceTaxReference" -> "A223456/25A",
      "paymentReference" -> "A223456/25A629671",
      "title" -> "Ms",
      "firstForename" -> "Firstnametwo",
      "secondForename" -> "E",
      "surname" -> "Surname",
      "ihtpStatus" -> "Not reconciled"
    )
  )
}
