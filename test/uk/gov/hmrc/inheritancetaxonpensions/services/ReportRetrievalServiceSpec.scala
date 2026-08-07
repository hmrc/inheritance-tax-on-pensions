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
import play.api.libs.json.Json
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HeaderCarrier

import scala.language.postfixOps
import scala.concurrent.Future

import java.time.Instant

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
  private val service = new ReportRetrievalService(mockUserAnswersRepository, mockIhtpReportConnector)

  "getOverview" - {
    "returns all reports that are coming from the connector in the response" in {

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
          value.success.ihtpOverview must have size 3
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
                  "inheritanceTaxReference" -> "A000001/01A"
                )
              )
            )
          )
        )

      val result = service.getOverview(testPstr, srn, testDateFrom, testDateTo, None).futureValue

      result match {
        case Left(value) => fail("unexpected")
        case Right(value) =>
          value.success.ihtpOverview must have size 4
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
          value.success.ihtpOverview must have size 4
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
          value.success.ihtpOverview must have size 3
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
}
