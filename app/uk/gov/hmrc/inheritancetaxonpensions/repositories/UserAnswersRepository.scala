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

import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.inheritancetaxonpensions.config.AppConfig
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.inheritancetaxonpensions.services.EncryptionService
import org.mongodb.scala.bson.conversions.Bson
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.inheritancetaxonpensions.models.UserAnswers

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

@Singleton
class UserAnswersRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  clock: Clock,
  encryptionService: EncryptionService
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[UserAnswers](
      collectionName = "user-answers",
      mongoComponent = mongoComponent,
      domainFormat = UserAnswers.encryptedFormat(encryptionService),
      indexes = Seq(
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("lastUpdatedIdx")
            .expireAfter(appConfig.userAnswersTtl, TimeUnit.SECONDS)
        ),
        IndexModel(
          Indexes.ascending("srn"),
          IndexOptions()
            .name("srnIdx")
        ),
        IndexModel(
          Indexes.ascending("uuid"),
          IndexOptions()
            .name("uuidIdx")
        )
      )
    ) {

  implicit val instantFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  private def byId(id: String): Bson = Filters.equal("_id", id)

  def keepAlive(id: String): Future[Boolean] =
    collection
      .updateOne(
        filter = byId(id),
        update = Updates.set("lastUpdated", Instant.now(clock))
      )
      .toFuture()
      .map(_ => true)

  def get(id: String): Future[Option[UserAnswers]] =
    keepAlive(id).flatMap { _ =>
      Mdc.preservingMdc {
        collection
          .find(byId(id))
          .headOption()
      }
    }

  def set(answers: UserAnswers): Future[Boolean] = {

    val updatedAnswers = answers.copy(lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byId(updatedAnswers.id),
        replacement = updatedAnswers,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => true)
  }

  def findBySrn(srn: String): Future[Seq[UserAnswers]] =
    collection
      .find(Filters.equal("srn", srn))
      .toFuture()

  def clear(id: String): Future[Boolean] =
    collection
      .deleteOne(byId(id))
      .toFuture()
      .map(_ => true)
}
