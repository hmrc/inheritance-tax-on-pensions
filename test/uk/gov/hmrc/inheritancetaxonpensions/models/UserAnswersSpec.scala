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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsValue, Json}

import java.time.{Clock, Instant, ZoneId}

class UserAnswersSpec extends AnyFreeSpec with Matchers {
  val clockMillis: Long = 1718118467838L
  val clock: Clock = Clock.fixed(Instant.ofEpochMilli(clockMillis), ZoneId.of("UTC"))
  private val instant = Instant.now(clock)

  val userAnswers: UserAnswers = UserAnswers(
    id = "id",
    data = Json.obj("someDataItem" -> "someDataItemValue"),
    lastUpdated = instant
  )

  "UserAnswers" - {

    "read successfully" in {
      val fromJson = Json.parse(
        s"""
             {
           |  "_id": "id",
           |  "data": {
           |    "someDataItem": "someDataItemValue"
           |  },
           |  "lastUpdated": {
           |    "$$date": {
           |      "$$numberLong": "1718118467838"
           |    }
           |  }
           |}
           |""".stripMargin
      )

      val result = Json.fromJson[UserAnswers](fromJson)
      result.map(res => res mustBe userAnswers)
    }

    "write successfully" in {
      val expectedJson = Json.parse(
        s"""
         {
           |  "_id": "id",
           |  "data": {
           |    "someDataItem": "someDataItemValue"
           |  },
           |  "lastUpdated": {
           |    "$$date": {
           |      "$$numberLong": "1718118467838"
           |    }
           |  }
           |}
          |""".stripMargin
      )

      val result: JsValue = Json.toJson(userAnswers)
      result mustBe expectedJson
    }
  }
}
