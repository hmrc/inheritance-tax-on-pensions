/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.inheritancetaxonpensions.connectors

import uk.gov.hmrc.inheritancetaxonpensions.config.AppConfig
import uk.gov.hmrc.inheritancetaxonpensions.connectors.helpers.HIPHeaders
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.inheritancetaxonpensions.models._
import play.api.Logging
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.json.Json
import play.api.http.Status._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import javax.inject.Inject

class IhtpReportConnector @Inject() (
  headers: HIPHeaders,
  config: AppConfig,
  implicit val httpClient: HttpClientV2
)(implicit ec: ExecutionContext)
    extends HttpReadsInstances
    with Logging {

  def submitReport(srn: Srn, ihtpReportSubmission: IhtpReportSubmission)(implicit
    hc: HeaderCarrier
  ): Future[Either[ErrorResponse, IhtpReportSubmissionResponse]] = {
    val url: String = config.submitIhtpReportUrl(srn)

    httpClient
      .post(url"$url")
      .setHeader(headers.ihtpReportSubmissionHeaders()*)
      .withBody(Json.toJson(ihtpReportSubmission))
      .execute[HttpResponse]
      .flatMap {
        case response if response.status == OK =>
          Try(response.json.as[IhtpReportSubmissionResponse]) match {
            case Success(submissionResponse) =>
              logger.info(
                s"[IhtpReportConnector][submitReport] IHTP Report submitted successfully for srn ${srn.value}"
              )
              Future.successful(Right(submissionResponse))
            case Failure(_) =>
              logger.warn(
                s"[IhtpReportConnector][submitReport] Parsing failed for submission response for srn ${srn.value}"
              )
              Future.successful(Left(ErrorCodes.unexpectedResponse))
          }
        case response if response.status == BAD_REQUEST =>
          logger.warn(
            s"[IhtpReportConnector][submitReport] Bad request returned for submission for srn ${srn.value}"
          )
          Future.successful(Left(ErrorCodes.badRequest))
        case response if response.status == NOT_FOUND =>
          logger.warn(
            s"[IhtpReportConnector][submitReport] Not found returned for submission for srn ${srn.value}"
          )
          Future.successful(Left(ErrorCodes.entityNotFound))
        case response if response.status == UNPROCESSABLE_ENTITY =>
          logger.warn(
            s"[IhtpReportConnector][submitReport] Unprocessable entity returned for submission for srn ${srn.value}"
          )
          Future.successful(Left(ErrorCodes.unprocessableEntity))
        case response => // All transient (502, 503 ....)
          logger.warn(
            s"[IhtpReportConnector][submitReport] Received unexpected response for submission for srn ${srn.value}"
          )
          Future.successful(Left(ErrorCodes.unexpectedResponse))
      }
  }
}
