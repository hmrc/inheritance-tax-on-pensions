/*
 * Copyright 2025 HM Revenue & Customs
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

import uk.gov.hmrc.inheritancetaxonpensions.services.EncryptionService
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import play.api.libs.functional.syntax.toFunctionalBuilderOps

import java.time.Instant

final case class UserAnswers(
  id: String,
  srn: String,
  uuid: String,
  data: JsObject = Json.obj(),
  lastUpdated: Instant = Instant.now
)

object UserAnswers {

  private val reads: Reads[UserAnswers] =
    (__ \ "_id")
      .read[String]
      .and((__ \ "srn").read[String])
      .and((__ \ "uuid").read[String])
      .and((__ \ "data").read[JsObject])
      .and((__ \ "lastUpdated").read(using MongoJavatimeFormats.instantFormat))(UserAnswers.apply)

  private val writes: OWrites[UserAnswers] =
    (__ \ "_id")
      .write[String]
      .and((__ \ "srn").write[String])
      .and((__ \ "uuid").write[String])
      .and((__ \ "data").write[JsObject])
      .and((__ \ "lastUpdated").write(using MongoJavatimeFormats.instantFormat))(ua =>
        (ua.id, ua.srn, ua.uuid, ua.data, ua.lastUpdated)
      )

  implicit val format: OFormat[UserAnswers] = OFormat(reads, writes)

  def encryptedFormat(encryptionService: EncryptionService): OFormat[UserAnswers] = {
    def encryptObject(obj: JsObject, path: String = ""): JsObject =
      JsObject(obj.fields.map { case (key, value) =>
        val fullPath = if (path.isEmpty) key else s"$path.$key"
        value match {
          case JsString(str) if PiiFields.isPiiField(fullPath) =>
            key -> encryptionService.encryptField(fullPath, JsString(str))
          case JsObject(nested) =>
            key -> encryptObject(JsObject(nested), fullPath)
          case _ =>
            key -> value
        }
      })

    def decryptObject(obj: JsObject, path: String = ""): JsObject =
      JsObject(obj.fields.map { case (key, value) =>
        val fullPath = if (path.isEmpty) key else s"$path.$key"
        value match {
          case JsString(str) if PiiFields.isPiiField(fullPath) =>
            key -> encryptionService.decryptField(fullPath, JsString(str))
          case JsObject(nested) =>
            key -> decryptObject(JsObject(nested), fullPath)
          case _ =>
            key -> value
        }
      })

    val reads: Reads[UserAnswers] =
      (__ \ "_id")
        .read[String]
        .and((__ \ "srn").read[String])
        .and((__ \ "uuid").read[String])
        .and((__ \ "data").read[JsObject].map(decryptObject(_)))
        .and((__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat))(UserAnswers.apply)

    val writes: OWrites[UserAnswers] = OWrites { ua =>
      Json.obj(
        "_id" -> ua.id,
        "srn" -> ua.srn,
        "uuid" -> ua.uuid,
        "data" -> encryptObject(ua.data),
        "lastUpdated" -> MongoJavatimeFormats.instantFormat.writes(ua.lastUpdated)
      )
    }

    OFormat(reads, writes)
  }
}
