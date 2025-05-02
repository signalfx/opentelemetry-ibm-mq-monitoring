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
package com.appdynamics.extensions.webspheremq.metricscollector;

import static com.appdynamics.extensions.webspheremq.metricscollector.MetricAssert.assertThatMetric;
import static com.appdynamics.extensions.webspheremq.metricscollector.MetricPropertiesAssert.standardPropsForAlias;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.appdynamics.extensions.AMonitorJob;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.PathResolver;
import com.appdynamics.extensions.webspheremq.common.Constants;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
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
public class QueueCollectionBuddyTest {
  private QueueCollectionBuddy classUnderTest;

  @Mock private AMonitorJob aMonitorJob;

  @Mock private PCFMessageAgent pcfMessageAgent;

  @Mock private MetricWriteHelper metricWriteHelper;

  @Mock private CountDownLatch phaser;

  private MonitorContextConfiguration monitorContextConfig;
  private Map<String, WMQMetricOverride> queueMetricsToReport;
  private QueueManager queueManager;
  ArgumentCaptor<List<Metric>> pathCaptor;
  MetricCreator metricCreator;
  MetricsCollectorContext collectorContext;

  @BeforeEach
  void setup() {
    monitorContextConfig =
        new MonitorContextConfiguration(
            "WMQMonitor",
            "Custom Metrics|WMQMonitor|",
            PathResolver.resolveDirectory(AManagedMonitor.class),
            aMonitorJob);
    monitorContextConfig.setConfigYml("src/test/resources/conf/config.yml");
    Map<String, ?> configMap = monitorContextConfig.getConfigYml();
    ObjectMapper mapper = new ObjectMapper();
    queueManager =
        mapper.convertValue(((List) configMap.get("queueManagers")).get(0), QueueManager.class);
    Map<String, Map<String, WMQMetricOverride>> metricsMap =
        WMQUtil.getMetricsToReportFromConfigYml((List<Map>) configMap.get("mqMetrics"));
    queueMetricsToReport = metricsMap.get(Constants.METRIC_TYPE_QUEUE);
    QueueCollectorSharedState.getInstance().resetForTest();
    pathCaptor = ArgumentCaptor.forClass(List.class);
    metricCreator =
        new MetricCreator(
            monitorContextConfig.getMetricPrefix(), queueManager, QueueMetricsCollector.ARTIFACT);
    collectorContext =
        new MetricsCollectorContext(
            queueMetricsToReport, queueManager, pcfMessageAgent, metricWriteHelper);
  }

  @Test
  void testProcessPCFRequestAndPublishQMetricsForInquireQStatusCmd() throws Exception {
    QueueCollectorSharedState sharedState = QueueCollectorSharedState.getInstance();
    sharedState.putQueueType("AMQ.5AF1608820C7D76E", "local-transmission");
    sharedState.putQueueType("DEV.DEAD.LETTER.QUEUE", "local-transmission");
    sharedState.putQueueType("DEV.QUEUE.1", "local-transmission");
    PCFMessage request = createPCFRequestForInquireQStatusCmd();
    when(pcfMessageAgent.send(request)).thenReturn(createPCFResponseForInquireQStatusCmd());

    classUnderTest =
        new QueueCollectionBuddy(
            collectorContext, sharedState, metricCreator, "MQCMD_INQUIRE_Q_STATUS");
    classUnderTest.processPCFRequestAndPublishQMetrics(request, "*");

    verify(metricWriteHelper, times(2)).transformAndPrintMetrics(pathCaptor.capture());

    List<List<Metric>> allValues = pathCaptor.getAllValues();
    assertThat(allValues).hasSize(2);
    assertThat(allValues.get(0)).hasSize(5);
    assertThat(allValues.get(1)).hasSize(5);

    verifyStatusRow(allValues.get(0), "DEV.DEAD.LETTER.QUEUE", List.of(0, -1, -1, -1, 0));
    verifyStatusRow(allValues.get(1), "DEV.QUEUE.1", List.of(1, -1, -1, -1, 10));
  }

  private static void verifyStatusRow(
      List<Metric> metrics, String component, List<Integer> values) {
    assertThatMetric(metrics.get(0))
        .hasName("CurrentQueueDepth")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|local-transmission|CurrentQueueDepth")
        .hasValue(String.valueOf(values.get(0)))
        .withPropertiesMatching(standardPropsForAlias("Current Queue Depth"));

    assertThatMetric(metrics.get(1))
        .hasName("OnQTime_1")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|OnQTime_1")
        .hasValue(String.valueOf(values.get(1)))
        .withPropertiesMatching(standardPropsForAlias("OnQTime"));

    assertThatMetric(metrics.get(2))
        .hasName("OnQTime_2")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|OnQTime_2")
        .hasValue(String.valueOf(values.get(2)))
        .withPropertiesMatching(standardPropsForAlias("OnQTime"));

    assertThatMetric(metrics.get(3))
        .hasName("OldestMsgAge")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|local-transmission|OldestMsgAge")
        .hasValue(String.valueOf(values.get(3)))
        .withPropertiesMatching(standardPropsForAlias("OldestMsgAge"));

    assertThatMetric(metrics.get(4))
        .hasName("UncommittedMsgs")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|local-transmission|UncommittedMsgs")
        .hasValue(String.valueOf(values.get(4)))
        .withPropertiesMatching(standardPropsForAlias("UncommittedMsgs"));
  }

  @Test
  void testProcessPCFRequestAndPublishQMetricsForInquireQCmd() throws Exception {
    PCFMessage request = createPCFRequestForInquireQCmd();
    when(pcfMessageAgent.send(request)).thenReturn(createPCFResponseForInquireQCmd());
    classUnderTest =
        new QueueCollectionBuddy(
            collectorContext,
            QueueCollectorSharedState.getInstance(),
            metricCreator,
            "MQCMD_INQUIRE_Q");
    classUnderTest.processPCFRequestAndPublishQMetrics(request, "*");

    verify(metricWriteHelper, times(2)).transformAndPrintMetrics(pathCaptor.capture());

    List<List<Metric>> allValues = pathCaptor.getAllValues();
    assertThat(allValues).hasSize(2);
    assertThat(allValues.get(0)).hasSize(4);
    assertThat(allValues.get(1)).hasSize(4);

    verifyQRow(allValues.get(0), "DEV.DEAD.LETTER.QUEUE", List.of(5000, 2, 2, 2));
    verifyQRow(allValues.get(1), "DEV.QUEUE.1", List.of(5000, 3, 3, 3));
  }

  private static void verifyQRow(List<Metric> metrics, String component, List<Integer> values) {
    assertThatMetric(metrics.get(0))
        .hasName("MaxQueueDepth")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|local-transmission|MaxQueueDepth")
        .hasValue(String.valueOf(values.get(0)))
        .withPropertiesMatching(standardPropsForAlias("Max Queue Depth"));

    assertThatMetric(metrics.get(1))
        .hasName("CurrentQueueDepth")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|local-transmission|CurrentQueueDepth")
        .hasValue(String.valueOf(values.get(1)))
        .withPropertiesMatching(standardPropsForAlias("Current Queue Depth"));

    assertThatMetric(metrics.get(2))
        .hasName("OpenInputCount")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|local-transmission|OpenInputCount")
        .hasValue(String.valueOf(values.get(2)))
        .withPropertiesMatching(standardPropsForAlias("Open Input Count"));

    assertThatMetric(metrics.get(3))
        .hasName("OpenOutputCount")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|"
                + component
                + "|local-transmission|OpenOutputCount")
        .hasValue(String.valueOf(values.get(3)))
        .withPropertiesMatching(standardPropsForAlias("Open Output Count"));
  }

  @Test
  void testProcessPCFRequestAndPublishQMetricsForResetQStatsCmd() throws Exception {
    QueueCollectorSharedState sharedState = QueueCollectorSharedState.getInstance();
    sharedState.putQueueType("AMQ.5AF1608820C7D76E", "local-transmission");
    sharedState.putQueueType("DEV.DEAD.LETTER.QUEUE", "local-transmission");
    sharedState.putQueueType("DEV.QUEUE.1", "local-transmission");
    PCFMessage request = createPCFRequestForResetQStatsCmd();
    when(pcfMessageAgent.send(request)).thenReturn(createPCFResponseForResetQStatsCmd());
    classUnderTest =
        new QueueCollectionBuddy(
            collectorContext, sharedState, metricCreator, "MQCMD_RESET_Q_STATUS");
    classUnderTest.processPCFRequestAndPublishQMetrics(request, "*");

    verify(metricWriteHelper, times(1)).transformAndPrintMetrics(pathCaptor.capture());

    List<List<Metric>> allValues = pathCaptor.getAllValues();
    assertThat(allValues).hasSize(1);
    assertThat(allValues.get(0)).hasSize(3);

    assertThatMetric(allValues.get(0).get(0))
        .hasName("HighQDepth")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|DEV.DEAD.LETTER.QUEUE|local-transmission|HighQDepth")
        .hasValue("10")
        .withPropertiesMatching(standardPropsForAlias("HighQDepth"));

    assertThatMetric(allValues.get(0).get(1))
        .hasName("MsgDeqCount")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|DEV.DEAD.LETTER.QUEUE|local-transmission|MsgDeqCount")
        .hasValue("0")
        .withPropertiesMatching(standardPropsForAlias("MsgDeqCount"));

    assertThatMetric(allValues.get(0).get(2))
        .hasName("MsgEnqCount")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Queues|DEV.DEAD.LETTER.QUEUE|local-transmission|MsgEnqCount")
        .hasValue("3")
        .withPropertiesMatching(standardPropsForAlias("MsgEnqCount"));
  }

  /*
      PCFMessage:
      MQCFH [type: 1, strucLength: 36, version: 1, command: 41 (MQCMD_INQUIRE_Q_STATUS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 2]
      MQCFST [type: 4, strucLength: 24, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 0, stringLength: 1, string: *]
      MQCFIL [type: 5, strucLength: 32, parameter: 1026 (MQIACF_Q_STATUS_ATTRS), count: 4, values: {2016, 1226, 1227, 1027}]
  */
  private PCFMessage createPCFRequestForInquireQStatusCmd() {
    PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_STATUS);
    request.addParameter(CMQC.MQCA_Q_NAME, "*");
    request.addParameter(CMQCFC.MQIACF_Q_STATUS_ATTRS, new int[] {2016, 1226, 1227, 1027});
    return request;
  }

  /*
      0 = {PCFMessage@6026} "PCFMessage:
      MQCFH [type: 2, strucLength: 36, version: 2, command: 41 (MQCMD_INQUIRE_Q_STATUS), msgSeqNumber: 1, control: 0, compCode: 0, reason: 0, parameterCount: 6]
      MQCFST [type: 4, strucLength: 68, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 819, stringLength: 48, string: AMQ.5AF1608820C7D76E                            ]
      MQCFIN [type: 3, strucLength: 16, parameter: 1103 (MQIACF_Q_STATUS_TYPE), value: 1105]
      MQCFIN [type: 3, strucLength: 16, parameter: 3 (MQIA_CURRENT_Q_DEPTH), value: 12]
      MQCFIN [type: 3, strucLength: 16, parameter: 1227 (MQIACF_OLDEST_MSG_AGE), value: -1]
      MQCFIL [type: 5, strucLength: 24, parameter: 1226 (MQIACF_Q_TIME_INDICATOR), count: 2, values: {-1, -1}]
      MQCFIN [type: 3, strucLength: 16, parameter: 1027 (MQIACF_UNCOMMITTED_MSGS), value: 0]"

      1 = {PCFMessage@6029} "PCFMessage:
      MQCFH [type: 2, strucLength: 36, version: 2, command: 41 (MQCMD_INQUIRE_Q_STATUS), msgSeqNumber: 2, control: 0, compCode: 0, reason: 0, parameterCount: 6]
      MQCFST [type: 4, strucLength: 68, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 819, stringLength: 48, string: DEV.DEAD.LETTER.QUEUE                           ]
      MQCFIN [type: 3, strucLength: 16, parameter: 1103 (MQIACF_Q_STATUS_TYPE), value: 1105]
      MQCFIN [type: 3, strucLength: 16, parameter: 3 (MQIA_CURRENT_Q_DEPTH), value: 0]
      MQCFIN [type: 3, strucLength: 16, parameter: 1227 (MQIACF_OLDEST_MSG_AGE), value: -1]
      MQCFIL [type: 5, strucLength: 24, parameter: 1226 (MQIACF_Q_TIME_INDICATOR), count: 2, values: {-1, -1}]
      MQCFIN [type: 3, strucLength: 16, parameter: 1027 (MQIACF_UNCOMMITTED_MSGS), value: 0]"

      2 = {PCFMessage@6030} "PCFMessage:
      MQCFH [type: 2, strucLength: 36, version: 2, command: 41 (MQCMD_INQUIRE_Q_STATUS), msgSeqNumber: 3, control: 0, compCode: 0, reason: 0, parameterCount: 6]
      MQCFST [type: 4, strucLength: 68, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 819, stringLength: 48, string: DEV.QUEUE.1                                     ]
      MQCFIN [type: 3, strucLength: 16, parameter: 1103 (MQIACF_Q_STATUS_TYPE), value: 1105]
      MQCFIN [type: 3, strucLength: 16, parameter: 3 (MQIA_CURRENT_Q_DEPTH), value: 1]
      MQCFIN [type: 3, strucLength: 16, parameter: 1227 (MQIACF_OLDEST_MSG_AGE), value: -1]
      MQCFIL [type: 5, strucLength: 24, parameter: 1226 (MQIACF_Q_TIME_INDICATOR), count: 2, values: {-1, -1}]
      MQCFIN [type: 3, strucLength: 16, parameter: 1027 (MQIACF_UNCOMMITTED_MSGS), value: 0]"
  */
  private PCFMessage[] createPCFResponseForInquireQStatusCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_Q_STATUS, 1, false);
    response1.addParameter(CMQC.MQCA_Q_NAME, "AMQ.5AF1608820C7D76E");
    response1.addParameter(CMQCFC.MQIACF_Q_STATUS_TYPE, 1105);
    response1.addParameter(CMQC.MQIA_CURRENT_Q_DEPTH, 12);
    response1.addParameter(CMQCFC.MQIACF_OLDEST_MSG_AGE, -1);
    response1.addParameter(CMQCFC.MQIACF_Q_TIME_INDICATOR, new int[] {-1, -1});
    response1.addParameter(CMQCFC.MQIACF_UNCOMMITTED_MSGS, 0);

    PCFMessage response2 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_Q_STATUS, 2, false);
    response2.addParameter(CMQC.MQCA_Q_NAME, "DEV.DEAD.LETTER.QUEUE");
    response2.addParameter(CMQCFC.MQIACF_Q_STATUS_TYPE, 1105);
    response2.addParameter(CMQC.MQIA_CURRENT_Q_DEPTH, 0);
    response2.addParameter(CMQCFC.MQIACF_OLDEST_MSG_AGE, -1);
    response2.addParameter(CMQCFC.MQIACF_Q_TIME_INDICATOR, new int[] {-1, -1});
    response2.addParameter(CMQCFC.MQIACF_UNCOMMITTED_MSGS, 0);

    PCFMessage response3 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_Q_STATUS, 1, false);
    response3.addParameter(CMQC.MQCA_Q_NAME, "DEV.QUEUE.1");
    response3.addParameter(CMQCFC.MQIACF_Q_STATUS_TYPE, 1105);
    response3.addParameter(CMQC.MQIA_CURRENT_Q_DEPTH, 1);
    response3.addParameter(CMQCFC.MQIACF_OLDEST_MSG_AGE, -1);
    response3.addParameter(CMQCFC.MQIACF_Q_TIME_INDICATOR, new int[] {-1, -1});
    response3.addParameter(CMQCFC.MQIACF_UNCOMMITTED_MSGS, 10);

    return new PCFMessage[] {response1, response2, response3};
  }

  /*
     PCFMessage:
     MQCFH [type: 1, strucLength: 36, version: 1, command: 13 (MQCMD_INQUIRE_Q), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 3]
     MQCFST [type: 4, strucLength: 24, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 0, stringLength: 1, string: *]
     MQCFIN [type: 3, strucLength: 16, parameter: 20 (MQIA_Q_TYPE), value: 1001]
     MQCFIL [type: 5, strucLength: 36, parameter: 1002 (MQIACF_Q_ATTRS), count: 5, values: {2016, 15, 3, 17, 18}]
  */
  private PCFMessage createPCFRequestForInquireQCmd() {
    PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
    request.addParameter(CMQC.MQCA_Q_NAME, "*");
    request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_ALL);
    request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[] {2016, 15, 3, 17, 18});
    return request;
  }

  /*
     0 = {PCFMessage@6059} "PCFMessage:
     MQCFH [type: 2, strucLength: 36, version: 1, command: 13 (MQCMD_INQUIRE_Q), msgSeqNumber: 1, control: 0, compCode: 0, reason: 0, parameterCount: 6]
     MQCFST [type: 4, strucLength: 68, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 819, stringLength: 48, string: AMQ.5AF1608820C76D80                            ]
     MQCFIN [type: 3, strucLength: 16, parameter: 20 (MQIA_Q_TYPE), value: 1]
     MQCFIN [type: 3, strucLength: 16, parameter: 3 (MQIA_CURRENT_Q_DEPTH), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 17 (MQIA_OPEN_INPUT_COUNT), value: 1]
     MQCFIN [type: 3, strucLength: 16, parameter: 15 (MQIA_MAX_Q_DEPTH), value: 5000]
     MQCFIN [type: 3, strucLength: 16, parameter: 18 (MQIA_OPEN_OUTPUT_COUNT), value: 1]"

     1 = {PCFMessage@6060} "PCFMessage:
     MQCFH [type: 2, strucLength: 36, version: 1, command: 13 (MQCMD_INQUIRE_Q), msgSeqNumber: 2, control: 0, compCode: 0, reason: 0, parameterCount: 6]
     MQCFST [type: 4, strucLength: 68, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 819, stringLength: 48, string: DEV.DEAD.LETTER.QUEUE                           ]
     MQCFIN [type: 3, strucLength: 16, parameter: 20 (MQIA_Q_TYPE), value: 1]
     MQCFIN [type: 3, strucLength: 16, parameter: 3 (MQIA_CURRENT_Q_DEPTH), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 17 (MQIA_OPEN_INPUT_COUNT), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 15 (MQIA_MAX_Q_DEPTH), value: 5000]
     MQCFIN [type: 3, strucLength: 16, parameter: 18 (MQIA_OPEN_OUTPUT_COUNT), value: 0]"

     2 = {PCFMessage@6061} "PCFMessage:
     MQCFH [type: 2, strucLength: 36, version: 1, command: 13 (MQCMD_INQUIRE_Q), msgSeqNumber: 3, control: 0, compCode: 0, reason: 0, parameterCount: 6]
     MQCFST [type: 4, strucLength: 68, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 819, stringLength: 48, string: DEV.QUEUE.1                                     ]
     MQCFIN [type: 3, strucLength: 16, parameter: 20 (MQIA_Q_TYPE), value: 1]
     MQCFIN [type: 3, strucLength: 16, parameter: 3 (MQIA_CURRENT_Q_DEPTH), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 17 (MQIA_OPEN_INPUT_COUNT), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 15 (MQIA_MAX_Q_DEPTH), value: 5000]
     MQCFIN [type: 3, strucLength: 16, parameter: 18 (MQIA_OPEN_OUTPUT_COUNT), value: 0]"
  */

  private PCFMessage[] createPCFResponseForInquireQCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_Q, 1, false);
    response1.addParameter(CMQC.MQCA_Q_NAME, "AMQ.5AF1608820C76D80");
    response1.addParameter(CMQC.MQIA_Q_TYPE, 1);
    response1.addParameter(CMQC.MQIA_CURRENT_Q_DEPTH, 1);
    response1.addParameter(CMQC.MQIA_OPEN_INPUT_COUNT, 1);
    response1.addParameter(CMQC.MQIA_MAX_Q_DEPTH, 5000);
    response1.addParameter(CMQC.MQIA_OPEN_OUTPUT_COUNT, 1);
    response1.addParameter(CMQC.MQIA_USAGE, CMQC.MQUS_NORMAL);

    PCFMessage response2 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_Q, 2, false);
    response2.addParameter(CMQC.MQCA_Q_NAME, "DEV.DEAD.LETTER.QUEUE");
    response2.addParameter(CMQC.MQIA_Q_TYPE, 1);
    response2.addParameter(CMQC.MQIA_CURRENT_Q_DEPTH, 2);
    response2.addParameter(CMQC.MQIA_OPEN_INPUT_COUNT, 2);
    response2.addParameter(CMQC.MQIA_MAX_Q_DEPTH, 5000);
    response2.addParameter(CMQC.MQIA_OPEN_OUTPUT_COUNT, 2);
    response2.addParameter(CMQC.MQIA_USAGE, CMQC.MQUS_TRANSMISSION);

    PCFMessage response3 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_Q, 3, false);
    response3.addParameter(CMQC.MQCA_Q_NAME, "DEV.QUEUE.1");
    response3.addParameter(CMQC.MQIA_Q_TYPE, 1);
    response3.addParameter(CMQC.MQIA_CURRENT_Q_DEPTH, 3);
    response3.addParameter(CMQC.MQIA_OPEN_INPUT_COUNT, 3);
    response3.addParameter(CMQC.MQIA_MAX_Q_DEPTH, 5000);
    response3.addParameter(CMQC.MQIA_OPEN_OUTPUT_COUNT, 3);
    response3.addParameter(CMQC.MQIA_USAGE, CMQC.MQUS_TRANSMISSION);

    return new PCFMessage[] {response1, response2, response3};
  }

  /*
     PCFMessage:
     MQCFH [type: 1, strucLength: 36, version: 1, command: 17 (MQCMD_RESET_Q_STATS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 1]
     MQCFST [type: 4, strucLength: 24, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 0, stringLength: 1, string: *]
  */
  private PCFMessage createPCFRequestForResetQStatsCmd() {
    PCFMessage request = new PCFMessage(CMQCFC.MQCMD_RESET_Q_STATS);
    request.addParameter(CMQC.MQCA_Q_NAME, "*");
    return request;
  }

  /*
     0 = {PCFMessage@6144} "PCFMessage:
     MQCFH [type: 2, strucLength: 36, version: 1, command: 17 (MQCMD_RESET_Q_STATS), msgSeqNumber: 1, control: 0, compCode: 0, reason: 0, parameterCount: 5]
     MQCFST [type: 4, strucLength: 68, parameter: 2016 (MQCA_Q_NAME), codedCharSetId: 819, stringLength: 48, string: DEV.DEAD.LETTER.QUEUE                           ]
     MQCFIN [type: 3, strucLength: 16, parameter: 37 (MQIA_MSG_ENQ_COUNT), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 38 (MQIA_MSG_DEQ_COUNT), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 36 (MQIA_HIGH_Q_DEPTH), value: 0]
     MQCFIN [type: 3, strucLength: 16, parameter: 35 (MQIA_TIME_SINCE_RESET), value: 65]"
  */
  private PCFMessage[] createPCFResponseForResetQStatsCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_RESET_Q_STATS, 1, false);
    response1.addParameter(CMQC.MQCA_Q_NAME, "DEV.DEAD.LETTER.QUEUE");
    response1.addParameter(CMQC.MQIA_MSG_ENQ_COUNT, 3);
    response1.addParameter(CMQC.MQIA_MSG_DEQ_COUNT, 0);
    response1.addParameter(CMQC.MQIA_HIGH_Q_DEPTH, 10);
    response1.addParameter(CMQC.MQIA_TIME_SINCE_RESET, 65);

    return new PCFMessage[] {response1};
  }
}
