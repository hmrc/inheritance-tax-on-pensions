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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json._

class IndividualOrOrgSpec extends AnyWordSpec with Matchers {

  "IndividualOrOrg" should {

    "successfully convert from IndividualOrOrg to Json" in {
      Json.toJson(IndividualOrOrg.Individual)(implicitly[Writes[IndividualOrOrg]]) shouldEqual JsString("01")
      Json.toJson(IndividualOrOrg.Organisation)(implicitly[Writes[IndividualOrOrg]]) shouldEqual JsString("02")
    }

    "successfully convert from Json to IndividualOrOrg" in {
      JsString("01").validate[IndividualOrOrg] shouldEqual JsSuccess(IndividualOrOrg.Individual)
      JsString("02").validate[IndividualOrOrg] shouldEqual JsSuccess(IndividualOrOrg.Organisation)
      JsString("INVALID").validate[IndividualOrOrg] shouldEqual JsError(
        "Unknown value for IndividualOrOrg: \"INVALID\""
      )
    }
  }
}
