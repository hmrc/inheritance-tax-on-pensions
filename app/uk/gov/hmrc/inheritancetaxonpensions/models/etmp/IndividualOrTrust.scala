/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.inheritancetaxonpensions.models.etmp

import play.api.libs.json._

sealed trait IndividualOrTrust {
  def value: String
}

object IndividualOrTrust {
  case object Individual extends IndividualOrTrust { val value = "01" }
  case object Trust extends IndividualOrTrust { val value = "02" }

  def apply(string: String): IndividualOrTrust = if (string.toLowerCase() == "individual") Individual else Trust

  def unapply(IndividualOrTrust: IndividualOrTrust): String = IndividualOrTrust.value

  implicit val writes: Writes[IndividualOrTrust] = IndividualOrTrust => JsString(IndividualOrTrust.value)
  implicit val reads: Reads[IndividualOrTrust] = Reads {
    case JsString(Individual.value) => JsSuccess(Individual)
    case JsString(Trust.value) => JsSuccess(Trust)
    case unknown => JsError(s"Unknown value for IndividualOrTrust: $unknown")
  }
}
