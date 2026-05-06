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

import play.api.libs.json.{JsString, JsValue}
import uk.gov.hmrc.inheritancetaxonpensions.models.PiiFields

import scala.util.{Failure, Success, Try}

import java.nio.ByteBuffer
import javax.crypto.spec.{GCMParameterSpec, SecretKeySpec}
import java.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.{Cipher, SecretKey}
import java.security.{MessageDigest, SecureRandom}

class EncryptionService(encryptionKey: String, encryptionEnabled: Boolean, random: SecureRandom = new SecureRandom()) {

  protected val enabled: Boolean = encryptionEnabled

  private val AES_ALGO = "AES/GCM/NoPadding"
  private val GCM_TAG_BITS = 128
  private val IV_LENGTH_BYTES = 12

  private val key: SecretKey = deriveKey(encryptionKey)

  private def deriveKey(secret: String): SecretKey = {
    val decoded = tryBase64DecodeOrPlain(secret)
    val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
    new SecretKeySpec(digest, "AES")
  }

  private def tryBase64DecodeOrPlain(value: String): Array[Byte] =
    Try(Base64.getDecoder.decode(value)) match {
      case Success(bytes) => bytes
      case Failure(_) => value.getBytes(StandardCharsets.UTF_8)
    }

  private def encrypt(plainText: String): String = {
    if (!enabled) return plainText

    require(plainText != null, "Cannot encrypt null string")

    val iv = new Array[Byte](IV_LENGTH_BYTES)
    random.nextBytes(iv)

    val cipher = Cipher.getInstance(AES_ALGO)
    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv))

    val cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8))
    val payload = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array()

    Base64.getEncoder.encodeToString(payload)
  }

  private def decrypt(encoded: String): Either[Throwable, String] = {
    if (!enabled) return Right(encoded)

    Try {
      val payload = Base64.getDecoder.decode(encoded)
      val buf = ByteBuffer.wrap(payload)

      val iv = new Array[Byte](IV_LENGTH_BYTES)
      buf.get(iv)

      val cipherBytes = new Array[Byte](payload.length - IV_LENGTH_BYTES)
      buf.get(cipherBytes)

      val cipher = Cipher.getInstance(AES_ALGO)
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv))

      new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }.toEither
  }

  def encryptField(fieldName: String, value: JsValue): JsValue = {
    if (!enabled || !PiiFields.isPiiField(fieldName)) return value
    value match {
      case JsString(str) => JsString(encrypt(str))
      case _ => value
    }
  }

  def decryptField(fieldName: String, value: JsValue): JsValue = {
    if (!enabled || !PiiFields.isPiiField(fieldName)) return value
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
