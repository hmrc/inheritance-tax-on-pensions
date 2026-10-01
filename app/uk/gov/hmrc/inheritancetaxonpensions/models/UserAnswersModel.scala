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

package uk.gov.hmrc.inheritancetaxonpensions.models

import play.api.libs.json._


case class UserAnswersModel(
    inheritanceTaxReference: Option[String],
    nameOfDeceased: Option[NameOfDeceased],
    hasNino: Option[Boolean],
    nino: Option[String],
    reasonForNoNino: Option[String],
    birthDeathDates: Option[BirthDeathDatesAnswers],
    didPrSubmit: Option[Boolean],
    ihtTaxInformation: Option[IhTaxInformationAnswers],
    areBeneficiariesKnown: Option[Boolean],
    prType: Option[String],
    prDetails: Option[PrDetailsAnswers]
)

object UserAnswersModel {
  implicit val format: OFormat[UserAnswersModel] = Json.format[UserAnswersModel]
}

case class NameOfDeceased(
    firstForename: String,
    surname: String
)

object NameOfDeceased {
  implicit val format: OFormat[NameOfDeceased] = Json.format[NameOfDeceased]
}

case class BirthDeathDatesAnswers(
    dateOfBirth: String,
    dateOfDeath: String
)

object BirthDeathDatesAnswers {
  implicit val format: OFormat[BirthDeathDatesAnswers] = Json.format[BirthDeathDatesAnswers]
}

case class IhTaxInformationAnswers(
    dateThePensionSchemeReceivedNoticeToPay: String
)

object IhTaxInformationAnswers {
  implicit val format: OFormat[IhTaxInformationAnswers] = Json.format[IhTaxInformationAnswers]
}

case class PrDetailsAnswers(
    individual: Option[IndividualDetailsAnswers],
    organisation: Option[OrganisationDetailsAnswers]
)

object PrDetailsAnswers {
  implicit val format: OFormat[PrDetailsAnswers] = Json.format[PrDetailsAnswers]
}

case class IndividualDetailsAnswers(
    title: Option[String],
    firstForename: String,
    secondForename: Option[String],
    surname: String,
    addressLine1: Option[String],
    addressLine2: Option[String],
    addressLine3: Option[String],
    addressLine4: Option[String],
    postCode: Option[String],
    country: Option[String]
)

object IndividualDetailsAnswers {
  implicit val format: OFormat[IndividualDetailsAnswers] = Json.format[IndividualDetailsAnswers]
}

case class OrganisationDetailsAnswers(
    organisationName: String,
    title: Option[String],
    firstForename: String,
    secondForename: Option[String],
    surname: String,
    addressLine1: Option[String],
    addressLine2: Option[String],
    addressLine3: Option[String],
    addressLine4: Option[String],
    postCode: Option[String],
    country: Option[String]
)

object OrganisationDetailsAnswers {
  implicit val format: OFormat[OrganisationDetailsAnswers] = Json.format[OrganisationDetailsAnswers]
}
