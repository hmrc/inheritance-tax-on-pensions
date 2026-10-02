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

package uk.gov.hmrc.inheritancetaxonpensions.services

import uk.gov.hmrc.inheritancetaxonpensions.connectors.IhtpReportConnector
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.IndividualOrOrg.{Individual, Organisation}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import models.{IhtpOverviewReport, IhtpOverviewResponse, IhtpOverviewSuccess}
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.inheritancetaxonpensions.repositories.UserAnswersRepository
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.YesNo.Yes
import uk.gov.hmrc.inheritancetaxonpensions.models._
import uk.gov.hmrc.inheritancetaxonpensions.models.etmp.{IndividualOrOrg, YesNo}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants
import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import uk.gov.hmrc.inheritancetaxonpensions.utils.UserAnswersHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.Right

import java.time._

@Singleton
class ReportRetrievalService @Inject() (
  userAnswersRepository: UserAnswersRepository,
  ihtpReportConnector: IhtpReportConnector,
  clock: Clock
) extends Logging {

  def getOverview(pstr: String, srn: String, dateFrom: String, dateTo: String, status: Option[String])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[ErrorResponse, IhtpOverviewResponse]] =
    val downstreamEitherF: Future[Either[ErrorResponse, IhtpOverviewResponse]] =
      ihtpReportConnector.getOverview(pstr, dateFrom, dateTo, status)

    val inProgressF: Future[Seq[UserAnswers]] =
      userAnswersRepository.findBySrn(srn).recover { case e =>
        Seq.empty
      }

    for {
      dsEither <- downstreamEitherF
      inProgress <- inProgressF
    } yield dsEither match {
      case Right(ds) =>
        val latestSubmittedReports =
          IhtpOverviewSuccess.filterForHighestVersion(ds.success.ihtpOverview.toList, Nil).ihtpOverview
        val filtered = inProgress
          .filter(ua =>
            val draftPaymentReference = UserAnswersHelper.getOptional(ua, "ihtPaymentReference").getOrElse("fail")
            val draftLastUpdated = ua.lastUpdated
            !latestSubmittedReports.exists(item =>
              (item.paymentReference.getOrElse("test") == draftPaymentReference) &&
                item.submissionDate
                  .getOrElse(Instant.EPOCH)
                  .isAfter(draftLastUpdated.atZone(ZoneId.of("Europe/Paris")).toInstant)
            )
          )
        val mappedInProgress = filtered
          .map(ua =>
            IhtpOverviewReport(
              uuid = Some(ua.uuid),
              fbNumber = None,
              submissionDate = None,
              paymentDueDate = Some(
                UserAnswersHelper
                  .getOptionalAs[LocalDate](ua, s"${ihtTaxInformationPath}.${noticeToPayDatePath}")
                  .getOrElse(LocalDate.now())
                  .plusDays(dueDateDifferenceInDays)
              ),
              ihtVersion = "000", // fixed value
              inheritanceTaxReference = UserAnswersHelper.getMandatoryAs[String](ua, inheritanceTaxReferenceNumberPath),
              paymentReference = None,
              title = None, // likely not in final version of ETMP payload
              firstForename = UserAnswersHelper.getOptional(ua, s"${nameOfDeceasedPath}.${deceasedFirstForename}"),
              secondForename = None, // likely not in final version of ETMP payload
              surname = Some(
                UserAnswersHelper
                  .getOptional(ua, s"${nameOfDeceasedPath}.${deceasedSurname}")
                  .getOrElse("Enter name") // fallback if only the first page of the journey was saved
              ),
              nino = None,
              ihtpStatus = "In progress" // fixed value
            )
          )
        Right(IhtpOverviewResponse(IhtpOverviewSuccess(mappedInProgress ++ latestSubmittedReports)))
      case Left(e) =>
        Left(e)
    }

  def getReport(
    pstr: String,
    srn: String,
    fbNumber: Option[String],
    ihtPaymentReference: Option[String],
    versionNumber: Option[String]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Either[HttpResponse, (String, String, JsValue)]] =
    ihtpReportConnector
      .getReport(pstr, fbNumber, ihtPaymentReference, versionNumber)
      .flatMap { response =>
        if (response.status >= 400) {
          Future.successful(Left(response))
        } else {
          val ihtNoticeResponse =
            response.json.as[IhtpPaymentNoticeRetrievalResponse]

          setUserAnswers(ihtNoticeResponse.ihtNoticeResponse, srn).map { case (uuid, userAnswersJson) =>
            Right(
              uuid,
              response.header("correlationid").getOrElse(""),
              userAnswersJson
            )
          }
        }
      }

  private def setUserAnswers(response: IhtNoticeResponse, srn: String)(implicit
    ec: ExecutionContext
  ): Future[(String, JsValue)] =
    val paymentReference = response.reportDetails.ihtPaymentReference

    val data = buildData(response)

    userAnswersRepository
      .getByPaymentReference(paymentReference)
      .map {
        case Some(existingUserAnswers) =>
          logger.info(s"UserAnswers already exist for payment reference: $paymentReference, srn: $srn")
          UserAnswersHelper.set(
            existingUserAnswers,
            JsPath \ "ihtVersion",
            Some(response.reportDetails.ihtVersion)
          )

          val userAnswers = existingUserAnswers.copy(
            data = data
          )

          (userAnswers.uuid, Json.toJson(userAnswers))
        case None =>
          val uuid = java.util.UUID.randomUUID().toString
          logger.info(s"Creating new UserAnswers for payment reference: $paymentReference, srn: $srn, uuid: $uuid")
          val userAnswers =
            UserAnswers(
              id = s"$srn-$uuid",
              uuid = uuid,
              srn = srn,
              lastUpdated = Instant.now(clock),
              data = data
            )
          userAnswersRepository.set(userAnswers)
          (uuid, Json.toJson(userAnswers))
      }

  private def buildData(response: IhtNoticeResponse): JsObject =
    Json
      .toJson(
        UserAnswersModel(
          inheritanceTaxReference = Some(response.reportDetails.ihtPaymentReference),
          nameOfDeceased = Some(
            NameOfDeceased(
              firstForename = response.deceased.deceasedPersonalDetails.firstForename,
              surname = response.deceased.deceasedPersonalDetails.surname
            )
          ),
          hasNino = Some(response.deceased.deceasedPersonalDetails.ninoExist == YesNo.Yes),
          nino = response.deceased.deceasedPersonalDetails.nino,
          reasonNoNino = response.deceased.deceasedPersonalDetails.reasonNoNino,
          birthDeathDates = Some(
            BirthDeathDatesAnswers(
              dateOfBirth = response.deceased.deceasedDetails.deceasedsDob,
              dateOfDeath = response.deceased.deceasedDetails.deceasedsDod
            )
          ),
          didPrSubmit = Some(response.ihTaxInformation.noticeSubmittedByPr == Yes),
          ihtTaxInformation = Some(
            IhTaxInformationAnswers(
              dateThePensionSchemeReceivedNoticeToPay = response.ihTaxInformation.dateNoticeReceived
            )
          ),
          areBeneficiariesKnown = Some(response.ihTaxInformation.knownBeneficiaries == Yes),
          prType = Some(response.personalRep.typeOfPr.name),
          prDetails = Some(buildPrDetails(response)),
          ihtPaymentReference = Some(response.reportDetails.ihtPaymentReference),
          ihtVersion = Some(response.reportDetails.ihtVersion)
        )
      )
      .as[JsObject] // TODO update beneficiary once pages are complete

  private def buildPrDetails(response: IhtNoticeResponse): PrDetailsAnswers =
    response.personalRep.typeOfPr match {
      case Individual =>
        PrDetailsAnswers(
          individual = Some(
            IndividualDetailsAnswers(
              title = response.personalRep.prContactDetails.title,
              firstForename = response.personalRep.prContactDetails.firstForename,
              secondForename = response.personalRep.prContactDetails.secondForename,
              surname = response.personalRep.prContactDetails.surname,
              addressLine1 = Some(response.personalRep.prContactDetails.prAddress.addressLine1),
              addressLine2 = response.personalRep.prContactDetails.prAddress.addressLine2,
              addressLine3 = response.personalRep.prContactDetails.prAddress.addressLine3,
              addressLine4 = response.personalRep.prContactDetails.prAddress.addressLine4,
              postCode = response.personalRep.prContactDetails.prAddress.postCode,
              country = Some(response.personalRep.prContactDetails.prAddress.country)
            )
          ),
          organisation = None
        )
      case Organisation =>
        PrDetailsAnswers(
          individual = None,
          organisation = Some(
            OrganisationDetailsAnswers(
              organisationName = response.personalRep.prContactDetails.orgName.get,
              title = response.personalRep.prContactDetails.title,
              firstForename = response.personalRep.prContactDetails.firstForename,
              secondForename = response.personalRep.prContactDetails.secondForename,
              surname = response.personalRep.prContactDetails.surname,
              addressLine1 = Some(response.personalRep.prContactDetails.prAddress.addressLine1),
              addressLine2 = response.personalRep.prContactDetails.prAddress.addressLine2,
              addressLine3 = response.personalRep.prContactDetails.prAddress.addressLine3,
              addressLine4 = response.personalRep.prContactDetails.prAddress.addressLine4,
              postCode = response.personalRep.prContactDetails.prAddress.postCode,
              country = Some(response.personalRep.prContactDetails.prAddress.country)
            )
          )
        )
    }
}
