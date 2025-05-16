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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.appdynamics.extensions.metrics.Metric;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQC;
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
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueueManagerMetricsCollectorTest {

  QueueManagerMetricsCollector classUnderTest;

  @Mock PCFMessageAgent pcfMessageAgent;

  @Mock OpenTelemetryMetricWriteHelper metricWriteHelper;

  Map<String, WMQMetricOverride> queueMgrMetricsToReport;
  QueueManager queueManager;
  ArgumentCaptor<List> pathCaptor;
  MetricCreator metricCreator;
  MetricsCollectorContext context;

  @BeforeEach
  public void setup() throws Exception {

    ConfigWrapper config = ConfigWrapper.parse("src/test/resources/conf/config.yml");
    ObjectMapper mapper = new ObjectMapper();
    queueManager = mapper.convertValue(config.getQueueManagers().get(0), QueueManager.class);
    Map<String, Map<String, WMQMetricOverride>> metricsMap = config.getMQMetrics();
    queueMgrMetricsToReport = metricsMap.get(Constants.METRIC_TYPE_QUEUE_MANAGER);
    pathCaptor = ArgumentCaptor.forClass(List.class);
    metricCreator = new MetricCreator(config.getMetricPrefix(), queueManager);
    context =
        new MetricsCollectorContext(
            queueMgrMetricsToReport, queueManager, pcfMessageAgent, metricWriteHelper);
  }

  @Test
  public void testProcessPCFRequestAndPublishQMetricsForInquireQStatusCmd() throws Exception {
    CountDownLatch latch = mock(CountDownLatch.class);
    when(pcfMessageAgent.send(any(PCFMessage.class)))
        .thenReturn(createPCFResponseForInquireQMgrStatusCmd());
    classUnderTest = new QueueManagerMetricsCollector(context, metricCreator);
    classUnderTest.publishMetrics();
    verify(metricWriteHelper, times(1)).transformAndPrintMetrics(pathCaptor.capture());
    List<String> metricPathsList = Lists.newArrayList();
    metricPathsList.add("Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Status");

    for (List<Metric> metricList : pathCaptor.getAllValues()) {
      for (Metric metric : metricList) {
        if (metricPathsList.contains(metric.getMetricPath())) {
          if (metric
              .getMetricPath()
              .equals("Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Status")) {
            assertThat(metric.getMetricValue()).isEqualTo("2");
            assertThat(metric.getMetricValue()).isNotEqualTo("10");
          }
        }
      }
    }
  }

  /*  Request
     PCFMessage:
     MQCFH [type: 1, strucLength: 36, version: 1, command: 161 (MQCMD_INQUIRE_Q_MGR_STATUS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 1]
     MQCFIL [type: 5, strucLength: 20, parameter: 1229 (MQIACF_Q_MGR_STATUS_ATTRS), count: 1, values: {1009}]

     Response
     PCFMessage:
     MQCFH [type: 2, strucLength: 36, version: 1, command: 161 (MQCMD_INQUIRE_Q_MGR_STATUS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 23]
     MQCFST [type: 4, strucLength: 68, parameter: 2015 (MQCA_Q_MGR_NAME), codedCharSetId: 819, stringLength: 48, string: QM1                                             ]
     MQCFIN [type: 3, strucLength: 16, parameter: 1149 (MQIACF_Q_MGR_STATUS), value: 2]
     MQCFST [type: 4, strucLength: 20, parameter: 3208 (null), codedCharSetId: 819, stringLength: 0, string: ]
     MQCFIN [type: 3, strucLength: 16, parameter: 1416 (null), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 1232 (MQIACF_CHINIT_STATUS), value: 2]
     MQCFIN [type: 3, strucLength: 16, parameter: 1233 (MQIACF_CMD_SERVER_STATUS), value: 2]
     MQCFIN [type: 3, strucLength: 16, parameter: 1230 (MQIACF_CONNECTION_COUNT), value: 23]
     MQCFST [type: 4, strucLength: 20, parameter: 3071 (MQCACF_CURRENT_LOG_EXTENT_NAME), codedCharSetId: 819, stringLength: 0, string: ]
     MQCFST [type: 4, strucLength: 20, parameter: 2115 (null), codedCharSetId: 819, stringLength: 0, string: ]
     MQCFST [type: 4, strucLength: 36, parameter: 2116 (null), codedCharSetId: 819, stringLength: 13, string: Installation1]
     MQCFST [type: 4, strucLength: 28, parameter: 2117 (null), codedCharSetId: 819, stringLength: 8, string: /opt/mqm]
     MQCFIN [type: 3, strucLength: 16, parameter: 1409 (null), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 1420 (null), value: 9]
     MQCFST [type: 4, strucLength: 44, parameter: 3074 (MQCACF_LOG_PATH), codedCharSetId: 819, stringLength: 24, string: /var/mqm/log/QM1/active/]
     MQCFIN [type: 3, strucLength: 16, parameter: 1421 (null), value: 9]
     MQCFST [type: 4, strucLength: 20, parameter: 3073 (MQCACF_MEDIA_LOG_EXTENT_NAME), codedCharSetId: 819, stringLength: 0, string: ]
     MQCFIN [type: 3, strucLength: 16, parameter: 1417 (null), value: 0]
     MQCFST [type: 4, strucLength: 20, parameter: 3072 (MQCACF_RESTART_LOG_EXTENT_NAME), codedCharSetId: 819, stringLength: 0, string: ]
     MQCFIN [type: 3, strucLength: 16, parameter: 1418 (null), value: 1]
     MQCFIN [type: 3, strucLength: 16, parameter: 1419 (null), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 1325 (null), value: 0]
     MQCFST [type: 4, strucLength: 32, parameter: 3175 (null), codedCharSetId: 819, stringLength: 12, string: 2018-05-08  ]
     MQCFST [type: 4, strucLength: 28, parameter: 3176 (null), codedCharSetId: 819, stringLength: 8, string: 08.32.08]
  */

  private PCFMessage[] createPCFResponseForInquireQMgrStatusCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_Q_MGR_STATUS, 1, true);
    response1.addParameter(CMQC.MQCA_Q_MGR_NAME, "QM1");
    response1.addParameter(CMQCFC.MQIACF_Q_MGR_STATUS, 2);
    response1.addParameter(CMQCFC.MQIACF_CHINIT_STATUS, 2);
    response1.addParameter(CMQCFC.MQIACF_CMD_SERVER_STATUS, 2);
    response1.addParameter(CMQCFC.MQIACF_CONNECTION_COUNT, 23);
    response1.addParameter(CMQCFC.MQCACF_CURRENT_LOG_EXTENT_NAME, "");
    response1.addParameter(CMQCFC.MQCACF_LOG_PATH, "/var/mqm/log/QM1/active/");
    response1.addParameter(CMQCFC.MQCACF_MEDIA_LOG_EXTENT_NAME, "");
    response1.addParameter(CMQCFC.MQCACF_RESTART_LOG_EXTENT_NAME, "");

    return new PCFMessage[] {response1};
  }
}
