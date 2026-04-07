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

package uk.gov.hmrc.inheritancetaxonpensions.config

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.Configuration

import scala.concurrent.duration.Duration

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  private[config] def getConfStringAndThrowIfNotFound(key: String) =
    servicesConfig.getConfString(key, throw new RuntimeException(s"Could not find services config key '$key'"))

  private val pensionsSchemeURL: String = servicesConfig.baseUrl(serviceName = "pensionsScheme")
  val isPsaAssociatedUrl: String = s"$pensionsSchemeURL${config.get[String](path = "serviceUrls.is-psa-associated")}"

  // IHTP Report
  lazy val submitReportUrl: String =
    s"$ihtpReportHost$submitIhtpReportUrl"

  private val ihtpReportHost: String = servicesConfig.baseUrl("ihtp-report")
  lazy val ihtpReportClientId: String = getConfStringAndThrowIfNotFound("ihtp-report.clientId")
  lazy val ihtpReportSecret: String = getConfStringAndThrowIfNotFound("ihtp-report.secret")

  private lazy val submitIhtpReportUrl: String = getConfStringAndThrowIfNotFound(
    "ihtp-report.url.submitReport"
  )
  // IHTP Report End

  val ifsTimeout: Duration = config.get[Duration]("ifs.timeout")
  val userAnswersTtl: Long = config.get[Int]("mongodb.userAnswersTtl")
  val authSessionTtl: Long = config.get[Int]("mongodb.authSessionTtl")
}
