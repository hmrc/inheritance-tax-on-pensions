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

import play.api.libs.json._

import scala.annotation.tailrec

case class IhtpOverviewErrorResponse(errors: Errors)

object IhtpOverviewErrorResponse {
  implicit val formats: OFormat[IhtpOverviewErrorResponse] = Json.format[IhtpOverviewErrorResponse]
}

case class Errors(processingDate: Option[String], code: Option[String], text: Option[String])

object Errors {
  implicit val formats: OFormat[Errors] = Json.format[Errors]
}
