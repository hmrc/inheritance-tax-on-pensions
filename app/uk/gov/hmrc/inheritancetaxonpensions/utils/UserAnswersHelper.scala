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

import play.api.libs.json._
import models.RichJsObject
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers

import scala.util.{Failure, Success, Try}

object UserAnswersHelper {

  def getMandatory(userAnswers: UserAnswers, path: String): String = {
    val parts = path.split("\\.")
    val result = parts.foldLeft(userAnswers.data: play.api.libs.json.JsValue) { (json, part) =>
      (json \ part).getOrElse(
        throw new IllegalArgumentException(s"A mandatory field: '$path' was not found in user answers")
      )
    }
    result
      .asOpt[String]
      .getOrElse(
        throw new IllegalArgumentException(s"A mandatory field: '$path' was not found in user answers")
      )
  }

  def getOptional(userAnswers: UserAnswers, path: String): Option[String] = {
    val parts = path.split("\\.")
    val result = parts.foldLeft(Option(userAnswers.data: play.api.libs.json.JsValue)) { (optJson, part) =>
      optJson.flatMap(json => (json \ part).asOpt[play.api.libs.json.JsValue])
    }
    result.flatMap(_.asOpt[String])
  }

  def getOptionalAs[A: Reads](userAnswers: UserAnswers, path: String): Option[A] = {
    val parts = path.split("\\.")
    val result = parts.foldLeft(Option(userAnswers.data: play.api.libs.json.JsValue)) { (optJson, part) =>
      optJson.flatMap(json => (json \ part).asOpt[play.api.libs.json.JsValue])
    }
    result.flatMap(_.asOpt[A])
  }

  def getMandatoryAs[A: Reads](userAnswers: UserAnswers, path: String): A =
    path
      .split("\\.")
      .foldLeft(userAnswers.data: play.api.libs.json.JsValue) { (json, part) =>
        (json \ part).getOrElse(
          throw new IllegalArgumentException(s"A mandatory field: '$path' was not found in user answers")
        )
      }
      .asOpt[A]
      .getOrElse(
        throw new IllegalArgumentException(s"A mandatory field: '$path' was not found in user answers")
      )

  def set[A](userAnswers: UserAnswers, path: JsPath, value: A)(implicit writes: Writes[A]): Try[UserAnswers] =
    userAnswers.data.setObject(path, Json.toJson(value)) match {
      case JsSuccess(jsValue, _) =>
        Success(userAnswers.copy(data = jsValue))
      case JsError(errors) =>
        Failure(JsResultException(errors))
    }
}
