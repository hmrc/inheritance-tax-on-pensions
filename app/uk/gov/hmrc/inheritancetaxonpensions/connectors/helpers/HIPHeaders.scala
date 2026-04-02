/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.inheritancetaxonpensions.connectors.helpers

import uk.gov.hmrc.inheritancetaxonpensions.config.AppConfig
import play.api.http.HeaderNames
import uk.gov.hmrc.inheritancetaxonpensions.utils.DateTimeHelper

import java.time.{Clock, Instant}
import java.util.Base64
import javax.inject.Inject

class HIPHeaders @Inject()(randomUUIDGenerator: RandomUUIDGenerator, appConfig: AppConfig, clock: Clock) {
  private val correlationIdHeader: String       = "correlationId"
  private val xOriginatingSystemHeader: String  = "X-Originating-System"
  private val xReceiptDateHeader: String        = "X-Receipt-Date"
  private val xTransmittingSystemHeader: String = "X-Transmitting-System"

  private val mdtp = "MDTP"
  private val hip  = "HIP"

  // TODO check these headers against the EPIDs when we get them!
  def ihtpReportSubmissionHeaders(): Seq[(String, String)] =
    Seq(
      (HeaderNames.AUTHORIZATION, authorizationForIhtpReportSubmission()),
      (correlationIdHeader, randomUUIDGenerator.uuid),
      (xOriginatingSystemHeader, mdtp),
      (xReceiptDateHeader, DateTimeHelper.formatISOInstantSeconds(Instant.now(clock))),
      (xTransmittingSystemHeader, hip)
    )

  private def authorizationForIhtpReportSubmission(): String = {
    val clientId = appConfig.submitIhtpReportClientId
    val secret   = appConfig.submitIhtpReportSecret

    val encoded = Base64.getEncoder.encodeToString(s"$clientId:$secret".getBytes("UTF-8"))
    s"Basic $encoded"
  }
}
