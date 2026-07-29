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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.inheritancetaxonpensions.config.{Crypto, FakeCrypto}
import play.api.libs.json.JsString

class EncryptionServiceSpec extends AnyFreeSpec with Matchers {
  "EncryptionService" - {

    "when encryption is disabled" - {
      val crypto = Crypto.noop

      "must return original value for encryptField" in {
        val service = new EncryptionService(crypto)
        val result = service.encryptField("ninoOrReason.nino", JsString("AB123456C"))
        result mustBe JsString("AB123456C")
      }

      "must return original value for decryptField" in {
        val service = new EncryptionService(crypto)
        val result = service.decryptField("ninoOrReason.nino", JsString("AB123456C"))
        result mustBe JsString("AB123456C")
      }

      "must return original value for nonPii field when decrypting" in {
        val service = new EncryptionService(crypto)
        val result = service.decryptField("someOtherField", JsString("AB123456C"))
        result mustBe JsString("AB123456C")
      }
    }

    "when encryption is enabled" - {
      val crypto = FakeCrypto

      "must encrypt and decrypt PII fields correctly" in {
        val service = new EncryptionService(crypto)

        val original = JsString("Joe Bloggs")

        val encrypted = service.encryptField("nameOfDeceased.firstForename", original)
        encrypted must not be original

        val decrypted = service.decryptField("nameOfDeceased.firstForename", encrypted)
        decrypted mustBe original
      }

      "must not encrypt non-PII fields" in {
        val service = new EncryptionService(crypto)
        val original = JsString("some non-PII data")

        val encrypted = service.encryptField("someOtherField", original)
        encrypted mustBe original
      }

      "must handle non-JsString values" in {
        val service = new EncryptionService(crypto)

        val original = play.api.libs.json.Json.obj("key" -> "value")
        val result = service.encryptField("nameOfDeceased.firstForename", original)
        result mustBe original
      }

      "must handle decryption errors gracefully" in {
        val service = new EncryptionService(crypto)

        val corrupted = JsString("invalid-encrypted-data")
        val result = service.decryptField("ninoOrReason.nino", corrupted)
        result mustBe corrupted
      }

      "must test different PII field names" in {
        val service = new EncryptionService(crypto)

        val piiFields = List("nameOfDeceased.surname", "ninoOrReason.nino", "birthDeathDates.dateOfBirth")

        piiFields.foreach { fieldName =>
          val original = JsString("test-data")
          val encrypted = service.encryptField(fieldName, original)
          encrypted must not be original

          val decrypted = service.decryptField(fieldName, encrypted)
          decrypted mustBe original
        }
      }

      "must handle empty strings" in {
        val service = new EncryptionService(crypto)

        val original = JsString("")
        val encrypted = service.encryptField("ninoOrReason.nino", original)
        encrypted must not be original

        val decrypted = service.decryptField("ninoOrReason.nino", encrypted)
        decrypted mustBe original
      }
    }
  }
}
