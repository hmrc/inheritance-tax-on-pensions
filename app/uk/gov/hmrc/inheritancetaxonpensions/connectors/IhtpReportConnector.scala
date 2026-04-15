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

import com.typesafe.config.Config
import uk.gov.hmrc.inheritancetaxonpensions.config.{AppConfig, Constants}
import uk.gov.hmrc.inheritancetaxonpensions.connectors.helpers.HIPHeaders
import org.apache.pekko.actor.ActorSystem
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.inheritancetaxonpensions.models.*
import play.api.Logging
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.json.Json
import play.api.http.Status.*
import uk.gov.hmrc.http.{StringContextOps, *}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import java.time.Instant
import javax.inject.Inject

class IhtpReportConnector @Inject() (
  headers: HIPHeaders,
  config: AppConfig,
  implicit val httpClient: HttpClientV2,
  override val configuration: Config,
  override val actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends HttpReadsInstances
    with Retries
    with Logging {

  def submitReport(ihtpReportSubmission: IhtpReportSubmission)(implicit
    hc: HeaderCarrier
  ): Future[Either[ErrorResponse, IhtpReportSubmissionResponse]] = {
    val url: String = config.submitReportUrl

    retryFor[Either[ErrorResponse, IhtpReportSubmissionResponse]]("IHTP Report submission") {
      case UpstreamErrorResponse.WithStatusCode(status) if Constants.TransientErrorStatusCodes.contains(status) => true
    } {
      val startTime = Instant.now()
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
                  "[IhtpReportConnector][submitReport] IHTP Report submitted successfully"
                )
                Future.successful(Right(submissionResponse))
              case Failure(_) =>
                logger.warn(
                  "[IhtpReportConnector][submitReport] Parsing failed for submission response"
                )
                Future.successful(Left(ErrorCodes.unexpectedResponse))
            }
          case response if response.status == BAD_REQUEST =>
            logger.warn(
              "[IhtpReportConnector][submitReport] Bad request returned for submission"
            )
            Future.successful(Left(ErrorCodes.badRequest))
          case response if response.status == NOT_FOUND =>
            logger.warn(
              "[IhtpReportConnector][submitReport] Not found returned for submission"
            )
            Future.successful(Left(ErrorCodes.entityNotFound))
          case response if response.status == UNPROCESSABLE_ENTITY =>
            logger.warn(
              "[IhtpReportConnector][submitReport] Unprocessable entity returned for submission"
            )
            Future.successful(Left(ErrorCodes.unprocessableEntity))
          case response if Constants.TransientErrorStatusCodes.contains(response.status) =>
            throw UpstreamErrorResponse(
              s"Transient error: ${response.status}",
              response.status,
              response.status
            )
        }
        .recoverWith {
          case errorResponse @ UpstreamErrorResponse.WithStatusCode(statusCode)
              if Constants.TransientErrorStatusCodes.contains(statusCode) =>
            val elapsedTime = java.time.Duration.between(startTime, Instant.now()).toSeconds
            logger.warn(
              s"[IhtpReportConnector][submitReport] IHTP Report submission failed with status: $statusCode and took: ${elapsedTime}s. Error: ${errorResponse.getMessage}"
            )
            Future.failed(errorResponse)
        }
    }.recover {
      case UpstreamErrorResponse.WithStatusCode(statusCode) if Constants.TransientErrorStatusCodes.contains(statusCode) =>
        Left(ErrorCodes.unexpectedResponse)
    }
  }
}
