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

sealed trait IndividualOrOrg {
  def value: String
}

object IndividualOrOrg {
  case object Individual extends IndividualOrOrg { val value = "01" }
  case object Organisation extends IndividualOrOrg { val value = "02" }

  def apply(string: String): IndividualOrOrg = if (string.toLowerCase() == "individual") Individual else Organisation

  def unapply(individualOrOrg: IndividualOrOrg): String = individualOrOrg.value

  implicit val writes: Writes[IndividualOrOrg] = individualOrOrg => JsString(individualOrOrg.value)
  implicit val reads: Reads[IndividualOrOrg] = Reads {
    case JsString(Individual.value) => JsSuccess(Individual)
    case JsString(Organisation.value) => JsSuccess(Organisation)
    case unknown => JsError(s"Unknown value for IndividualOrOrg: $unknown")
  }
}
