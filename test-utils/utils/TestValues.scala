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

import generators.Generators
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants.psaEnrolmentKey
import uk.gov.hmrc.inheritancetaxonpensions.models._

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
  val psrVersion = "001"
  val psrFormBundleNumber = "1234567890"
  val schemeName = "SchemeName"
  val userName = "userName"
  val psaPspId = "psaPspId"
  val credentialRole = "credentialRole"
  val cipPsrStatus = None
  val sampleToday: LocalDate = LocalDate.of(2023, 10, 19)
  val psaId = "A0000000"
  val pspId = "21000005"
  val testNino: String = ninoGen.sample.get
  val testDateOfBirth = "1950-01-01"
  val testDateOfDeath = "2026-01-01"
  val testAddressLine1 = "1 ABCDE Street"
  val testAddressLine2 = "FGHIJ Town"
  val testUkPostcode = "ZZ99 1AA"
  val testCountry = "GB"

  val testReportSubmissionRequestBody = IhtpReportSubmission(
    ReportDetails(
      pstr = "S2400000001"
    ),
    DeceasedDetails(
      inheritanceTaxReference = "A123456/25A",
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
            addressLine1 = testAddressLine1,
            addressLine2 = testAddressLine2,
            ukPostcode = Some(testUkPostcode),
            country = testCountry
          )
        )
      ),
      organisation = None
    )
  )

  val testReportSubmissionResponse = IhtpReportSubmissionResponse(Instant.now(clock), "910000000000", "123456789")
}
