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
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers
import uk.gov.hmrc.inheritancetaxonpensions.services.{ReportSubmissionService, SessionService}
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.Logging
import play.api.libs.json.{JsPath, Json}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.utils.UserAnswersHelper

import scala.language.postfixOps
import scala.concurrent.{ExecutionContext, Future}

import java.time.Instant
import javax.inject.{Inject, Singleton}

@Singleton
class ReportSubmissionController @Inject() (
  val reportSubmissionService: ReportSubmissionService,
  cc: ControllerComponents,
  override val authConnector: AuthConnector,
  override protected val schemeDetailsConnector: SchemeDetailsConnector,
  override protected val sessionService: SessionService,
  val userAnswersRepository: UserAnswersRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with IhtpAuthWithSessionCache
    with BaseController
    with Logging {

  def submitReport(pstr: String, userAnswersId: String): Action[AnyContent] = Action.async { implicit request =>
    val Seq(userName, schemeName, srnS, requestRole) =
      requiredHeaders(HEADER_KEY_USER_NAME, HEADER_KEY_SCHEME_NAME, HEADER_KEY_SRN, HEADER_KEY_REQUEST_ROLE)

    authorisedAsIhtpUser(srnS) { _ =>
      reportSubmissionService.submitReport(userAnswersId, pstr).map {
        case Right(submissionResponse) =>
          val uaOptionF = userAnswersRepository.get(userAnswersId)
          uaOptionF.map {
            // this might be replaced eventually with the removal of the user answer after submission
            // added for now to try out logic of displaying drafts on submission list only
            // if the draft is "newer" than the submission
            case Some(ua0) =>
              for {
                ua1 <- Future.fromTry(
                  UserAnswersHelper.set(ua0, JsPath \ "processingDate", Instant.now())
                )
                ua2 <- Future.fromTry(
                  UserAnswersHelper.set(ua1, JsPath \ "ihtPaymentReference", submissionResponse.paymentReference)
                )
                ua3 <- Future.fromTry(
                  UserAnswersHelper.set(ua2, JsPath \ "formBundleNo", submissionResponse.formBundleNumber)
                )
                _ <- userAnswersRepository.set(ua3)
              } yield ()
            case None =>
              logger.warn(
                s"[ReportSubmissionController][submitReport] Unable to save submit response"
              )
          }
          Ok(Json.toJson(submissionResponse))
        case Left(errorResponse) =>
          logger.warn(
            s"[ReportSubmissionController][submitReport] Report submission failed for pstr $pstr: ${errorResponse.message}"
          )
          Status(errorResponse.statusCode)(Json.toJson(errorResponse))
      }
    }
  }
}
