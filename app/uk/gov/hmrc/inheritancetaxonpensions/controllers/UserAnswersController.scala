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
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers
import uk.gov.hmrc.inheritancetaxonpensions.services.{CacheKeyService, SessionService}
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.Logging
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton()
class UserAnswersController @Inject() (
  val userAnswersRepository: UserAnswersRepository,
  cacheKeyService: CacheKeyService,
  cc: ControllerComponents,
  override val authConnector: AuthConnector,
  override protected val schemeDetailsConnector: SchemeDetailsConnector,
  override protected val sessionService: SessionService
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with IhtpAuthWithSessionCache
    with BaseController
    with Logging {

  def fetch(id: String): Action[AnyContent] = Action.async { implicit request =>
    val Seq(userName, schemeName, srnS, requestRole) =
      requiredHeaders(HEADER_KEY_USER_NAME, HEADER_KEY_SCHEME_NAME, HEADER_KEY_SRN, HEADER_KEY_REQUEST_ROLE)

    if (!cacheKeyService.validateCacheKey(id)) {
      Future.successful(NotFound)
    } else if (!cacheKeyService.extractSrn(id).contains(srnS)) {
      Future.successful(NotFound)
    } else {
      authorisedAsIhtpUser(srnS) { _ =>
        userAnswersRepository.get(id).map {
          case Some(ua) => Ok(Json.toJson(ua))
          case None => NotFound
        }
      }
    }
  }

  def set(): Action[AnyContent] = Action.async { implicit request =>
    val Seq(userName, schemeName, srnS, requestRole) =
      requiredHeaders(HEADER_KEY_USER_NAME, HEADER_KEY_SCHEME_NAME, HEADER_KEY_SRN, HEADER_KEY_REQUEST_ROLE)
    val userAnswers = requiredBody.as[UserAnswers]

    val finalUserAnswers =
      if (
        cacheKeyService.validateCacheKey(userAnswers.id) &&
        cacheKeyService.extractSrn(userAnswers.id).contains(srnS) &&
        userAnswers.srn == srnS
      ) {
        userAnswers
      } else {
        val generatedCacheKey = cacheKeyService.generateCacheKey(srnS)
        val generatedUuid = cacheKeyService.extractUuid(generatedCacheKey).getOrElse("")
        userAnswers.copy(id = generatedCacheKey, srn = srnS, uuid = generatedUuid)
      }

    authorisedAsIhtpUser(srnS) { _ =>
      userAnswersRepository.set(finalUserAnswers).map {
        case true => Ok(Json.toJson(finalUserAnswers))
        case _ => InternalServerError("Failed to save the user answers")
      }
    }
  }
}
