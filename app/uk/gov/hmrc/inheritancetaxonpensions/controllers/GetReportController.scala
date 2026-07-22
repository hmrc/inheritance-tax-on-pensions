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

import uk.gov.hmrc.inheritancetaxonpensions.connectors.{IhtpReportConnector, SchemeDetailsConnector}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.http.{ContentTypes, HeaderNames}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.inheritancetaxonpensions.auth.IhtpAuthWithSessionCache
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.services.SessionService
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.http.Status._
import uk.gov.hmrc.http.{BadRequestException, HttpResponse}

import scala.concurrent.ExecutionContext

import javax.inject.{Inject, Singleton}

@Singleton()
class GetReportController @Inject() (
  cc: ControllerComponents,
  override val authConnector: AuthConnector,
  override protected val schemeDetailsConnector: SchemeDetailsConnector,
  override protected val sessionService: SessionService,
  ihtpReportConnector: IhtpReportConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with BaseController
    with IhtpAuthWithSessionCache {

  def getReport: Action[AnyContent] = Action.async { implicit request =>
    val Seq(_, _, srnS, _) =
      requiredHeaders(HEADER_KEY_USER_NAME, HEADER_KEY_SCHEME_NAME, HEADER_KEY_SRN, HEADER_KEY_REQUEST_ROLE)

    authorisedAsIhtpUser(srnS) { _ =>
      val pstr = request.getQueryString("pstr").getOrElse {
        throw new BadRequestException("Bad Request with missing query parameter: pstr")
      }

      ihtpReportConnector
        .getReport(
          pstr,
          request.getQueryString("fbNumber"),
          request.getQueryString("paymentReferenceNumber"),
          request.getQueryString("versionNumber")
        )
        .map(toResult)
    }
  }

  private def toResult(response: HttpResponse) = {
    val result =
      if (noBodyStatuses.contains(response.status) || response.body.isEmpty) {
        Status(response.status)
      } else {
        Status(response.status)(response.body)
          .as(response.header(HeaderNames.CONTENT_TYPE).getOrElse(ContentTypes.JSON))
      }

    response.header("correlationid").fold(result)(correlationId => result.withHeaders("correlationid" -> correlationId))
  }

  private val noBodyStatuses: Set[Int] = Set(UNAUTHORIZED, FORBIDDEN, NOT_FOUND, UNSUPPORTED_MEDIA_TYPE)
}
