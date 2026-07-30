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

import uk.gov.hmrc.inheritancetaxonpensions.config.Crypto
import uk.gov.hmrc.crypto.{Crypted, PlainText}
import play.api.libs.json.{JsString, JsValue}
import uk.gov.hmrc.inheritancetaxonpensions.models.PiiFields

import scala.util.Try

import javax.inject.{Inject, Singleton}

@Singleton
class EncryptionService @Inject() (crypto: Crypto) {
  private def encrypt(plainText: String): String =
    crypto.getCrypto.encrypt(PlainText(plainText)).value

  private def decrypt(encoded: String): Either[Throwable, String] =
    Try {
      crypto.getCrypto.decrypt(Crypted(encoded)).value
    }.toEither

  def encryptField(fieldName: String, value: JsValue): JsValue = {
    if (!PiiFields.isPiiField(fieldName)) return value
    value match {
      case JsString(str) => JsString(encrypt(str))
      case _ => value
    }
  }

  def decryptField(fieldName: String, value: JsValue): JsValue = {
    if (!PiiFields.isPiiField(fieldName)) return value
    value match {
      case JsString(encrypted) =>
        decrypt(encrypted) match {
          case Right(decrypted) => JsString(decrypted)
          case Left(_) => value
        }
      case _ => value
    }
  }
}
