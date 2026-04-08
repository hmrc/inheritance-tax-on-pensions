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

package uk.gov.hmrc.inheritancetaxonpensions.controllers

import uk.gov.hmrc.inheritancetaxonpensions.connectors.SchemeDetailsConnector
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.inheritancetaxonpensions.auth.IhtpAuthWithSessionCache
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.services.{ReportSubmissionService, SessionService}
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.Logging
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}

@Singleton
class ReportSubmissionController @Inject() (
  val reportSubmissionService: ReportSubmissionService,
  cc: ControllerComponents,
  override val authConnector: AuthConnector,
  override protected val schemeDetailsConnector: SchemeDetailsConnector,
  override protected val sessionService: SessionService
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with IhtpAuthWithSessionCache
    with BaseController
    with Logging {

  def submitReport(pstr: String, userAnswersId: String): Action[AnyContent] = Action.async { implicit request =>
    val Seq(userName, schemeName, requestRole) =
      requiredHeaders(HEADER_KEY_USER_NAME, HEADER_KEY_SCHEME_NAME, HEADER_KEY_REQUEST_ROLE)

    reportSubmissionService.submitReport(userAnswersId, pstr).map {
      case Right(submissionResponse) =>
        logger.info(s"[ReportSubmissionController][submitReport] Report submitted successfully for pstr $pstr")
        Ok(Json.toJson(submissionResponse))

      case Left(errorResponse) =>
        logger.warn(
          s"[ReportSubmissionController][submitReport] Report submission failed for pstr $pstr: ${errorResponse.message}"
        )
        Status(errorResponse.statusCode)(Json.toJson(errorResponse))
    }
  }
}
