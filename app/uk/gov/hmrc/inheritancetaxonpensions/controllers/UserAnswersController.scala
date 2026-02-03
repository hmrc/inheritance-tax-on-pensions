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
import uk.gov.hmrc.inheritancetaxonpensions.auth.IhtpAuth
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.Logging
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton()
class UserAnswersController @Inject()(
  cc: ControllerComponents,
  override val authConnector: AuthConnector,
  override protected val schemeDetailsConnector: SchemeDetailsConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with IhtpAuth
    with BaseController
    with Logging {

  // TODO - get this working with auth
  def fetch(id: String): Action[AnyContent] = Action.async { implicit request =>
    val Seq(srnS) =
      requiredHeaders(HEADER_KEY_SRN)
    authorisedAsIhtpUser(srnS) { _ =>
      logger.info(s"Fetching user answers for id: $id and srn: $srnS")
      // TODO play ticket IHTP-166 to implement the persistence (repository and models)
      Future.successful(NotFound)
    }
  }

  def set(): Action[AnyContent] = Action.async { implicit request =>
    val Seq(srnS) =
      requiredHeaders(HEADER_KEY_SRN)
    authorisedAsIhtpUser(srnS) { _ =>
      // TODO play ticket IHTP-166 to implement the persistence (repository and models)
      logger.info(s"Setting user answers: ${request.body} and srn$srnS")
      Future.successful(Ok)
    }
  }
}
