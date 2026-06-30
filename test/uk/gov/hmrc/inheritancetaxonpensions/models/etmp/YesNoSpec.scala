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

class YesNoSpec extends AnyWordSpec with Matchers {

  "YesNo" should {

    "successfully convert from YesNo to Json" in {
      Json.toJson(YesNo.Yes)(implicitly[Writes[YesNo]]) shouldEqual JsString("Yes")
      Json.toJson(YesNo.No)(implicitly[Writes[YesNo]]) shouldEqual JsString("No")
    }

    "successfully convert from Json to YesNo" in {
      JsString("Yes").validate[YesNo] shouldEqual JsSuccess(YesNo.Yes)
      JsString("No").validate[YesNo] shouldEqual JsSuccess(YesNo.No)
      JsString("INVALID").validate[YesNo] shouldEqual JsError("Unknown value for YesNo: \"INVALID\"")
    }
  }
}
