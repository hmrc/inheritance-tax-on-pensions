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

import play.api.test.FakeRequest
import uk.gov.hmrc.inheritancetaxonpensions.connectors.SchemeDetailsConnector
import play.api.mvc.{AnyContentAsEmpty, Result}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants
import play.api.http.Status
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, UnauthorizedException}
import uk.gov.hmrc.inheritancetaxonpensions.config.Constants._
import org.mockito.ArgumentMatchers.any
import utils.BaseSpec
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import org.mockito.Mockito._
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.inheritancetaxonpensions.models.Srn
import org.mockito.ArgumentMatchers
import play.api.mvc.Results.Ok

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class IhtpAuthSpec extends BaseSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSchemeDetailsConnector = mock[SchemeDetailsConnector]

  override protected def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockSchemeDetailsConnector)
  }

  private val psaEnrolment = Enrolments(
    Set(
      Enrolment(
        psaEnrolmentKey,
        Seq(
          EnrolmentIdentifier("PSAID", psaId)
        ),
        "Activated",
        None
      )
    )
  )

  private val pspEnrolment = Enrolments(
    Set(
      Enrolment(
        pspEnrolmentKey,
        Seq(
          EnrolmentIdentifier("PSPID", psaId)
        ),
        "Activated",
        None
      )
    )
  )

  private val unknownEnrolment = Enrolments(
    Set(
      Enrolment(
        "unknownEnrolmentId",
        Seq(
          EnrolmentIdentifier("unknownId", psaId)
        ),
        "Activated",
        None
      )
    )
  )

  private implicit val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  private implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(req)

  private val body: IhtpAuthContext[Any] => Future[Result] = _ => Future.successful(Ok)

  val auth: IhtpAuth = new IhtpAuth {
    override val authConnector: AuthConnector = mockAuthConnector
    override protected val schemeDetailsConnector: SchemeDetailsConnector = mockSchemeDetailsConnector
  }

  "authorisedAsIhtpUser" should {

    "fail when srn is not in valid format" in {
      val result = auth.authorisedAsIhtpUser("INVALID_SRN")(body)
      status(result) mustBe Status.BAD_REQUEST
      contentAsString(result) mustEqual "Invalid scheme reference number"
      verify(mockAuthConnector, never).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }

    "fail when it's not possible to authorise as there is empty enrolments and None as externalId" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(None, Enrolments(Set.empty))))

      intercept[UnauthorizedException](Await.result(auth.authorisedAsIhtpUser(srn)(body), Duration.Inf))
      verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }

    "fail when it's not possible to authorise as there is no psp or psa enrolment" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some(externalId), unknownEnrolment)))

      intercept[BadRequestException](Await.result(auth.authorisedAsIhtpUser(srn)(body), Duration.Inf))
      verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }

    "fail when it's not possible to authorise as the scheme is not associated with the user" in {
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some(externalId), psaEnrolment)))
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))

      intercept[BadRequestException](Await.result(auth.authorisedAsIhtpUser(srn)(body), Duration.Inf))
      verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }
  }

  "authorisedAsIhtpUser with a `requestRole` header present" should {
    "throw BadRequestException when request role is not allowed value" in {
      val req: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> "xxx")
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some(externalId), psaEnrolment)))

      intercept[BadRequestException](
        Await.result(auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req), Duration.Inf)
      )
      verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
    }

    "throw UnauthorizedException when it's not possible to authorise PSA as the scheme is not associated with the user" in {
      val req: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA)
      when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(new ~(Some(externalId), psaEnrolment)))
      when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(false))

      intercept[UnauthorizedException](
        Await.result(auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req), Duration.Inf)
      )
      verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
      verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
    }

    "authorisedAsIhtpUser with a `requestRole` header value 'PSA' present" should {
      "throw UnauthorizedException when it's not possible to authorise PSA as the scheme is only associated with the user as a PSP" in {
        val req: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA)
        when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
          .thenReturn(Future.successful(new ~(Some(externalId), psaEnrolment)))
        when(
          mockSchemeDetailsConnector
            .checkAssociation(any(), ArgumentMatchers.eq(Constants.pspId), ArgumentMatchers.eq(Srn(srn).value))(
              any(),
              any()
            )
        ).thenReturn(Future.successful(true))
        when(
          mockSchemeDetailsConnector
            .checkAssociation(any(), ArgumentMatchers.eq(Constants.psaId), ArgumentMatchers.eq(Srn(srn).value))(
              any(),
              any()
            )
        ).thenReturn(Future.successful(false))

        intercept[UnauthorizedException](
          Await.result(auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req), Duration.Inf)
        )
        verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
        verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      }

      "return ok when PSA is associated" in {
        val req: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA)
        when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
          .thenReturn(Future.successful(new ~(Some(externalId), psaEnrolment)))
        when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(true))

        val result = auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req)
        status(result) mustBe Status.OK
        verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
        verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      }

      "throw BadRequest when PsaId is missing from the enrolment" in {
        val req: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSA)
        when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
          .thenReturn(
            Future.successful(
              new ~(
                Some(externalId),
                Enrolments(
                  Set(
                    Enrolment(
                      psaEnrolmentKey,
                      Seq(
                        EnrolmentIdentifier("PSPID", pspId) // deliberately pspId here
                      ),
                      "Activated",
                      None
                    )
                  )
                )
              )
            )
          )
        when(
          mockSchemeDetailsConnector
            .checkAssociation(any(), ArgumentMatchers.eq(Constants.pspId), ArgumentMatchers.eq(Srn(srn).value))(
              any(),
              any()
            )
        ).thenReturn(Future.successful(true))
        intercept[BadRequestException](
          Await.result(auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req), Duration.Inf)
        )
        verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
        verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
      }
    }

    "authorisedAsIhtpUser with a `requestRole` header value 'PSP' present" should {
      "throw UnauthorizedException when it's not possible to authorise PSP as the scheme is not associated with the user" in {
        val req: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSP)
        when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
          .thenReturn(Future.successful(new ~(Some(externalId), pspEnrolment)))
        when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(false))

        intercept[UnauthorizedException](
          Await.result(auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req), Duration.Inf)
        )
        verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
        verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      }

      "throw UnauthorizedException when it's not possible to authorise PSP as the scheme is only associated with the user as a PSA" in {
        val req: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSP)
        when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
          .thenReturn(Future.successful(new ~(Some(externalId), pspEnrolment)))
        when(
          mockSchemeDetailsConnector
            .checkAssociation(any(), ArgumentMatchers.eq(Constants.psaId), ArgumentMatchers.eq(Srn(srn).value))(
              any(),
              any()
            )
        ).thenReturn(Future.successful(true))
        when(
          mockSchemeDetailsConnector
            .checkAssociation(any(), ArgumentMatchers.eq(Constants.pspId), ArgumentMatchers.eq(Srn(srn).value))(
              any(),
              any()
            )
        ).thenReturn(Future.successful(false))

        intercept[UnauthorizedException](
          Await.result(auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req), Duration.Inf)
        )
        verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
        verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      }

      "return ok when PSP is associated" in {
        val req: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSP)
        when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
          .thenReturn(Future.successful(new ~(Some(externalId), pspEnrolment)))
        when(mockSchemeDetailsConnector.checkAssociation(any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(true))

        val result = auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req)
        status(result) mustBe Status.OK
        verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
        verify(mockSchemeDetailsConnector, times(1)).checkAssociation(any(), any(), any())(any(), any())
      }

      "throw BadRequest when PsaId is missing from the enrolment" in {
        val req: FakeRequest[AnyContentAsEmpty.type] =
          FakeRequest().withHeaders(HEADER_KEY_REQUEST_ROLE -> HEADER_VALUE_PSP)
        when(mockAuthConnector.authorise[Option[String] ~ Enrolments](any(), any())(any(), any()))
          .thenReturn(
            Future.successful(
              new ~(
                Some(externalId),
                Enrolments(
                  Set(
                    Enrolment(
                      pspEnrolmentKey,
                      Seq(
                        EnrolmentIdentifier("PSAID", psaId) // deliberately psaId here
                      ),
                      "Activated",
                      None
                    )
                  )
                )
              )
            )
          )
        when(
          mockSchemeDetailsConnector
            .checkAssociation(any(), ArgumentMatchers.eq(Constants.pspId), ArgumentMatchers.eq(Srn(srn).value))(
              any(),
              any()
            )
        ).thenReturn(Future.successful(true))
        intercept[BadRequestException](
          Await.result(auth.authorisedAsIhtpUser(srn)(body)(implicitly, implicitly, req), Duration.Inf)
        )
        verify(mockAuthConnector, times(1)).authorise[Option[String] ~ Enrolments](any(), any())(any(), any())
        verify(mockSchemeDetailsConnector, never).checkAssociation(any(), any(), any())(any(), any())
      }

    }
  }
}
