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

package uk.gov.hmrc.inheritancetaxonpensions.config

import play.api.http.Status._

object Constants {

  val psaEnrolmentKey = "HMRC-PODS-ORG"
  val pspEnrolmentKey = "HMRC-PODSPP-ORG"

  val psaId = "psaId"
  val pspId = "pspId"

  val HEADER_KEY_USER_NAME = "userName"
  val HEADER_KEY_SCHEME_NAME = "schemeName"
  val HEADER_KEY_SRN = "srn"
  val HEADER_KEY_REQUEST_ROLE = "requestRole"
  val HEADER_VALUE_PSA = "PSA"
  val HEADER_VALUE_PSP = "PSP"

  val inheritanceTaxReferenceNumberPath = "inheritanceTaxReferenceNumber"

  val TransientErrorStatusCodes: Set[Int] = Set(
    INTERNAL_SERVER_ERROR,
    BAD_GATEWAY,
    SERVICE_UNAVAILABLE,
    GATEWAY_TIMEOUT
  )
}
