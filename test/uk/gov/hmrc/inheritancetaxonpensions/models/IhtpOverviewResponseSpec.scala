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

import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsError, JsSuccess, Json}
import models.{IhtpOverviewReport, IhtpOverviewResponse, IhtpOverviewSuccess}
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec

import java.time.{Instant, LocalDate}

class IhtpOverviewResponseSpec extends AnyFreeSpec with Matchers with TestValues {

  private val submissionDate = Instant.parse("2026-04-10T16:12:49Z")
  private val paymentDueDate = LocalDate.of(2026, 2, 2)

  private val report = IhtpOverviewReport(
    uuid = None,
    fbNumber = Some("119000004320"),
    submissionDate = Some(submissionDate),
    paymentDueDate = Some(paymentDueDate),
    ihtpVersion = "001",
    inheritanceTaxReference = "A123456/25A",
    paymentReference = Some("A123456/25A629671"),
    title = Some("Dr"),
    firstForename = Some("John"),
    secondForename = Some("M"),
    surname = Some("Doe"),
    nino = Some("AB123456C"),
    ihtpStatus = "Not reconciled"
  )

  private val success = IhtpOverviewSuccess(
    ihtpOverview = Seq(report)
  )

  private val response = IhtpOverviewResponse(success)

  "IhtpOverviewResponse" - {

    "must successfully read from json" in {
      val json = Json.obj(
        "success" -> Json.obj(
          "ihtpOverview" -> Json.arr(
            Json.obj(
              "fbNumber" -> "119000004320",
              "submissionDate" -> "2026-04-10T16:12:49Z",
              "paymentDueDate" -> "2026-02-02",
              "ihtpVersion" -> "001",
              "inheritanceTaxReference" -> "A123456/25A",
              "paymentReference" -> "A123456/25A629671",
              "title" -> "Dr",
              "firstForename" -> "John",
              "secondForename" -> "M",
              "surname" -> "Doe",
              "nino" -> "AB123456C",
              "ihtpStatus" -> "Not reconciled"
            )
          )
        )
      )

      json.validate[IhtpOverviewResponse] mustBe JsSuccess(response)
    }

    "must successfully write to json" in {
      val json = Json.toJson(response)

      (json \ "success" \ "ihtpOverview" \ 0 \ "fbNumber").as[String] mustBe "119000004320"
      (json \ "success" \ "ihtpOverview" \ 0 \ "submissionDate").as[String] mustBe "2026-04-10T16:12:49Z"
      (json \ "success" \ "ihtpOverview" \ 0 \ "paymentDueDate").as[String] mustBe "2026-02-02"
      (json \ "success" \ "ihtpOverview" \ 0 \ "ihtpVersion").as[String] mustBe "001"
      (json \ "success" \ "ihtpOverview" \ 0 \ "inheritanceTaxReference").as[String] mustBe "A123456/25A"
      (json \ "success" \ "ihtpOverview" \ 0 \ "paymentReference").as[String] mustBe "A123456/25A629671"
      (json \ "success" \ "ihtpOverview" \ 0 \ "title").as[String] mustBe "Dr"
      (json \ "success" \ "ihtpOverview" \ 0 \ "firstForename").as[String] mustBe "John"
      (json \ "success" \ "ihtpOverview" \ 0 \ "secondForename").as[String] mustBe "M"
      (json \ "success" \ "ihtpOverview" \ 0 \ "surname").as[String] mustBe "Doe"
      (json \ "success" \ "ihtpOverview" \ 0 \ "nino").as[String] mustBe "AB123456C"
      (json \ "success" \ "ihtpOverview" \ 0 \ "ihtpStatus").as[String] mustBe "Not reconciled"
    }

    "must fail to read when success is missing" in {
      Json.obj().validate[IhtpOverviewResponse] mustBe a[JsError]
    }

    "must expose its case class values" in {
      response.success mustBe success
      response.copy(success = success) mustBe response
      response.productElement(0) mustBe success
      response.productArity mustBe 1
      response.productPrefix mustBe "IhtpOverviewResponse"
      response.toString must include("IhtpOverviewResponse")
    }
  }

  "IhtpOverviewSuccess" - {

    "must successfully read from json" in {
      val json = Json.obj(
        "ihtpOverview" -> Json.arr(Json.toJson(report))
      )

      json.validate[IhtpOverviewSuccess] mustBe JsSuccess(success)
    }

    "must successfully write to json" in {
      val json = Json.toJson(success)

      (json \ "ihtpOverview").as[Seq[IhtpOverviewReport]] mustBe Seq(report)
    }

    "must read and write an empty overview list" in {
      val success = IhtpOverviewSuccess(Seq.empty)
      val json = Json.toJson(success)

      json.validate[IhtpOverviewSuccess] mustBe JsSuccess(success)
      (json \ "ihtpOverview").as[Seq[IhtpOverviewReport]] mustBe Seq.empty
    }

    "must fail to read when required fields are missing" in {
      Json.obj().validate[IhtpOverviewSuccess] mustBe a[JsError]
    }

    "must expose its case class values" in {
      success.ihtpOverview mustBe Seq(report)
      success.copy(ihtpOverview = Seq(report)) mustBe success
      success.productElement(0) mustBe Seq(report)
      success.productArity mustBe 1
      success.productPrefix mustBe "IhtpOverviewSuccess"
      success.toString must include("IhtpOverviewSuccess")
    }
  }

  "IhtpOverviewReport" - {

    "must successfully read from json" in {
      val json = Json.obj(
        "fbNumber" -> "119000004320",
        "submissionDate" -> "2026-04-10T16:12:49Z",
        "paymentDueDate" -> "2026-02-02",
        "ihtpVersion" -> "001",
        "inheritanceTaxReference" -> "A123456/25A",
        "paymentReference" -> "A123456/25A629671",
        "title" -> "Dr",
        "firstForename" -> "John",
        "secondForename" -> "M",
        "surname" -> "Doe",
        "nino" -> "AB123456C",
        "ihtpStatus" -> "Not reconciled"
      )

      json.validate[IhtpOverviewReport] mustBe JsSuccess(report)
    }

    "must successfully write to json" in {
      val json = Json.toJson(report)

      json.validate[IhtpOverviewReport] mustBe JsSuccess(report)
    }

    "must handle missing optional fields when reading from json" in {
      val json = Json.obj(
        "ihtpVersion" -> "000",
        "inheritanceTaxReference" -> "F654321/25B",
        "ihtpStatus" -> "In progress"
      )

      json.validate[IhtpOverviewReport] mustBe JsSuccess(
        IhtpOverviewReport(
          uuid = None,
          fbNumber = None,
          submissionDate = None,
          paymentDueDate = None,
          ihtpVersion = "000",
          inheritanceTaxReference = "F654321/25B",
          paymentReference = None,
          title = None,
          firstForename = None,
          secondForename = None,
          surname = None,
          nino = None,
          ihtpStatus = "In progress"
        )
      )
    }

    "must write optional fields as null when they are not present" in {
      val report = this.report.copy(
        paymentReference = None,
        title = None,
        secondForename = None,
        nino = None
      )

      val json = Json.toJson(report)

      (json \ "paymentReference").asOpt[String] mustBe None
      (json \ "title").asOpt[String] mustBe None
      (json \ "secondForename").asOpt[String] mustBe None
      (json \ "nino").asOpt[String] mustBe None
    }

    "must fail to read when required fields are missing" in {
      Json
        .obj(
          "submissionDate" -> "2026-04-10T16:12:49Z",
          "paymentDueDate" -> "2026-02-02",
          "inheritanceTaxReference" -> "A123456/25A",
          "firstForename" -> "John",
          "surname" -> "Doe",
          "ihtpStatus" -> "Not reconciled"
        )
        .validate[IhtpOverviewReport] mustBe a[JsError]
    }

    "must fail to read when date fields have the wrong type" in {
      Json
        .obj(
          "fbNumber" -> "119000004320",
          "submissionDate" -> "not-a-date",
          "paymentDueDate" -> "not-a-date",
          "ihtpVersion" -> "001",
          "inheritanceTaxReference" -> "A123456/25A",
          "firstForename" -> "John",
          "surname" -> "Doe",
          "ihtpStatus" -> "Not reconciled"
        )
        .validate[IhtpOverviewReport] mustBe a[JsError]
    }

    "must expose all case class values" in {
      report.fbNumber mustBe Some("119000004320")
      report.submissionDate mustBe Some(submissionDate)
      report.paymentDueDate mustBe Some(paymentDueDate)
      report.ihtpVersion mustBe "001"
      report.inheritanceTaxReference mustBe "A123456/25A"
      report.paymentReference mustBe Some("A123456/25A629671")
      report.title mustBe Some("Dr")
      report.firstForename mustBe Some("John")
      report.secondForename mustBe Some("M")
      report.surname mustBe Some("Doe")
      report.nino mustBe Some("AB123456C")
      report.ihtpStatus mustBe "Not reconciled"
      report.copy(
        fbNumber = report.fbNumber,
        submissionDate = report.submissionDate,
        paymentDueDate = report.paymentDueDate,
        ihtpVersion = report.ihtpVersion,
        inheritanceTaxReference = report.inheritanceTaxReference,
        paymentReference = report.paymentReference,
        title = report.title,
        firstForename = report.firstForename,
        secondForename = report.secondForename,
        surname = report.surname,
        nino = report.nino,
        ihtpStatus = report.ihtpStatus
      ) mustBe report
      report.productArity mustBe 13
      report.productPrefix mustBe "IhtpOverviewReport"
      (report.productIterator.toSeq must contain).allOf(Some("119000004320"), "001", Some("John"), Some("Doe"))
      report.toString must include("IhtpOverviewReport")
    }

    "must format deceasedName with title and second forename" in {
      report.deceasedName mustBe "Dr John M Doe"
    }

    "must format deceasedName without title" in {
      report.copy(title = None).deceasedName mustBe "John M Doe"
    }

    "must format deceasedName without second forename" in {
      report.copy(secondForename = None).deceasedName mustBe "Dr John Doe"
    }

    "must format deceasedName with only required name fields" in {
      report.copy(title = None, secondForename = None).deceasedName mustBe "John Doe"
    }

    "must not add extra spaces when optional name fields are empty strings" in {
      report.copy(title = Some(""), secondForename = Some("")).deceasedName mustBe "John Doe"
    }
  }
}
