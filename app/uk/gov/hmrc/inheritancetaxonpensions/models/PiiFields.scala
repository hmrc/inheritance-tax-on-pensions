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

package uk.gov.hmrc.inheritancetaxonpensions.models

object PiiFields {
  // Add further fields both here and in the spec as we progress the journey
  private val piiFields: Set[String] = Set(
    "nameOfDeceased.firstForename",
    "nameOfDeceased.secondForename",
    "nameOfDeceased.surname",
    "nameOfDeceased.dateOfBirth",
    "nameOfDeceased.dateOfDeath",
    "nino",
    "birthDeathDates.dateOfBirth",
    "birthDeathDates.dateOfDeath",
    "prDetails.individual.firstForename",
    "prDetails.individual.secondForename",
    "prDetails.individual.surname",
    "prDetails.organisation.organisationName",
    "prDetails.organisation.firstForename",
    "prDetails.organisation.secondForename",
    "prDetails.organisation.surname",
    "prDetails.individual.addressLine1",
    "prDetails.individual.addressLine2",
    "prDetails.individual.addressLine3",
    "prDetails.individual.addressLine4",
    "prDetails.individual.postCode",
    "prDetails.individual.country",
    "prDetails.organisation.addressLine1",
    "prDetails.organisation.addressLine2",
    "prDetails.organisation.addressLine3",
    "prDetails.organisation.addressLine4",
    "prDetails.organisation.postCode",
    "prDetails.organisation.country",
    "beneficiaries.beneficiaryDetails.individual.firstForename",
    "beneficiaries.beneficiaryDetails.individual.secondForename",
    "beneficiaries.beneficiaryDetails.individual.surname",
    "beneficiaries.beneficiaryDetails.trust.beneficiaryTrustName",
    "beneficiaries.beneficiaryDetails.trust.firstForename",
    "beneficiaries.beneficiaryDetails.trust.secondForename",
    "beneficiaries.beneficiaryDetails.trust.surname",
    "beneficiaries.nino",
    "beneficiaries.beneficiaryAddress.addressLine1",
    "beneficiaries.beneficiaryAddress.addressLine2",
    "beneficiaries.beneficiaryAddress.addressLine3",
    "beneficiaries.beneficiaryAddress.addressLine4",
    "beneficiaries.beneficiaryAddress.addressLine5",
    "beneficiaries.beneficiaryAddress.postCode",
    "beneficiaries.beneficiaryAddress.country"
  )

  def isPiiField(fieldName: String): Boolean = piiFields.contains(fieldName)

}
