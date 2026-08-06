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

import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.IndividualOrOrg.{Individual => IorOIndividual, Organisation}
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.IndividualOrTrust.{Individual => IorTIndividual}
import generators.Generators
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo.{No, Yes}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants.psaEnrolmentKey
import uk.gov.hmrc.inheritancetaxonpensions.models._
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo

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
  val testSubmissionResponse = IhtpPaymentNoticeResponse(
    formBundleNo = "910000000000",
    ihtPaymentReference = "A123459/25A"
  )

  val deceasedPersonalDetails = DeceasedPersonalDetails(
    title = Some("Mr"),
    firstForename = "John",
    secondForename = Some("William"),
    surname = "Doe",
    ninoExist = Yes,
    nino = Some(testNino),
    reasonNoNINO = None
  )

  val deceasedDetails = DeceasedDetails(
    deceasedsDOB = testDateOfBirth,
    deceasedsDOD = testDateOfDeath,
    ihtRefNumber = "A123459/25A"
  )

  val deceased = Deceased(
    deceasedPersonalDetails = deceasedPersonalDetails,
    deceasedDetails = deceasedDetails
  )

  val prContactDetailsIndividual = PrContactDetails(
    title = Some("Mr"),
    firstForename = "John",
    secondForename = Some("William"),
    surname = "Doe"
  )

  val prContactDetailsOrganisation = PrContactDetails(
    orgName = Some("Test Organisation"),
    title = Some("Ms"),
    firstForename = "Jane",
    secondForename = Some("Ann"),
    surname = "Doe"
  )

  val prDetailsIndividual = PrDetails(
    prChangeFlag = None,
    typeOfPR = IorOIndividual,
    prContactDetails = prContactDetailsIndividual,
    prAddress = AddressDetails(
      addressLine1 = testAddressLine1,
      addressLine2 = testAddressLine2,
      postcode = Some(testUkPostcode),
      country = testCountry
    )
  )
  val prDetailsOrganisation = PrDetails(
    prChangeFlag = None,
    typeOfPR = Organisation,
    prContactDetails = prContactDetailsOrganisation,
    prAddress = AddressDetails(
      addressLine1 = "1 ABCDE Street",
      addressLine2 = "FGHIJ Town",
      postcode = Some("ZZ99 1AA"),
      country = "GB"
    )
  )

  val beneficiaryPersonalDetails = BeneficiaryPersonalDetails(
    title = Some("Mr"),
    firstForename = "Paul",
    secondForename = Some("William"),
    surname = "Doe",
    ninoExist = Yes,
    nino = None,
    reasonNoNINO = None
  )

  val beneficiaryContactDetails = BeneficiaryContactDetails(
    beneficiaryPersonalDetails = beneficiaryPersonalDetails,
    beneficiaryAddress = AddressDetails(
      addressLine1 = testAddressLine1,
      addressLine2 = testAddressLine2,
      postcode = Some(testUkPostcode),
      country = testCountry
    )
  )

  val beneficiaryPaymentDetails = BeneficiaryPaymentDetails(
    beneficiaryIHTPayable = "TODO",
    beneficiaryInterestPayable = "TODO",
    beneficiaryTotal = "TODO"
  )

  val declarations = Declarations(
    submittedBy = "PSA",
    submitterID = "TODO",
    psaDeclaration = Some(PsaDeclaration("true", "true")),
    pspDeclaration = Some(PspDeclaration("true", "true", "TODO"))
  )

  val testReportSubmissionRequestBody = IhtpPaymentNoticeSubmission(
    ReportDetails(
      pstr = "S2400000001",
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

  val testReportSubmissionRequestBodyOrganisation = IhtpPaymentNoticeSubmission(
    ReportDetails(
      pstr = "S2400000001",
      ihtPaymentReference = "A123459/25A"
    ),
    deceased,
    prDetailsOrganisation,
    IhTaxInformation(
      ihTaxChangeFlag = None,
      dateNoticeReceived = testPaymentNoticeDate,
      noticeSubmittedByPR = Yes,
      knownBeneficiaries = Some(No),
      totalIHTPayable = Some("1000.00"),
      totalInterestPayable = Some("50.00"),
      total = Some("1050.00")
    ),
    Some(
      Seq(
        BeneficiaryDetails(
          beneficiaryType = IorTIndividual,
          beneficiaryContactDetails = beneficiaryContactDetails,
          beneficiaryPaymentDetails = beneficiaryPaymentDetails
        )
      )
    ),
    declarations = declarations
  )

  val testReportSubmissionResponse = IhtpPaymentNoticeResponse("910000000000", "123456789")

  val testUserAnswerJson: JsObject = Json.obj(
    "inheritanceTaxReference" -> "A123459/25A",
    "nameOfDeceased" -> Json.obj(
      "title" -> "Mr",
      "firstForename" -> "John",
      "secondForename" -> "William",
      "surname" -> "Doe"
    ),
    "birthDeathDates" -> Json.obj(
      "dateOfBirth" -> "1950-01-01",
      "dateOfDeath" -> "2026-01-01"
    ),
    "prType" -> "individual",
    "prDetails" -> Json.obj(
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
    "hasNino" -> true,
    "nino" -> "AB123456C",
    "ihtTaxInformation" -> Json.obj(
      "dateThePensionSchemeReceivedNoticeToPay" -> "2026-03-27"
    ),
    "didPrSubmit" -> true,
    "ihtPaymentReference" -> "A123456/25A629671",
    "formBundleNo" -> "000012345678",
    "processingDateTime" -> "2026-08-12T16:26:37"
  )

  val testOverviewResponse: JsArray = Json.arr(
    Json.obj(
      "fbNumber" -> "100000000000",
      "submissionDate" -> "2026-04-10T16:12:49Z",
      "paymentDueDate" -> "2026-10-10",
      "ihtpVersion" -> "001",
      "inheritanceTaxReference" -> "A123456/25A",
      "paymentReference" -> "A123456/25A629671",
      "title" -> "Dr",
      "firstForename" -> "John",
      "secondForename" -> "E",
      "surname" -> "Doe",
      "ihtpStatus" -> "Paid"
    ),
    Json.obj(
      "fbNumber" -> "100000000000",
      "submissionDate" -> "2026-04-10T16:12:49Z",
      "paymentDueDate" -> "2026-10-10",
      "ihtpVersion" -> "002",
      "inheritanceTaxReference" -> "A123456/25A",
      "paymentReference" -> "A123456/25A629671",
      "title" -> "Dr",
      "firstForename" -> "John",
      "secondForename" -> "E",
      "surname" -> "Doe",
      "ihtpStatus" -> "Not reconciled"
    ),
    Json.obj(
      "fbNumber" -> "200000000000",
      "submissionDate" -> "2026-04-10T16:12:49Z",
      "paymentDueDate" -> "2026-10-10",
      "ihtpVersion" -> "001",
      "inheritanceTaxReference" -> "A223456/25A",
      "paymentReference" -> "A223456/25A629671",
      "title" -> "Ms",
      "firstForename" -> "Jane",
      "secondForename" -> "E",
      "surname" -> "Doe",
      "ihtpStatus" -> "Not reconciled"
    )
  )
}
