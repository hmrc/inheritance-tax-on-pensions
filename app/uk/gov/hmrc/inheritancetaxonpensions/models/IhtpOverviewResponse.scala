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

package models

import play.api.libs.json.{Json, OFormat}

import scala.annotation.tailrec

import java.time.{Instant, LocalDate}

case class IhtpOverviewResponse(success: IhtpOverviewSuccess)

object IhtpOverviewResponse {
  implicit val formats: OFormat[IhtpOverviewResponse] = Json.format[IhtpOverviewResponse]
}

case class IhtpOverviewSuccess(ihtpOverview: Seq[IhtpOverviewReport])

object IhtpOverviewSuccess {
  implicit val formats: OFormat[IhtpOverviewSuccess] = Json.format[IhtpOverviewSuccess]

  @tailrec
  def filterForHighestVersion(
    curr: List[IhtpOverviewReport],
    acc: List[IhtpOverviewReport]
  ): IhtpOverviewSuccess =
    curr match {
      case Nil => IhtpOverviewSuccess(acc)
      case head :: tail =>
        if (tail.isEmpty) {
          filterForHighestVersion(tail, acc :+ head)
        } else {
          head.paymentReference match {
            case Some(paymentReference) =>
              val allVersions = curr.filter(item => item.paymentReference.contains(paymentReference))
              val sorted = allVersions.sortWith(_.ihtVersion.toInt > _.ihtVersion.toInt)
              val removeDuplicates = tail.filterNot(item => item.paymentReference.contains(paymentReference))

              filterForHighestVersion(removeDuplicates, acc :+ sorted.head)
            case None =>
              filterForHighestVersion(tail, acc :+ head)
          }
        }
    }
}

case class IhtpOverviewReport(
  uuid: Option[String],
  fbNumber: Option[String],
  submissionDate: Option[Instant],
  paymentDueDate: Option[LocalDate],
  ihtVersion: String,
  inheritanceTaxReference: String,
  paymentReference: Option[String],
  title: Option[String],
  firstForename: Option[String],
  secondForename: Option[String],
  surname: Option[String],
  nino: Option[String],
  ihtpStatus: String
) {

  val deceasedName: String =
    Seq(title, firstForename, secondForename, surname).flatten.filter(_.nonEmpty).mkString(" ")
}

object IhtpOverviewReport {
  implicit val formats: OFormat[IhtpOverviewReport] = Json.format[IhtpOverviewReport]
}
