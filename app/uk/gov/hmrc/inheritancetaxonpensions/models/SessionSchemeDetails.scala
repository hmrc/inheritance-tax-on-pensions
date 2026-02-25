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

import play.api.libs.json.{__, Format, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import play.api.libs.functional.syntax._

import java.time.Instant

case class SessionSchemeDetails(
  id: String,
  isAssociated: Boolean = false,
  lastUpdated: Instant = Instant.now
)

object SessionSchemeDetails {
  implicit val format: OFormat[SessionSchemeDetails] =
    (__ \ "_id")
      .format[String]
      .and((__ \ "isAssociated").format[Boolean])
      .and((__ \ "lastUpdated").format(using MongoJavatimeFormats.instantFormat))(
        SessionSchemeDetails.apply,
        o => Tuple.fromProductTyped(o)
      )
}
