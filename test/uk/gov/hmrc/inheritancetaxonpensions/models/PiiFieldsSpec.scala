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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class PiiFieldsSpec extends AnyFreeSpec with Matchers {

  "PiiFields" - {

    "must return true for all defined PII fields" in {
      val piiFields = List(
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
        "prDetails.organisation.firstForename",
        "prDetails.organisation.secondForename",
        "prDetails.organisation.surname",
        "prDetails.individual.addressline1",
        "prDetails.individual.addressline2",
        "prDetails.individual.addressline3",
        "prDetails.individual.addressline4",
        "prDetails.individual.ukPostcode",
        "prDetails.individual.country",
        "prDetails.organisation.addressline1",
        "prDetails.organisation.addressline2",
        "prDetails.organisation.addressline3",
        "prDetails.organisation.addressline4",
        "prDetails.organisation.ukPostcode",
        "prDetails.organisation.country"
      )

      piiFields.foreach { field =>
        PiiFields.isPiiField(field) mustBe true
      }
    }

    "must return false for non-PII fields" in {
      val nonPiiFields = List(
        "randomField",
        "random.data",
        "randomUserAnswers",
        "userAnswers.id",
        "lastUpdated",
        "data.someField",
        "prDetails.organisation.organisationName"
      )

      nonPiiFields.foreach { field =>
        PiiFields.isPiiField(field) mustBe false
      }
    }

    "must return false for empty string" in {
      PiiFields.isPiiField("") mustBe false
    }

    "must return false for null" in {
      PiiFields.isPiiField(null) mustBe false
    }

    "must be case sensitive" in {
      PiiFields.isPiiField("nameofdeceased.firstforename") mustBe false
      PiiFields.isPiiField("NAMEOFDECEASED.FIRSTFORENAME") mustBe false
      PiiFields.isPiiField("NINO") mustBe false
    }

    "must require exact field match" in {
      PiiFields.isPiiField("nameOfDeceased.firstForename.extra") mustBe false
      PiiFields.isPiiField("extra.nameOfDeceased.firstForename") mustBe false
      PiiFields.isPiiField("firstForename") mustBe false
    }
  }
}
