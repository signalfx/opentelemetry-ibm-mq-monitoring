/*
 * Copyright Splunk Inc.
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
package com.splunk.ibm.mq.metricscollector;

import static com.splunk.ibm.mq.metricscollector.MetricAssert.assertThatMetric;
import static com.splunk.ibm.mq.metricscollector.MetricPropertiesAssert.standardPropsForAlias;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.common.Constants;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListenerMetricsCollectorTest {

  private ListenerMetricsCollector classUnderTest;

  @Mock private PCFMessageAgent pcfMessageAgent;

  @Mock private OpenTelemetryMetricWriteHelper metricWriteHelper;

  private Map<String, WMQMetricOverride> listenerMetricsToReport;
  private QueueManager queueManager;
  private ArgumentCaptor<List<Metric>> pathCaptor;
  private MetricCreator metricCreator;

  @BeforeEach
  public void setup() throws Exception {
    ConfigWrapper config = ConfigWrapper.parse("src/test/resources/conf/config.yml");
    ObjectMapper mapper = new ObjectMapper();
    queueManager = mapper.convertValue(config.getQueueManagers().get(0), QueueManager.class);
    Map<String, Map<String, WMQMetricOverride>> metricsMap = config.getMQMetrics();
    listenerMetricsToReport = metricsMap.get(Constants.METRIC_TYPE_LISTENER);
    pathCaptor = ArgumentCaptor.forClass(List.class);
    metricCreator = new MetricCreator(queueManager.getName());
  }

  @Test
  public void testPublishMetrics() throws Exception {
    when(pcfMessageAgent.send(any(PCFMessage.class)))
        .thenReturn(createPCFResponseForInquireListenerStatusCmd());
    IntAttributesBuilder attributesBuilder = new IntAttributesBuilder(listenerMetricsToReport);
    MetricsCollectorContext context =
        new MetricsCollectorContext(
            listenerMetricsToReport,
            attributesBuilder,
            queueManager,
            pcfMessageAgent,
            metricWriteHelper);
    classUnderTest = new ListenerMetricsCollector(context, metricCreator);
    classUnderTest.publishMetrics();
    verify(metricWriteHelper, times(2)).transformAndPrintMetrics(pathCaptor.capture());

    List<List<Metric>> allValues = pathCaptor.getAllValues();
    assertThat(allValues).hasSize(2);
    assertThat(allValues.get(0)).hasSize(1);
    assertThat(allValues.get(1)).hasSize(1);

    assertThatMetric(allValues.get(0).get(0))
        .hasName("mq.listener.status")
        .hasValue("2")
        .withPropertiesMatching(standardPropsForAlias("mq.listener.status"));

    assertThatMetric(allValues.get(1).get(0))
        .hasName("mq.listener.status")
        .hasValue("3")
        .withPropertiesMatching(standardPropsForAlias("mq.listener.status"));
  }

  /*
     Request
     PCFMessage:
     MQCFH [type: 1, strucLength: 36, version: 1, command: 98 (MQCMD_INQUIRE_LISTENER_STATUS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 2]
     MQCFST [type: 4, strucLength: 24, parameter: 3554 (MQCACH_LISTENER_NAME), codedCharSetId: 0, stringLength: 1, string: *]
     MQCFIL [type: 5, strucLength: 24, parameter: 1223 (MQIACF_LISTENER_STATUS_ATTRS), count: 2, values: {3554, 1599}]

     Response
     PCFMessage:
     MQCFH [type: 2, strucLength: 36, version: 1, command: 98 (MQCMD_INQUIRE_LISTENER_STATUS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 2]
     MQCFST [type: 4, strucLength: 48, parameter: 3554 (MQCACH_LISTENER_NAME), codedCharSetId: 819, stringLength: 27, string: SYSTEM.DEFAULT.LISTENER.TCP]
     MQCFIN [type: 3, strucLength: 16, parameter: 1599 (MQIACH_LISTENER_STATUS), value: 2]
  */

  private PCFMessage[] createPCFResponseForInquireListenerStatusCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_LISTENER_STATUS, 1, true);
    response1.addParameter(CMQCFC.MQCACH_LISTENER_NAME, "DEV.DEFAULT.LISTENER.TCP");
    response1.addParameter(CMQCFC.MQIACH_LISTENER_STATUS, 2);

    PCFMessage response2 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_LISTENER_STATUS, 2, true);
    response2.addParameter(CMQCFC.MQCACH_LISTENER_NAME, "DEV.LISTENER.TCP");
    response2.addParameter(CMQCFC.MQIACH_LISTENER_STATUS, 3);

    PCFMessage response3 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_LISTENER_STATUS, 3, true);
    response3.addParameter(CMQCFC.MQCACH_LISTENER_NAME, "SYSTEM.LISTENER.TCP");
    response3.addParameter(CMQCFC.MQIACH_LISTENER_STATUS, 1);

    return new PCFMessage[] {response1, response2, response3};
  }
}
