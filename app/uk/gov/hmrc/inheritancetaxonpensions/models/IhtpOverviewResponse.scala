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

import play.api.libs.functional.syntax._
import play.api.Logging
import play.api.libs.json._

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
          head.ihtPaymentReference match {
            case Some(ihtPaymentReference) =>
              val allVersions = curr.filter(item => item.ihtPaymentReference.contains(ihtPaymentReference))
              val sorted = allVersions.sortWith(_.ihtVersion.toInt > _.ihtVersion.toInt)
              val removeDuplicates = tail.filterNot(item => item.ihtPaymentReference.contains(ihtPaymentReference))

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
  ihtPaymentReference: Option[String],
  firstForename: Option[String],
  surname: Option[String],
  nino: Option[String],
  ihtStatus: String,
  allocatedDate: Option[LocalDate]
) {

  val deceasedName: String =
    Seq(firstForename, surname).flatten.filter(_.nonEmpty).mkString(" ")
}

object IhtpOverviewReport extends Logging {

  implicit val reads: Reads[IhtpOverviewReport] =
    (JsPath \ "uuid")
      .readNullable[String]
      .and((JsPath \ "fbNumber").readNullable[String])
      .and((JsPath \ "submissionDate").readNullable[Instant])
      .and((JsPath \ "paymentDueDate").readNullable[LocalDate])
      .and((JsPath \ "ihtVersion").read[String])
      .and((JsPath \ "ihtPaymentReference").readNullable[String])
      .and((JsPath \ "firstForename").readNullable[String])
      .and((JsPath \ "surname").readNullable[String])
      .and((JsPath \ "nino").readNullable[String])
      .and((JsPath \ "ihtStatus").read[String])
      .and((JsPath \ "allocatedDate").readNullable[LocalDate])(
        (
          uuid,
          fbNumber,
          submissionDate,
          paymentDueDate,
          ihtVersion,
          ihtPaymentReference,
          firstForename,
          surname,
          nino,
          ihtStatus,
          allocatedDate
        ) =>
          IhtpOverviewReport(
            uuid,
            fbNumber,
            submissionDate,
            paymentDueDate,
            ihtVersion,
            getInheritanceTaxReference(ihtPaymentReference),
            ihtPaymentReference,
            firstForename,
            surname,
            nino,
            ihtStatus,
            allocatedDate
          )
      )

  private def getInheritanceTaxReference(
    ihtPaymentReference: Option[String]
  ): String =
    ihtPaymentReference match {
      case Some(reference) if reference.length >= 11 =>
        reference.take(11)

      case Some(reference) =>
        logger.warn(
          s"[IhtpOverviewReport] ihtPaymentReference is shorter than 11 characters, ihtPaymentReference: ${reference}"
        )
        reference

      case None =>
        ""
    }

  implicit val writes: OWrites[IhtpOverviewReport] = Json.writes[IhtpOverviewReport]

  implicit val formats: OFormat[IhtpOverviewReport] = OFormat(reads, writes)

}
