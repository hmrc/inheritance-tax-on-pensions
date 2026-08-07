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
import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector.RetryableReportResponse
import uk.gov.hmrc.inheritancetaxonpensions.connectors.helpers.HIPHeaders
import org.apache.pekko.actor.ActorSystem
import models.IhtpOverviewResponse
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.inheritancetaxonpensions.models._
import play.api.Logging
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.json.{JsValue, Json}
import play.api.http.Status._
import uk.gov.hmrc.http.{StringContextOps, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import java.time.Instant
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import java.net.URLEncoder

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

  def getOverview(pstr: String, dateFrom: String, dateTo: String, status: Option[String])(implicit
    hc: HeaderCarrier
  ): Future[Either[ErrorResponse, IhtpOverviewResponse]] = {
    val url: String = overviewUrl(pstr, dateFrom, dateTo, status)

    retryFor[Either[ErrorResponse, IhtpOverviewResponse]]("IHTP Report overview") {
      case UpstreamErrorResponse.WithStatusCode(status) if Constants.TransientErrorStatusCodes.contains(status) => true
    } {
      val startTime = Instant.now()
      httpClient
        .get(url"$url")
        .setHeader(headers.ihtpReportHeaders()*)
        .execute[HttpResponse]
        .map {
          case response if response.status == OK =>
            logger.info("[IhtpReportConnector][getOverview] IHTP Report overview retrieved successfully")
            Right(response.json.as[IhtpOverviewResponse])
          case response if response.status == BAD_REQUEST =>
            logger.warn("[IhtpReportConnector][getOverview] Bad request returned for overview")
            Left(ErrorCodes.badRequest)
          case response if response.status == NOT_FOUND =>
            logger.warn("[IhtpReportConnector][getOverview] Not found returned for overview")
            Left(ErrorCodes.entityNotFound)
          case response if response.status == UNPROCESSABLE_ENTITY =>
            logger.warn("[IhtpReportConnector][getOverview] Unprocessable entity returned for overview")
            Left(ErrorCodes.unprocessableEntity)
          case response if Constants.TransientErrorStatusCodes.contains(response.status) =>
            throw UpstreamErrorResponse(
              s"Transient error: ${response.status}",
              response.status,
              response.status
            )
          case response =>
            logger.warn(
              s"[IhtpReportConnector][getOverview] Unexpected status returned for overview: ${response.status}"
            )
            Left(ErrorCodes.unexpectedResponse)
        }
        .recoverWith {
          case errorResponse @ UpstreamErrorResponse.WithStatusCode(statusCode)
              if Constants.TransientErrorStatusCodes.contains(statusCode) =>
            val elapsedTime = java.time.Duration.between(startTime, Instant.now()).toSeconds
            logger.warn(
              s"[IhtpReportConnector][getOverview] IHTP Report overview failed with status: $statusCode and took: ${elapsedTime}s. Error: ${errorResponse.getMessage}"
            )
            Future.failed(errorResponse)
        }
    }.recover {
      case UpstreamErrorResponse.WithStatusCode(statusCode)
          if Constants.TransientErrorStatusCodes.contains(statusCode) =>
        Left(ErrorCodes.unexpectedResponse)
    }
  }

  def getReport(
    pstr: String,
    fbNumber: Option[String],
    paymentReferenceNumber: Option[String],
    versionNumber: Option[String]
  )(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url: String = reportUrl(pstr, fbNumber, paymentReferenceNumber, versionNumber)
    val reportHeaders = headers.ihtpReportHeaders()
    val correlationId = reportHeaders
      .collectFirst { case (name, value) if name.equalsIgnoreCase(correlationIdHeader) => value }
      .getOrElse(throw new IllegalStateException("HIP report headers must contain a correlation ID"))

    retryFor[HttpResponse]("IHTP Report retrieval") { case RetryableReportResponse(_) =>
      true
    } {
      val startTime = Instant.now()
      httpClient
        .get(url"$url")
        .setHeader(reportHeaders*)
        .execute[HttpResponse]
        .map {
          case response if Constants.TransientErrorStatusCodes.contains(response.status) =>
            throw RetryableReportResponse(response)
          case response =>
            normaliseReportResponse(response, correlationId)
        }
        .recoverWith { case errorResponse @ RetryableReportResponse(response) =>
          val elapsedTime = java.time.Duration.between(startTime, Instant.now()).toSeconds
          logger.warn(
            s"[IhtpReportConnector][getReport] IHTP Report retrieval failed with status: ${response.status} and took: ${elapsedTime}s. Error: ${errorResponse.getMessage}"
          )
          Future.failed(errorResponse)
        }
    }.recover { case RetryableReportResponse(response) =>
      normaliseReportResponse(response, correlationId)
    }
  }

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
        .setHeader(headers.ihtpReportHeaders()*)
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
      case UpstreamErrorResponse.WithStatusCode(statusCode)
          if Constants.TransientErrorStatusCodes.contains(statusCode) =>
        Left(ErrorCodes.unexpectedResponse)
    }
  }

  private def normaliseReportResponse(response: HttpResponse, correlationId: String): HttpResponse =
    response.status match {
      case OK =>
        Try(response.json) match {
          case Success(_) =>
            logger.info("[IhtpReportConnector][getReport] IHTP Report retrieved successfully")
            withCorrelationId(response, correlationId)
          case Failure(error) =>
            logger.warn(
              s"[IhtpReportConnector][getReport] Invalid JSON returned for successful report retrieval: ${error.getMessage}"
            )
            unexpectedReportResponse(correlationId)
        }
      case status if documentedReportStatusCodes.contains(status) =>
        logger.warn(s"[IhtpReportConnector][getReport] Error status returned for report retrieval: $status")
        withCorrelationId(response, correlationId)
      case status =>
        logger.warn(s"[IhtpReportConnector][getReport] Unexpected status returned for report retrieval: $status")
        unexpectedReportResponse(correlationId)
    }

  private def withCorrelationId(response: HttpResponse, correlationId: String): HttpResponse =
    if (response.header(correlationIdHeader).isDefined) {
      response
    } else {
      HttpResponse(
        response.status,
        response.body,
        response.headers + (correlationIdHeader -> Seq(correlationId))
      )
    }

  private def unexpectedReportResponse(correlationId: String): HttpResponse =
    HttpResponse(
      INTERNAL_SERVER_ERROR,
      Json.obj(
        "origin" -> "HIP",
        "response" -> Json.arr(
          Json.obj(
            "type" -> "Unexpected response",
            "reason" -> "An unexpected response was received from the downstream service"
          )
        )
      ),
      Map(
        "Content-Type" -> Seq("application/json"),
        correlationIdHeader -> Seq(correlationId)
      )
    )

  private def overviewUrl(pstr: String, dateFrom: String, dateTo: String, status: Option[String]): String = {
    val queryParams =
      Seq("pstr" -> pstr, "dateFrom" -> dateFrom, "dateTo" -> dateTo) ++ status.map("status" -> _)

    val queryString = queryParams
      .map { case (key, value) => s"$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
      .mkString("&")

    s"${config.getOverviewUrl}?$queryString"
  }

  private def reportUrl(
    pstr: String,
    fbNumber: Option[String],
    paymentReferenceNumber: Option[String],
    versionNumber: Option[String]
  ): String = {
    val queryParams = Seq(
      Some("pstr" -> pstr),
      fbNumber.map("fbNumber" -> _),
      paymentReferenceNumber.map("paymentReferenceNumber" -> _),
      versionNumber.map("versionNumber" -> _)
    ).flatten

    val queryString = queryParams
      .map { case (key, value) => s"$key=${URLEncoder.encode(value, StandardCharsets.UTF_8)}" }
      .mkString("&")

    s"${config.getReportUrl}?$queryString"
  }

  private val correlationIdHeader = "correlationid"
  private val documentedReportStatusCodes =
    Set(
      BAD_REQUEST,
      UNAUTHORIZED,
      FORBIDDEN,
      NOT_FOUND,
      UNSUPPORTED_MEDIA_TYPE,
      UNPROCESSABLE_ENTITY,
      INTERNAL_SERVER_ERROR,
      SERVICE_UNAVAILABLE
    )
}

object IhtpReportConnector {
  final private case class RetryableReportResponse(response: HttpResponse)
      extends RuntimeException(s"Transient error: ${response.status}")
}
