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
  private val allocatedDate = LocalDate.of(2027, 10, 31)

  private val report = IhtpOverviewReport(
    uuid = None,
    fbNumber = Some(testFormBundleNumber),
    submissionDate = Some(submissionDate),
    paymentDueDate = Some(paymentDueDate),
    ihtVersion = "001",
    inheritanceTaxReference = testIhtReference,
    ihtPaymentReference = Some(testIhtPaymentReference),
    firstForename = Some("Firstname"),
    surname = Some("Surname"),
    nino = Some(testNino),
    ihtStatus = "Not reconciled",
    allocatedDate = Some(allocatedDate)
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
              "fbNumber" -> testFormBundleNumber,
              "submissionDate" -> "2026-04-10T16:12:49Z",
              "paymentDueDate" -> "2026-02-02",
              "ihtVersion" -> "001",
              "inheritanceTaxReference" -> testIhtReference,
              "ihtPaymentReference" -> testIhtPaymentReference,
              "title" -> "Dr",
              "firstForename" -> "Firstname",
              "secondForename" -> "M",
              "surname" -> "Surname",
              "nino" -> testNino,
              "ihtStatus" -> "Not reconciled",
              "allocatedDate" -> "2027-10-31"
            )
          )
        )
      )

      json.validate[IhtpOverviewResponse] mustBe JsSuccess(response)
    }

    "must successfully write to json" in {
      val json = Json.toJson(response)

      (json \ "success" \ "ihtpOverview" \ 0 \ "fbNumber").as[String] mustBe testFormBundleNumber
      (json \ "success" \ "ihtpOverview" \ 0 \ "submissionDate").as[String] mustBe "2026-04-10T16:12:49Z"
      (json \ "success" \ "ihtpOverview" \ 0 \ "paymentDueDate").as[String] mustBe "2026-02-02"
      (json \ "success" \ "ihtpOverview" \ 0 \ "ihtVersion").as[String] mustBe "001"
      (json \ "success" \ "ihtpOverview" \ 0 \ "inheritanceTaxReference").as[String] mustBe testIhtReference
      (json \ "success" \ "ihtpOverview" \ 0 \ "ihtPaymentReference").as[String] mustBe testIhtPaymentReference
      (json \ "success" \ "ihtpOverview" \ 0 \ "firstForename").as[String] mustBe "Firstname"
      (json \ "success" \ "ihtpOverview" \ 0 \ "surname").as[String] mustBe "Surname"
      (json \ "success" \ "ihtpOverview" \ 0 \ "nino").as[String] mustBe testNino
      (json \ "success" \ "ihtpOverview" \ 0 \ "ihtStatus").as[String] mustBe "Not reconciled"
      (json \ "success" \ "ihtpOverview" \ 0 \ "allocatedDate").as[String] mustBe "2027-10-31"
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

    "must retain only the highest numerical version for each payment reference" in {
      val versionOne = report.copy(ihtVersion = "001")
      val versionTwo = report.copy(ihtVersion = "002", fbNumber = Some(secondFormBundleNumber))
      val otherReport = report.copy(
        ihtPaymentReference = Some(secondIhtPaymentReference),
        inheritanceTaxReference = secondIhtReference
      )

      val result = IhtpOverviewSuccess.filterForHighestVersion(
        List(versionOne, otherReport, versionTwo),
        Nil
      )

      result.ihtpOverview must contain theSameElementsAs Seq(versionTwo, otherReport)
    }

    "must retain reports that do not have a payment reference" in {
      val firstDraft = report.copy(uuid = Some("first-draft"), ihtPaymentReference = None, ihtVersion = "000")
      val secondDraft = report.copy(uuid = Some("second-draft"), ihtPaymentReference = None, ihtVersion = "000")

      val result = IhtpOverviewSuccess.filterForHighestVersion(List(firstDraft, secondDraft), Nil)

      result.ihtpOverview mustBe Seq(firstDraft, secondDraft)
    }
  }

  "IhtpOverviewReport" - {

    "must successfully read from json" in {
      val json = Json.obj(
        "fbNumber" -> testFormBundleNumber,
        "submissionDate" -> "2026-04-10T16:12:49Z",
        "paymentDueDate" -> "2026-02-02",
        "ihtVersion" -> "001",
        "inheritanceTaxReference" -> testIhtReference,
        "ihtPaymentReference" -> testIhtPaymentReference,
        "title" -> "Dr",
        "firstForename" -> "Firstname",
        "secondForename" -> "M",
        "surname" -> "Surname",
        "nino" -> testNino,
        "ihtStatus" -> "Not reconciled",
        "allocatedDate" -> "2027-10-31"
      )

      json.validate[IhtpOverviewReport] mustBe JsSuccess(report)
    }

    "must successfully write to json" in {
      val json = Json.toJson(report)

      json.validate[IhtpOverviewReport] mustBe JsSuccess(report)
    }

    "must handle missing optional fields when reading from json" in {
      val json = Json.obj(
        "ihtVersion" -> "000",
        "ihtPaymentReference" -> secondIhtPaymentReference,
        "ihtStatus" -> "In progress"
      )

      json.validate[IhtpOverviewReport] mustBe JsSuccess(
        IhtpOverviewReport(
          uuid = None,
          fbNumber = None,
          submissionDate = None,
          paymentDueDate = None,
          ihtVersion = "000",
          inheritanceTaxReference = secondIhtReference,
          ihtPaymentReference = Some(secondIhtPaymentReference),
          firstForename = None,
          surname = None,
          nino = None,
          ihtStatus = "In progress",
          allocatedDate = None
        )
      )
    }

    "must write optional fields as null when they are not present" in {
      val report = this.report.copy(
        ihtPaymentReference = None,
        nino = None,
        allocatedDate = None
      )

      val json = Json.toJson(report)

      (json \ "ihtPaymentReference").asOpt[String] mustBe None
      (json \ "title").asOpt[String] mustBe None
      (json \ "secondForename").asOpt[String] mustBe None
      (json \ "nino").asOpt[String] mustBe None
      (json \ "allocatedDate").asOpt[String] mustBe None
    }

    "must fail to read when required fields are missing" in {
      Json
        .obj(
          "submissionDate" -> "2026-04-10T16:12:49Z",
          "paymentDueDate" -> "2026-02-02",
          "inheritanceTaxReference" -> testIhtReference,
          "firstForename" -> "Firstname",
          "surname" -> "Surname",
          "ihtStatus" -> "Not reconciled"
        )
        .validate[IhtpOverviewReport] mustBe a[JsError]
    }

    "must fail to read when date fields have the wrong type" in {
      Json
        .obj(
          "fbNumber" -> testFormBundleNumber,
          "submissionDate" -> "not-a-date",
          "paymentDueDate" -> "not-a-date",
          "ihtVersion" -> "001",
          "inheritanceTaxReference" -> testIhtReference,
          "firstForename" -> "Firstname",
          "surname" -> "Surname",
          "ihtStatus" -> "Not reconciled"
        )
        .validate[IhtpOverviewReport] mustBe a[JsError]
    }

    "must expose all case class values" in {
      report.fbNumber mustBe Some(testFormBundleNumber)
      report.submissionDate mustBe Some(submissionDate)
      report.paymentDueDate mustBe Some(paymentDueDate)
      report.ihtVersion mustBe "001"
      report.inheritanceTaxReference mustBe testIhtReference
      report.ihtPaymentReference mustBe Some(testIhtPaymentReference)
      report.firstForename mustBe Some("Firstname")
      report.surname mustBe Some("Surname")
      report.nino mustBe Some(testNino)
      report.ihtStatus mustBe "Not reconciled"
      report.copy(
        fbNumber = report.fbNumber,
        submissionDate = report.submissionDate,
        paymentDueDate = report.paymentDueDate,
        ihtVersion = report.ihtVersion,
        inheritanceTaxReference = report.inheritanceTaxReference,
        ihtPaymentReference = report.ihtPaymentReference,
        firstForename = report.firstForename,
        surname = report.surname,
        nino = report.nino,
        ihtStatus = report.ihtStatus,
        allocatedDate = report.allocatedDate
      ) mustBe report
      report.productArity mustBe 12
      report.productPrefix mustBe "IhtpOverviewReport"
      (report.productIterator.toSeq must contain).allOf(
        Some(testFormBundleNumber),
        "001",
        Some("Firstname"),
        Some("Surname")
      )
      report.toString must include("IhtpOverviewReport")
    }

    "must format deceasedName" in {
      report.deceasedName mustBe "Firstname Surname"
    }
  }
}
