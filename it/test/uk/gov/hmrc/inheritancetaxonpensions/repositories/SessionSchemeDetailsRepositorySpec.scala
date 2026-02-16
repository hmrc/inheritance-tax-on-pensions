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

package uk.gov.hmrc.inheritancetaxonpensions.repositories

import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalactic.source.Position
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.slf4j.MDC
import uk.gov.hmrc.inheritancetaxonpensions.config.AppConfig
import uk.gov.hmrc.inheritancetaxonpensions.models.SessionSchemeDetails
import uk.gov.hmrc.mdc.MdcExecutionContext
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

class SessionSchemeDetailsRepositorySpec
    extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[SessionSchemeDetails]
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val sessionSchemeDetails: SessionSchemeDetails =
    SessionSchemeDetails("id", "psp", "srn01", true, Instant.ofEpochSecond(1))

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.authSessionTtl).`thenReturn`(1L)

  implicit val productionLikeTestMdcExecutionContext: ExecutionContext = MdcExecutionContext()

  override protected val repository: SessionSchemeDetailsRepository = new SessionSchemeDetailsRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig,
    clock = stubClock
  )

  ".set" - {

    "must set the last updated time on the cache to `now`, and save them" in {

      val expectedResult = sessionSchemeDetails.copy(lastUpdated = instant)

      repository.set(sessionSchemeDetails).futureValue
      val updatedRecord = find(Filters.equal("_id", sessionSchemeDetails.id)).futureValue.headOption.value

      updatedRecord.mustEqual(expectedResult)
    }

    mustPreserveMdc(repository.set(sessionSchemeDetails))
  }

  ".get" - {

    "when there is a record for this id" - {

      "must update the lastUpdated time and get the record" in {

        insert(sessionSchemeDetails).futureValue

        val result = repository.get(sessionSchemeDetails.id).futureValue
        val expectedResult = sessionSchemeDetails.copy(lastUpdated = instant)

        result.value.mustEqual(expectedResult)
      }
    }

    "when there is no record for this id" - {

      "must return None" in {

        repository.get("id that does not exist").futureValue must not be defined
      }
    }

    mustPreserveMdc(repository.get(sessionSchemeDetails.id))
  }

  ".clear" - {

    "must remove a record" in {

      insert(sessionSchemeDetails).futureValue

      repository.clear(sessionSchemeDetails.id).futureValue

      repository.get(sessionSchemeDetails.id).futureValue must not be defined
    }

    "must return true when there is no record to remove" in {
      val result = repository.clear("id that does not exist").futureValue

      result mustEqual true
    }

    mustPreserveMdc(repository.clear(sessionSchemeDetails.id))
  }

  ".keepAlive" - {

    "when there is a record for this id" - {

      "must update its lastUpdated to `now` and return true" in {

        insert(sessionSchemeDetails).futureValue

        repository.keepAlive(sessionSchemeDetails.id).futureValue

        val expectedUpdatedAnswers = sessionSchemeDetails.copy(lastUpdated = instant)

        val updatedAnswers = find(Filters.equal("_id", sessionSchemeDetails.id)).futureValue.headOption.value
        updatedAnswers.mustEqual(expectedUpdatedAnswers)
      }
    }

    "when there is no record for this id" - {

      "must return true" in {

        repository.keepAlive("id that does not exist").futureValue mustEqual true
      }
    }

    mustPreserveMdc(repository.keepAlive(sessionSchemeDetails.id))
  }

  private def mustPreserveMdc[A](f: => Future[A])(implicit pos: Position): Unit =
    "must preserve MDC" in {

      MDC.put("test", "foo")

      (f.map { _ =>
        Option(MDC.get("test"))
      }.futureValue) mustEqual Some("foo")
    }
}
