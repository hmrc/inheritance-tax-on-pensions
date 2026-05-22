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

package uk.gov.hmrc.inheritancetaxonpensions.utils

import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec

class UserAnswersHelperSpec extends AnyFreeSpec with Matchers with TestValues {

  val userAnswers: UserAnswers = UserAnswers(
    id = s"$srn-$uuid",
    srn = srn,
    uuid = uuid,
    data = Json.obj(
      "mandatoryField" -> "mandatoryValue",
      "optionalField" -> "optionalValue"
    )
  )

  val userAnswersWithoutFields: UserAnswers = UserAnswers(
    id = s"$srn-$uuid",
    srn = srn,
    uuid = uuid,
    data = Json.obj()
  )

  "UserAnswersHelper" - {
    "getMandatory" - {
      "return the mandatory field value" in {
        val result = UserAnswersHelper.getMandatory(userAnswers, "mandatoryField")
        result mustBe "mandatoryValue"
      }

      "Throw an IllegalArgumentException when the mandatory field is not present" - {
        an[IllegalArgumentException] must be thrownBy UserAnswersHelper.getMandatory(
          userAnswersWithoutFields,
          "mandatoryField"
        )
      }
    }

    "getOptional" - {
      "return the optional field value" in {
        val result = UserAnswersHelper.getOptional(userAnswers, "optionalField")
        result mustBe Some("optionalValue")
      }

      "return None when the optional field is not present" in {
        val result = UserAnswersHelper.getOptional(userAnswersWithoutFields, "optionalField")
        result mustBe None
      }
    }
  }
}
