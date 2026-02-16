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
import org.mongodb.scala.bson.conversions.Bson
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.inheritancetaxonpensions.models.SessionSchemeDetails

import scala.concurrent.{ExecutionContext, Future}

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

@Singleton
class SessionSchemeDetailsRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  clock: Clock
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[SessionSchemeDetails](
      collectionName = "scheme-details",
      mongoComponent = mongoComponent,
      domainFormat = SessionSchemeDetails.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("lastUpdated"),
          IndexOptions()
            .name("lastUpdated")
            .expireAfter(appConfig.authSessionTtl, TimeUnit.SECONDS)
        )
      ),
      extraCodecs = Seq.empty,
      replaceIndexes = true
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

  def get(id: String): Future[Option[SessionSchemeDetails]] =
    keepAlive(id).flatMap { _ =>
      Mdc.preservingMdc {
        collection
          .find(byId(id))
          .headOption()
      }
    }

  def set(sessionSchemeDetails: SessionSchemeDetails): Future[Boolean] = {

    val updated = sessionSchemeDetails.copy(lastUpdated = Instant.now(clock))

    collection
      .replaceOne(
        filter = byId(updated.id),
        replacement = updated,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => true)
  }

  def clear(id: String): Future[Boolean] =
    collection
      .deleteOne(byId(id))
      .toFuture()
      .map(_ => true)
}
