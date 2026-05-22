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
import play.api.libs.json.Json
import uk.gov.hmrc.inheritancetaxonpensions.config.AppConfig
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers
import uk.gov.hmrc.inheritancetaxonpensions.services.EncryptionService
import uk.gov.hmrc.mdc.MdcExecutionContext
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.{ExecutionContext, Future}

class UserAnswersRepositorySpec
    extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UserAnswers]
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with MockitoSugar {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val testSrn = "S2400000001"
  private val testUuid = "ed350bdc-4010-406c-9ca0-8faaf5f93cbc"
  private val userAnswers =
    UserAnswers(s"$testSrn-$testUuid", testSrn, testUuid, Json.obj("foo" -> "bar"), Instant.ofEpochSecond(1))

  private val mockAppConfig = mock[AppConfig]
  private val mockEncryptionService = mock[EncryptionService]
  when(mockAppConfig.userAnswersTtl).`thenReturn`(100L)

  implicit val productionLikeTestMdcExecutionContext: ExecutionContext = MdcExecutionContext()

  override protected val repository: UserAnswersRepository = new UserAnswersRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig,
    clock = stubClock,
    encryptionService = mockEncryptionService
  )

  ".set" - {

    "must set the last updated time on the supplied user answers to `now`, and save them" in {

      val expectedResult = userAnswers.copy(lastUpdated = instant)

      repository.set(userAnswers).futureValue
      val updatedRecord = find(Filters.equal("_id", userAnswers.id)).futureValue.headOption.value

      updatedRecord.mustEqual(expectedResult)
    }

    mustPreserveMdc(repository.set(userAnswers))
  }

  ".get" - {

    "when there is a record for this id" - {

      "must update the lastUpdated time and get the record" in {

        insert(userAnswers).futureValue

        val result = repository.get(userAnswers.id).futureValue
        val expectedResult = userAnswers.copy(lastUpdated = instant)

        result.value.mustEqual(expectedResult)
      }
    }

    "when there is no record for this id" - {

      "must return None" in {

        repository.get("id that does not exist").futureValue must not be defined
      }
    }

    mustPreserveMdc(repository.get(userAnswers.id))
  }

  ".clear" - {

    "must remove a record" in {

      insert(userAnswers).futureValue

      repository.clear(userAnswers.id).futureValue

      repository.get(userAnswers.id).futureValue must not be defined
    }

    "must return true when there is no record to remove" in {
      val result = repository.clear("id that does not exist").futureValue

      result mustEqual true
    }

    mustPreserveMdc(repository.clear(userAnswers.id))
  }

  ".keepAlive" - {

    "when there is a record for this id" - {

      "must update its lastUpdated to `now` and return true" in {

        insert(userAnswers).futureValue

        repository.keepAlive(userAnswers.id).futureValue

        val expectedUpdatedAnswers = userAnswers.copy(lastUpdated = instant)

        val updatedAnswers = find(Filters.equal("_id", userAnswers.id)).futureValue.headOption.value
        updatedAnswers.mustEqual(expectedUpdatedAnswers)
      }
    }

    "when there is no record for this id" - {

      "must return true" in {

        repository.keepAlive("id that does not exist").futureValue mustEqual true
      }
    }

    mustPreserveMdc(repository.keepAlive(userAnswers.id))
  }

  ".findBySrn" - {

    "must return all user answers for a given SRN" in {
      val testSrn2 = "S2400000002"
      val testUuid2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
      val userAnswers2 =
        UserAnswers(s"$testSrn2-$testUuid2", testSrn2, testUuid2, Json.obj("foo" -> "bar"), Instant.ofEpochSecond(2))

      insert(userAnswers).futureValue
      insert(userAnswers2).futureValue

      val result = repository.findBySrn(testSrn).futureValue
      result must have size 1
      result.head.srn mustBe testSrn
    }

    "must return empty list when no user answers exist for SRN" in {
      val result = repository.findBySrn("S9999999999").futureValue
      result mustBe empty
    }

    "must return multiple user answers for the same SRN with different UUIDs" in {
      val testUuid2 = "cccccccc-cccc-cccc-cccc-cccccccccccc"
      val userAnswers2 =
        UserAnswers(s"$testSrn-$testUuid2", testSrn, testUuid2, Json.obj("foo" -> "bar"), Instant.ofEpochSecond(2))

      insert(userAnswers).futureValue
      insert(userAnswers2).futureValue

      val result = repository.findBySrn(testSrn).futureValue
      result must have size 2
      result.map(_.srn).distinct mustBe Seq(testSrn)
      result.map(_.uuid).distinct must have size 2
    }

    mustPreserveMdc(repository.findBySrn(testSrn))
  }

  private def mustPreserveMdc[A](f: => Future[A])(implicit pos: Position): Unit =
    "must preserve MDC" in {

      MDC.put("test", "foo")

      f.map { _ =>
        Option(MDC.get("test"))
      }.futureValue mustBe Some("foo")
    }
}
