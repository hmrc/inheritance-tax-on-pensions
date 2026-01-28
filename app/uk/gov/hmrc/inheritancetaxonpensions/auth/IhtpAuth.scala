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

package uk.gov.hmrc.inheritancetaxonpensions.auth

import uk.gov.hmrc.inheritancetaxonpensions.connectors.SchemeDetailsConnector
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{~, Retrieval}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import play.api.Logging
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.models.Srn
import play.api.mvc.Results.BadRequest

import scala.concurrent.{ExecutionContext, Future}

final case class IhtpAuthContext[A](
  externalId: String,
  psaPspId: String,
  credentialRole: String,
  request: Request[A]
)

trait IhtpAuth extends AuthorisedFunctions with Logging {

  protected val schemeDetailsConnector: SchemeDetailsConnector
  private val predicate = Enrolment(psaEnrolmentKey).or(Enrolment(pspEnrolmentKey))
  private val retrievals: Retrieval[Option[String] ~ Enrolments] =
    Retrievals.externalId.and(Retrievals.allEnrolments)

  private type IhtpAction[A] = IhtpAuthContext[A] => Future[Result]

  def authorisedAsIhtpUser(srnS: String)(
    body: IhtpAction[Any]
  )(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[?]
  ): Future[Result] =
    authorisedUser(srnS)(body)

  private def authorisedUser[A](srnS: String)(
    block: IhtpAction[A]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[A]): Future[Result] =
    Srn(srnS) match {
      case Some(srn) =>
        authorised(predicate)
          .retrieve(retrievals) {
            case Some(externalId) ~ enrolments =>
              request.headers.get(HEADER_KEY_REQUEST_ROLE).map(_.toUpperCase()) match {
                case Some(HEADER_VALUE_PSA) =>
                  checkPsa(srn, externalId, enrolments)(block)
                case Some(HEADER_VALUE_PSP) =>
                  checkPsp(srn, externalId, enrolments)(block)
                case None =>
                  Future.failed(new BadRequestException(s"Bad Request invalid $HEADER_KEY_REQUEST_ROLE header value"))
                case _ =>
                  Future.failed(new BadRequestException(s"Bad Request invalid $HEADER_KEY_REQUEST_ROLE header value"))
              }
            case _ =>
              Future.failed(new UnauthorizedException("Not Authorised - Unable to retrieve credentials - externalId"))
          }
      case _ => Future.successful(BadRequest("Invalid scheme reference number"))
    }

  private def checkPsa[A](srn: Srn, externalId: String, enrolments: Enrolments)(
    block: IhtpAction[A]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[A]): Future[Result] =
    getPsaId(enrolments) match {
      case Some(id) =>
        schemeDetailsConnector.checkAssociation(id, psaId, srn).flatMap {
          case true => block(IhtpAuthContext(externalId, id, psaId, request))
          case false =>
            Future
              .failed(
                new UnauthorizedException("Not Authorised - scheme is not associated with the PSA")
              )
        }
      case psa =>
        Future.failed(new BadRequestException("Bad Request - no PsaId in the enrolment"))
    }

  private def checkPsp[A](srn: Srn, externalId: String, enrolments: Enrolments)(
    block: IhtpAction[A]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[A]): Future[Result] =
    getPspId(enrolments) match {
      case Some(id) =>
        schemeDetailsConnector.checkAssociation(id, pspId, srn).flatMap {
          case true => block(IhtpAuthContext(externalId, id, pspId, request))
          case false =>
            Future
              .failed(
                new UnauthorizedException("Not Authorised - scheme is not associated with the PSP")
              )
        }
      case psp =>
        Future.failed(new BadRequestException("Bad Request - no PspId in the enrolment"))
    }

  private def getPsaId(enrolments: Enrolments): Option[String] =
    enrolments
      .getEnrolment(psaEnrolmentKey)
      .flatMap(_.getIdentifier(psaId.toUpperCase))
      .map(_.value)

  private def getPspId(enrolments: Enrolments): Option[String] =
    enrolments
      .getEnrolment(pspEnrolmentKey)
      .flatMap(_.getIdentifier(pspId.toUpperCase))
      .map(_.value)
}
