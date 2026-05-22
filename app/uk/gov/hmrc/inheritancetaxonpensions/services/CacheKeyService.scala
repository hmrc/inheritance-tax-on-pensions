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

import uk.gov.hmrc.inheritancetaxonpensions.connectors.helpers.RandomUUIDGenerator
import uk.gov.hmrc.inheritancetaxonpensions.models.Srn

import javax.inject.{Inject, Singleton}

@Singleton
class CacheKeyService @Inject() (
  randomUUIDGenerator: RandomUUIDGenerator
) {

  def generateCacheKey(srn: String): String = {
    val uuid = randomUUIDGenerator.uuid
    s"$srn-$uuid"
  }

  def parseCacheKey(cacheKey: String): Option[(String, String)] =
    cacheKey.split("-", 2) match {
      case Array(srnPart, uuidPart) if isValidSrn(srnPart) && isValidUuid(uuidPart) =>
        Some((srnPart, uuidPart))
      case _ => None
    }

  def validateCacheKey(cacheKey: String): Boolean =
    parseCacheKey(cacheKey).isDefined

  def extractSrn(cacheKey: String): Option[String] =
    parseCacheKey(cacheKey).map(_._1)

  def extractUuid(cacheKey: String): Option[String] =
    parseCacheKey(cacheKey).map(_._2)

  private def isValidSrn(srn: String): Boolean =
    Srn(srn).isDefined

  private def isValidUuid(uuid: String): Boolean =
    uuid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}
