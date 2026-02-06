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

import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import play.api.libs.functional.syntax.toFunctionalBuilderOps

import java.time.Instant

final case class UserAnswers(
  id: String,
  data: JsObject = Json.obj(),
  lastUpdated: Instant = Instant.now
)

object UserAnswers {

  val reads: Reads[UserAnswers] =
    (__ \ "_id")
      .read[String]
      .and((__ \ "data").read[JsObject])
      .and((__ \ "lastUpdated").read(using MongoJavatimeFormats.instantFormat))(UserAnswers.apply)

  val writes: OWrites[UserAnswers] =
    (__ \ "_id")
      .write[String]
      .and((__ \ "data").write[JsObject])
      .and((__ \ "lastUpdated").write(using MongoJavatimeFormats.instantFormat))(ua => (ua.id, ua.data, ua.lastUpdated))

  implicit val format: OFormat[UserAnswers] = OFormat(reads, writes)
}
