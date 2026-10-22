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

package uk.gov.hmrc.inheritancetaxonpensions.services

import models.{IhtpOverviewReport, IhtpOverviewResponse, IhtpOverviewSuccess}
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import uk.gov.hmrc.inheritancetaxonpensions.models.ErrorCodes
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers._
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.language.postfixOps
import scala.concurrent.Future

import java.time.{Clock, Instant, ZoneId}
import java.time.temporal.ChronoUnit

class ReportRetrievalServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with BeforeAndAfterEach
    with TestValues {

  override def beforeEach(): Unit = reset(mockUserAnswersRepository, mockIhtpReportConnector)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val mockUserAnswersRepository: UserAnswersRepository = mock[UserAnswersRepository]
  private val mockIhtpReportConnector: IhtpReportConnector = mock[IhtpReportConnector]
  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
  private val service = new ReportRetrievalService(mockUserAnswersRepository, mockIhtpReportConnector, stubClock)
  private val testIhtVersion = "001"

  "getOverview" - {
    "returns only the latest version of each report coming from the connector" in {

      when(mockIhtpReportConnector.getOverview(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            Right(
              IhtpOverviewResponse(IhtpOverviewSuccess(testOverviewResponse.as[Seq[IhtpOverviewReport]]))
            )
          )
        )

      when(mockUserAnswersRepository.findBySrn(any()))
        .thenReturn(
          Future.successful(Seq())
        )

      val result = service.getOverview(testPstr, srn, testDateFrom, testDateTo, None).futureValue

      result match {
        case Left(value) => fail("unexpected")
        case Right(value) =>
          value.success.ihtpOverview must have size 2
          value.success.ihtpOverview
            .filter(_.paymentReference.contains(testIhtPaymentReference))
            .map(_.ihtVersion) mustBe Seq("002")
      }
    }
    "returns all coming from both the connector and from user answers" in {
      when(mockIhtpReportConnector.getOverview(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            Right(
              IhtpOverviewResponse(IhtpOverviewSuccess(testOverviewResponse.as[Seq[IhtpOverviewReport]]))
            )
          )
        )

      when(mockUserAnswersRepository.findBySrn(any()))
        .thenReturn(
          Future.successful(
            Seq(
              emptyUserAnswers.copy(data =
                Json.obj(
                  "inheritanceTaxReference" -> testIhtReference
                )
              )
            )
          )
        )

      val result = service.getOverview(testPstr, srn, testDateFrom, testDateTo, None).futureValue

      result match {
        case Left(value) => fail("unexpected")
        case Right(value) =>
          value.success.ihtpOverview must have size 3
      }
    }
    "returns all coming from both the connector and complete ua" in {

      when(mockIhtpReportConnector.getOverview(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            Right(
              IhtpOverviewResponse(IhtpOverviewSuccess(testOverviewResponse.as[Seq[IhtpOverviewReport]]))
            )
          )
        )

      when(mockUserAnswersRepository.findBySrn(any()))
        .thenReturn(
          Future.successful(
            Seq(
              emptyUserAnswers.copy(data = testUserAnswerJson)
            )
          )
        )

      val result = service.getOverview(testPstr, srn, testDateFrom, testDateTo, None).futureValue

      result match {
        case Left(value) => fail("unexpected")
        case Right(value) =>
          value.success.ihtpOverview must have size 3
      }
    }
    "filters out reports that are not updated after the submission" in {

      when(mockIhtpReportConnector.getOverview(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            Right(
              IhtpOverviewResponse(IhtpOverviewSuccess(testOverviewResponse.as[Seq[IhtpOverviewReport]]))
            )
          )
        )

      when(mockUserAnswersRepository.findBySrn(any()))
        .thenReturn(
          Future.successful(
            Seq(
              emptyUserAnswers.copy(data = testUserAnswerJson, lastUpdated = Instant.EPOCH)
            )
          )
        )

      val result = service.getOverview(testPstr, srn, testDateFrom, testDateTo, None).futureValue

      result match {
        case Left(value) => fail("unexpected")
        case Right(value) =>
          value.success.ihtpOverview must have size 2
      }
    }
    "filters out drafts that match a paid report even when updated after the submission" in {

      when(mockIhtpReportConnector.getOverview(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            Right(
              IhtpOverviewResponse(
                IhtpOverviewSuccess(
                  testOverviewResponse.as[Seq[IhtpOverviewReport]].filter(_.ihtpStatus == "Paid")
                )
              )
            )
          )
        )

      when(mockUserAnswersRepository.findBySrn(any()))
        .thenReturn(
          Future.successful(
            Seq(
              emptyUserAnswers.copy(data = testUserAnswerJson)
            )
          )
        )

      val result = service.getOverview(testPstr, srn, testDateFrom, testDateTo, None).futureValue

      result match {
        case Left(value) => fail("unexpected")
        case Right(value) =>
          value.success.ihtpOverview must have size 1
          value.success.ihtpOverview.map(_.ihtpStatus) mustBe Seq("Paid")
      }
    }
    "errors out when connector errors out" in {

      when(mockIhtpReportConnector.getOverview(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            Left(ErrorCodes.unexpectedResponse)
          )
        )

      when(mockUserAnswersRepository.findBySrn(any()))
        .thenReturn(
          Future.successful(Seq())
        )

      val result = service.getOverview(testPstr, srn, testDateFrom, testDateTo, None).futureValue

      result match {
        case Left(value) =>
          value.statusCode mustBe INTERNAL_SERVER_ERROR
        case Right(value) =>
          fail("unexpected")
      }
    }
  }

  "getReport" - {
    "returns the uuid, correlationid and userAnswers coming from the connector when no user answers are found" in {

      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            HttpResponse(
              status = 200,
              body = testIhtNoticeResponse.toString,
              headers = Map("correlationid" -> Seq("testCorrelationId"))
            )
          )
        )

      when(mockUserAnswersRepository.getByPaymentReference(any()))
        .thenReturn(Future.successful(None))
      when(mockUserAnswersRepository.set(any()))
        .thenReturn(Future.successful(true))

      val result = service.getReport(testPstr, srn, None, Some(testIhtPaymentReference), Some(testIhtVersion))

      result.futureValue match {
        case Left(value) => fail("unexpected")
        case Right((uuid, correlationId, userAnswers)) =>
          uuid must not be empty
          correlationId mustBe "testCorrelationId"

          val actualData = (userAnswers \ "data").as[JsObject]

          actualData - "ihtVersion" mustEqual testUserAnswerJson
          (actualData \ "ihtVersion").as[String] mustBe testIhtVersion
      }
    }

    "returns the uuid, correlationid and userAnswers coming from the connector when user answers are found" in {

      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            HttpResponse(
              status = 200,
              body = testIhtNoticeResponse.toString,
              headers = Map("correlationid" -> Seq("testCorrelationId"))
            )
          )
        )

      when(mockUserAnswersRepository.getByPaymentReference(any()))
        .thenReturn(Future.successful(Some(emptyUserAnswers.copy(data = testUserAnswerJson))))

      val result = service.getReport(testPstr, srn, None, Some(testIhtPaymentReference), Some(testIhtVersion))

      result.futureValue match {
        case Left(value) => fail("unexpected")
        case Right((uuid, correlationId, userAnswers)) =>
          uuid mustBe emptyUserAnswers.uuid
          correlationId mustBe "testCorrelationId"
          (userAnswers \ "srn").as[String] mustBe srn
          (userAnswers \ "uuid").as[String] mustBe uuid

          val actualData = (userAnswers \ "data").as[JsObject]

          actualData - "ihtVersion" mustEqual testUserAnswerJson
          (actualData \ "ihtVersion").as[String] mustBe testIhtVersion
      }

      verify(mockUserAnswersRepository, never()).set(any())
    }

    "persists refreshed data when user answers are found and lookup is by fbNumber" in {

      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            HttpResponse(
              status = 200,
              body = testIhtNoticeResponse.toString,
              headers = Map("correlationid" -> Seq("testCorrelationId"))
            )
          )
        )

      when(mockUserAnswersRepository.getByPaymentReference(any()))
        .thenReturn(Future.successful(Some(emptyUserAnswers.copy(data = testUserAnswerJson))))
      when(mockUserAnswersRepository.set(any()))
        .thenReturn(Future.successful(true))

      val result = service.getReport(testPstr, srn, Some("testFbNumber"), None, None)

      result.futureValue match {
        case Left(value) => fail("unexpected")
        case Right((uuid, correlationId, userAnswers)) =>
          uuid mustBe emptyUserAnswers.uuid
          correlationId mustBe "testCorrelationId"

          val actualData = (userAnswers \ "data").as[JsObject]

          actualData - "ihtVersion" mustEqual testUserAnswerJson
          (actualData \ "ihtVersion").as[String] mustBe testIhtVersion
      }

      verify(mockUserAnswersRepository, times(1)).set(any())
    }

    "maps beneficiary details from the response into user answers" in {

      val beneficiaryBlock = Json.obj(
        "ihtNoticeResponse" -> Json.obj(
          "deceased" -> Json.obj(
            "deceasedPersonalDetails" -> Json.obj(
              "reasonNoNino" -> "should be suppressed"
            )
          ),
          "beneficiary" -> Json.obj(
            "beneficiaryDetails" -> Json.arr(
              Json.obj(
                "beneficiaryType" -> "01",
                "beneficiaryPersonalDetails" -> Json.obj(
                  "title" -> "Mr",
                  "firstForename" -> "First",
                  "secondForename" -> "Middle",
                  "surname" -> "Surname",
                  "ninoExist" -> "Yes",
                  "nino" -> testNino,
                  "reasonNoNino" -> "should be suppressed"
                ),
                "beneficiaryAddress" -> Json.obj(
                  "addressLine1" -> "21 RB Road",
                  "addressLine2" -> "RBtown",
                  "postCode" -> "LL1 4LL",
                  "country" -> "GB"
                ),
                "beneficiaryPaymentDetails" -> Json.obj(
                  "beneficiaryIhtPayable" -> 60.0,
                  "beneficiaryInterestPayable" -> 6.0,
                  "beneficiaryTotal" -> 66.0
                )
              ),
              Json.obj(
                "beneficiaryType" -> "01",
                "beneficiaryPersonalDetails" -> Json.obj(
                  "title" -> "Ms",
                  "firstForename" -> "Second",
                  "surname" -> "Surname",
                  "ninoExist" -> "No",
                  "nino" -> "AB111111C",
                  "reasonNoNino" -> "no nino reason"
                ),
                "beneficiaryAddress" -> Json.obj(
                  "addressLine1" -> "21 RB Road",
                  "postCode" -> "LL1 4LL",
                  "country" -> "GB"
                ),
                "beneficiaryPaymentDetails" -> Json.obj(
                  "beneficiaryIhtPayable" -> 40.0,
                  "beneficiaryInterestPayable" -> 4.0,
                  "beneficiaryTotal" -> 44.0
                )
              )
            )
          )
        )
      )

      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(
          Future.successful(
            HttpResponse(
              status = 200,
              body = testIhtNoticeResponse.deepMerge(beneficiaryBlock).toString,
              headers = Map("correlationid" -> Seq("testCorrelationId"))
            )
          )
        )

      when(mockUserAnswersRepository.getByPaymentReference(any()))
        .thenReturn(Future.successful(None))
      when(mockUserAnswersRepository.set(any()))
        .thenReturn(Future.successful(true))

      val result = service.getReport(testPstr, srn, None, Some(testIhtPaymentReference), Some(testIhtVersion))

      result.futureValue match {
        case Left(_) => fail("unexpected")
        case Right((_, _, userAnswers)) =>
          val data = (userAnswers \ "data").as[JsObject]
          (data \ "hasNino").as[Boolean] mustBe true
          (data \ "nino").as[String] mustBe testNino
          (data \ "reasonNoNino").asOpt[String] mustBe empty

          val beneficiaries = (data \ "beneficiaries").as[JsArray].value
          beneficiaries.size mustBe 2

          val beneficiary = beneficiaries.head
          (beneficiary \ "beneficiaryType").as[String] mustBe "individual"
          (beneficiary \ "beneficiaryDetails" \ "individual" \ "firstForename").as[String] mustBe "First"
          (beneficiary \ "beneficiaryDetails" \ "individual" \ "surname").as[String] mustBe "Surname"
          (beneficiary \ "hasNino").as[Boolean] mustBe true
//          (beneficiary \ "nino").as[String] mustBe testNino
          (beneficiary \ "reasonNoNino").asOpt[String] mustBe empty
          (beneficiary \ "beneficiaryAddress" \ "addressLine1").as[String] mustBe "21 RB Road"
          (beneficiary \ "beneficiaryAddress" \ "addressLine2").as[String] mustBe "RBtown"
          (beneficiary \ "beneficiaryAddress" \ "postCode").as[String] mustBe "LL1 4LL"
          (beneficiary \ "beneficiaryAddress" \ "country").as[String] mustBe "GB"
          (beneficiary \ "beneficiaryIhtPayable").as[Double] mustBe 60.0
          (beneficiary \ "beneficiaryInterestPayable").as[Double] mustBe 6.0

          val secondBeneficiary = beneficiaries(1)
          (secondBeneficiary \ "hasNino").as[Boolean] mustBe false
          (secondBeneficiary \ "nino").asOpt[String] mustBe empty
          (secondBeneficiary \ "reasonNoNino").as[String] mustBe "no nino reason"
          (secondBeneficiary \ "beneficiaryAddress" \ "addressLine1").as[String] mustBe "21 RB Road"
          (secondBeneficiary \ "beneficiaryIhtPayable").as[Double] mustBe 40.0
          (secondBeneficiary \ "beneficiaryInterestPayable").as[Double] mustBe 4.0
      }
    }
  }
}
