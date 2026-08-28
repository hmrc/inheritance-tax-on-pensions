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

package uk.gov.hmrc.inheritancetaxonpensions.validators

import com.networknt.schema._
import com.google.inject.{Inject, Singleton}
import com.fasterxml.jackson.databind.ObjectMapper
import play.api.libs.json._
import com.networknt.schema

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.io.Source

case class SchemaValidationResult(errors: Set[Error]) {
  override def toString: String =
    errors.map(error => s" ${error.getInstanceLocation.toString}: ${error.getMessage}").mkString

  def hasErrors: Boolean = errors.nonEmpty
}

@Singleton()
class JSONSchemaValidator @Inject() {

  def validatePayload(jsonSchemaPath: String, data: JsValue): SchemaValidationResult = {
    val objectMapper: ObjectMapper = new ObjectMapper()

    def loadSchema(path: String): Schema = {
      val stream = getClass.getResourceAsStream(path)
      val schemaText = Source.fromInputStream(stream).mkString
      stream.close()

      val schemaNode = objectMapper.readTree(schemaText)
      SchemaRegistry
        .withDefaultDialect(SpecificationVersion.DRAFT_4)
        .getSchema(schemaNode)
    }

    val schema = loadSchema(jsonSchemaPath)
    val jsonNode = objectMapper.readTree(data.toString())

    val errors = schema.validate(jsonNode).asScala.toSet
    SchemaValidationResult(errors)
  }
}
