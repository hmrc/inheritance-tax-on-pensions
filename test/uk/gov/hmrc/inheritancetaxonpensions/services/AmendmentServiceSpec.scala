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

import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.inheritancetaxonpensions.models._
import org.scalatestplus.mockito.MockitoSugar
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito._
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo.Yes
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.mockito.ArgumentMatchers._
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.IndividualOrTrust

import scala.concurrent.Future

class AmendmentServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach with TestValues {

  override def beforeEach(): Unit = reset(mockIhtpReportConnector)

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val mockIhtpReportConnector: IhtpReportConnector = mock[IhtpReportConnector]
  private val service = new AmendmentService(mockIhtpReportConnector)

  private val previousVersionJson: JsObject = (testIhtNoticeResponse \ "ihtNoticeResponse").get.as[JsObject]
  private val previousVersion: IhtNoticeResponse = previousVersionJson.as[IhtNoticeResponse]

  private val submission = IhtpPaymentNoticeSubmission(
    IhtNoticeRequest(
      ReportDetails(testPstr, Some(testIhtPaymentReference)),
      previousVersion.deceased,
      previousVersion.personalRep,
      previousVersion.ihTaxInformation,
      previousVersion.beneficiary,
      declarationsPayloadSection
    )
  )

  private val amendmentUserAnswers = UserAnswers(
    testUserAnswersId,
    srn,
    uuid,
    Json.obj(
      "ihtVersion" -> "002",
      "ihtPaymentReference" -> testIhtPaymentReference
    )
  )

  private def stubPreviousVersion(ihtNoticeResponseJson: JsObject = previousVersionJson): Unit =
    when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
      .thenReturn(
        Future.successful(
          HttpResponse(status = 200, body = Json.obj("ihtNoticeResponse" -> ihtNoticeResponseJson).toString)
        )
      )

  "applyChangeFlags" - {
    "set deceasedChangeFlag to Yes when the deceased section differs from the previous version" in {
      stubPreviousVersion(
        previousVersionJson.deepMerge(
          Json.obj("deceased" -> Json.obj("deceasedPersonalDetails" -> Json.obj("surname" -> "ChangedSurname")))
        )
      )

      service
        .applyChangeFlags(submission, testPstr, amendmentUserAnswers)
        .futureValue
        .map(_.ihtNoticeRequest.deceased.deceasedChangeFlag) mustBe Right(Some(Yes))

      verify(mockIhtpReportConnector).getReport(
        org.mockito.ArgumentMatchers.eq(testPstr),
        org.mockito.ArgumentMatchers.eq(None),
        org.mockito.ArgumentMatchers.eq(Some(testIhtPaymentReference)),
        org.mockito.ArgumentMatchers.eq(Some("001"))
      )(any())
    }

    "set prChangeFlag to Yes when the personalRep section differs from the previous version" in {
      stubPreviousVersion(
        previousVersionJson.deepMerge(
          Json.obj("personalRep" -> Json.obj("prContactDetails" -> Json.obj("surname" -> "ChangedSurname")))
        )
      )

      service
        .applyChangeFlags(submission, testPstr, amendmentUserAnswers)
        .futureValue
        .map(_.ihtNoticeRequest.personalRep.prChangeFlag) mustBe Right(Some(Yes))
    }

    "set ihTaxChangeFlag to Yes when the ihTaxInformation section differs from the previous version" in {
      stubPreviousVersion(
        previousVersionJson.deepMerge(
          Json.obj("ihTaxInformation" -> Json.obj("dateNoticeReceived" -> "2026-01-01"))
        )
      )

      service
        .applyChangeFlags(submission, testPstr, amendmentUserAnswers)
        .futureValue
        .map(_.ihtNoticeRequest.ihTaxInformation.ihTaxChangeFlag) mustBe Right(Some(Yes))
    }

    "set beneficiariesChangeFlag to Yes when a beneficiary is added compared to the previous version" in {
      stubPreviousVersion()
      val submissionWithBeneficiary = submission.copy(ihtNoticeRequest =
        submission.ihtNoticeRequest.copy(
          beneficiary = Some(
            Beneficiaries(
              Seq(
                BeneficiaryDetails(
                  beneficiaryType = IndividualOrTrust.Individual,
                  beneficiaryPersonalDetails = beneficiaryPersonalDetailsPayloadSection,
                  beneficiaryAddress = beneficiaryAddressPayloadSection,
                  beneficiaryPaymentDetails = beneficiaryPaymentDetailsPayloadSection
                )
              )
            )
          )
        )
      )

      service
        .applyChangeFlags(submissionWithBeneficiary, testPstr, amendmentUserAnswers)
        .futureValue
        .map(_.ihtNoticeRequest.beneficiary.get.beneficiariesChangeFlag) mustBe Right(Some(Yes))
    }

    "omit all change flags when nothing differs from the previous version" in {
      stubPreviousVersion()

      val result = service.applyChangeFlags(submission, testPstr, amendmentUserAnswers).futureValue.toOption.get
      result.ihtNoticeRequest.deceased.deceasedChangeFlag mustBe None
      result.ihtNoticeRequest.personalRep.prChangeFlag mustBe None
      result.ihtNoticeRequest.ihTaxInformation.ihTaxChangeFlag mustBe None
      result.ihtNoticeRequest.beneficiary mustBe None
    }

    "not fetch a previous version when the report version is 001" in {
      val versionOneUserAnswers =
        amendmentUserAnswers.copy(data = amendmentUserAnswers.data ++ Json.obj("ihtVersion" -> "001"))

      service.applyChangeFlags(submission, testPstr, versionOneUserAnswers).futureValue mustBe Right(submission)
      verify(mockIhtpReportConnector, never()).getReport(any(), any(), any(), any())(any())
    }

    "not fetch a previous version for a new report" in {
      val newReportUserAnswers = UserAnswers(testUserAnswersId, srn, uuid)

      service.applyChangeFlags(submission, testPstr, newReportUserAnswers).futureValue mustBe Right(submission)
      verify(mockIhtpReportConnector, never()).getReport(any(), any(), any(), any())(any())
    }

    "return Left when the previous version cannot be retrieved" in {
      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(status = 500, body = "")))

      service.applyChangeFlags(submission, testPstr, amendmentUserAnswers).futureValue.isLeft mustBe true
    }

    "return Left when the previous version response cannot be parsed" in {
      when(mockIhtpReportConnector.getReport(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(status = 200, body = "{}")))

      service.applyChangeFlags(submission, testPstr, amendmentUserAnswers).futureValue.isLeft mustBe true
    }
  }
}
