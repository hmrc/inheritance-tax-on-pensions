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
import uk.gov.hmrc.inheritancetaxonpensions.services.EncryptionService
import play.api.libs.json.{JsValue, Json}

import java.time.{Clock, Instant, ZoneId}

class UserAnswersSpec extends AnyFreeSpec with Matchers {
  val clockMillis: Long = 1718118467838L
  val clock: Clock = Clock.fixed(Instant.ofEpochMilli(clockMillis), ZoneId.of("UTC"))
  private val instant = Instant.now(clock)

  val testSrn = "S2400000001"
  val testUuid = "test-uuid"
  val userAnswers: UserAnswers = UserAnswers(
    id = s"$testSrn-$testUuid",
    srn = testSrn,
    uuid = testUuid,
    data = Json.obj("someDataItem" -> "someDataItemValue"),
    lastUpdated = instant
  )

  "UserAnswers" - {

    "read successfully" in {
      val fromJson = Json.parse(
        s"""
             {
           |  "_id": "$testSrn-$testUuid",
           |  "srn": "$testSrn",
           |  "uuid": "$testUuid",
           |  "data": {
           |    "someDataItem": "someDataItemValue"
           |  },
           |  "lastUpdated": {
           |    "$$date": {
           |      "$$numberLong": "1718118467838"
           |    }
           |  }
           |}
           |""".stripMargin
      )

      val result = Json.fromJson[UserAnswers](fromJson)
      result.map(res => res mustBe userAnswers)
    }

    "write successfully" in {
      val expectedJson = Json.parse(
        s"""
         {
           |  "_id": "$testSrn-$testUuid",
           |  "srn": "$testSrn",
           |  "uuid": "$testUuid",
           |  "data": {
           |    "someDataItem": "someDataItemValue"
           |  },
           |  "lastUpdated": {
           |    "$$date": {
           |      "$$numberLong": "1718118467838"
           |    }
           |  }
           |}
          |""".stripMargin
      )

      val result: JsValue = Json.toJson(userAnswers)
      result mustBe expectedJson
    }

    "UserAnswers encryptedFormat" - {

      "must encrypt PII fields and decrypt back to original" in {
        val service = new EncryptionService("test-key", true)

        val format = UserAnswers.encryptedFormat(service)

        val originalData = Json.obj(
          "nameOfDeceased.firstForename" -> "Joe",
          "ninoOrReason.nino" -> "AB123456C",
          "someOtherField" -> "non-PII data"
        )

        val userAnswers = UserAnswers("S2400000001-test-uuid", "S2400000001", "test-uuid", originalData, Instant.now())
        val json = format.writes(userAnswers)
        val result = format.reads(json)

        result.isSuccess mustBe true

        val firstNameInJson = (json \ "data" \ "nameOfDeceased.firstForename").get.asOpt[String]
        firstNameInJson must not be Some("Joe")

        val nonPiiInJson = (json \ "data" \ "someOtherField").get.asOpt[String]
        nonPiiInJson mustBe Some("non-PII data")

        val decryptedFirstName = (result.get.data \ "nameOfDeceased.firstForename").get.asOpt[String]
        decryptedFirstName mustBe Some("Joe")
      }

      "must not encrypt non-PII fields" in {
        val service = new EncryptionService("test-key", true)

        val format = UserAnswers.encryptedFormat(service)

        val originalData = Json.obj(
          "someOtherField" -> "non-PII data",
          "anotherField" -> "also non-PII"
        )

        val userAnswers = UserAnswers("S2400000001-test-uuid", "S2400000001", "test-uuid", originalData, Instant.now())
        val json = format.writes(userAnswers)

        (json \ "data" \ "someOtherField").get.asOpt[String] mustBe Some("non-PII data")
        (json \ "data" \ "anotherField").get.asOpt[String] mustBe Some("also non-PII")
      }

      "must handle nested objects correctly" in {
        val service = new EncryptionService("test-key", true)

        val format = UserAnswers.encryptedFormat(service)

        val originalData = Json.obj(
          "nameOfDeceased" -> Json.obj(
            "firstForename" -> "Joe",
            "surname" -> "Bloggs"
          ),
          "nonPiiSection" -> Json.obj(
            "someField" -> "data"
          )
        )

        val userAnswers = UserAnswers("S2400000001-test-uuid", "S2400000001", "test-uuid", originalData, Instant.now())
        val json = format.writes(userAnswers)
        val result = format.reads(json).get

        val encryptedName = (json \ "data" \ "nameOfDeceased" \ "firstForename").get.asOpt[String]
        encryptedName must not be Some("Joe")

        val nonPiiField = (json \ "data" \ "nonPiiSection" \ "someField").get.asOpt[String]
        nonPiiField mustBe Some("data")
        val decryptedName = (result.data \ "nameOfDeceased" \ "firstForename").get.asOpt[String]
        decryptedName mustBe Some("Joe")
      }

      "must work with encryption disabled" in {
        val service = new EncryptionService("test-key", false)
        val format = UserAnswers.encryptedFormat(service)

        val originalData = Json.obj(
          "ninoOrReason.nino" -> "AB123456C"
        )

        val userAnswers = UserAnswers("S2400000001-test-uuid", "S2400000001", "test-uuid", originalData, Instant.now())
        val json = format.writes(userAnswers)
        val result = format.reads(json).get

        result.data mustBe originalData
      }
    }
  }
}
