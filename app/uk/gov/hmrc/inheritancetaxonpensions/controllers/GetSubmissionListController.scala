/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json.Json
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.services.{ReportRetrievalService, SessionService}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}

@Singleton()
class GetSubmissionListController @Inject() (
  cc: ControllerComponents,
  override val authConnector: AuthConnector,
  override protected val schemeDetailsConnector: SchemeDetailsConnector,
  override protected val sessionService: SessionService,
  reportRetrievalService: ReportRetrievalService
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with BaseController
    with IhtpAuthWithSessionCache {

  def getSubmissionList(pstr: String): Action[AnyContent] = Action.async { implicit request =>
    val Seq(userName, schemeName, srnS, requestRole) =
      requiredHeaders(HEADER_KEY_USER_NAME, HEADER_KEY_SCHEME_NAME, HEADER_KEY_SRN, HEADER_KEY_REQUEST_ROLE)
    authorisedAsIhtpUser(srnS) { _ =>
      val dateFrom = requiredQueryParam("dateFrom")
      val dateTo = requiredQueryParam("dateTo")
      val status = request.getQueryString("status")

      reportRetrievalService.getOverview(pstr, srnS, dateFrom, dateTo, status).map {
        case Right(response) => Ok(Json.toJson(response))
        case Left(error) => Status(error.statusCode)(Json.obj("message" -> error.message))
      }
    }
  }

  private def requiredQueryParam(name: String)(implicit request: play.api.mvc.Request[AnyContent]): String =
    request.getQueryString(name).getOrElse {
      throw new BadRequestException(s"Bad Request with missing query parameter: $name")
    }
}
