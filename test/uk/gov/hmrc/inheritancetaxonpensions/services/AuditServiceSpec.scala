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
import uk.gov.hmrc.inheritancetaxonpensions.config.AppConfig
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.inheritancetaxonpensions.models.audit.ReportSubmittedAuditModel
import org.mockito.ArgumentMatchers.any
import utils.TestValues
import org.scalatest.freespec.AnyFreeSpec
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.ExecutionContext

class AuditServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach with TestValues {

  private val mockAuditConnector: AuditConnector = mock[AuditConnector]
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
  private val appName = "audit-source"
  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.appName).`thenReturn`(appName)

  private val service = new AuditService(mockAppConfig, mockAuditConnector)

  override def beforeEach(): Unit = {
    Mockito.reset(mockAuditConnector)
    Mockito.reset(mockAppConfig)
    super.beforeEach()
  }

  ".sendAudit" - {
    s"must send an event to the audit connector" in {
      when(mockAppConfig.appName).thenReturn(appName)
      service.audit(
        ReportSubmittedAuditModel.build(testReportSubmissionRequestBody, None)
      )
      val eventCaptor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])

      verify(mockAuditConnector, times(1))
        .sendExtendedEvent(eventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext])

      val event = eventCaptor.getValue
      event.auditSource mustEqual appName
      event.auditType mustEqual "SuccessfulSubmission"
    }
  }
}
