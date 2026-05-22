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

import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.inheritancetaxonpensions.connectors.helpers.RandomUUIDGenerator
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito.when

class CacheKeyServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  private val mockUUIDGenerator = mock[RandomUUIDGenerator]
  private val cacheKeyService = new CacheKeyService(mockUUIDGenerator)

  "CacheKeyService" - {

    "generateCacheKey" - {

      "must generate a cache key with SRN and UUID" in {
        val testSrn = "S2400000001"
        val testUuid = "ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
        when(mockUUIDGenerator.uuid).thenReturn(testUuid)

        val result = cacheKeyService.generateCacheKey(testSrn)
        result mustBe s"$testSrn-$testUuid"
      }
    }

    "parseCacheKey" - {

      "must parse a valid cache key" in {
        val testSrn = "S2400000001"
        val testUuid = "ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
        val cacheKey = s"$testSrn-$testUuid"

        val result = cacheKeyService.parseCacheKey(cacheKey)
        result mustBe Some((testSrn, testUuid))
      }

      "must return None for invalid SRN format" in {
        val cacheKey = "INVALID-ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
        val result = cacheKeyService.parseCacheKey(cacheKey)
        result mustBe None
      }

      "must return None for invalid UUID format" in {
        val cacheKey = "S2400000001-invalid-uuid"
        val result = cacheKeyService.parseCacheKey(cacheKey)
        result mustBe None
      }

      "must return None for cache key without hyphen" in {
        val cacheKey = "S2400000001"
        val result = cacheKeyService.parseCacheKey(cacheKey)
        result mustBe None
      }

      "must return None for empty cache key" in {
        val result = cacheKeyService.parseCacheKey("")
        result mustBe None
      }
    }

    "validateCacheKey" - {

      "must return true for valid cache key" in {
        val testSrn = "S2400000001"
        val testUuid = "ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
        val cacheKey = s"$testSrn-$testUuid"

        val result = cacheKeyService.validateCacheKey(cacheKey)
        result mustBe true
      }

      "must return false for invalid cache key" in {
        val result = cacheKeyService.validateCacheKey("invalid-key")
        result mustBe false
      }
    }

    "extractSrn" - {

      "must extract SRN from valid cache key" in {
        val testSrn = "S2400000001"
        val testUuid = "ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
        val cacheKey = s"$testSrn-$testUuid"

        val result = cacheKeyService.extractSrn(cacheKey)
        result mustBe Some(testSrn)
      }

      "must return None for invalid cache key" in {
        val result = cacheKeyService.extractSrn("invalid-key")
        result mustBe None
      }
    }

    "extractUuid" - {

      "must extract UUID from valid cache key" in {
        val testSrn = "S2400000001"
        val testUuid = "ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
        val cacheKey = s"$testSrn-$testUuid"

        val result = cacheKeyService.extractUuid(cacheKey)
        result mustBe Some(testUuid)
      }

      "must return None for invalid cache key" in {
        val result = cacheKeyService.extractUuid("invalid-key")
        result mustBe None
      }
    }
  }
}
