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
import static com.ibm.mq.constants.CMQC.MQRC_SELECTOR_ERROR;
import static com.ibm.mq.constants.CMQCFC.MQRCCF_CHL_STATUS_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelMetricsCollectorTest {
  ChannelMetricsCollector classUnderTest;

  @Mock AMonitorJob aMonitorJob;

  @Mock PCFMessageAgent pcfMessageAgent;

  @Mock MetricWriteHelper metricWriteHelper;

  MonitorContextConfiguration monitorContextConfig;

  QueueManager queueManager;
  ArgumentCaptor<List<Metric>> pathCaptor;
  MetricCreator metricCreator;
  MetricsCollectorContext context;

  @BeforeEach
  void setup() {
    monitorContextConfig =
        new MonitorContextConfiguration(
            "WMQMonitor",
            "Custom Metrics|WMQMonitor|",
            PathResolver.resolveDirectory(ChannelMetricsCollectorTest.class),
            aMonitorJob);
    monitorContextConfig.setConfigYml("src/test/resources/conf/config.yml");
    Map<String, ?> configMap = monitorContextConfig.getConfigYml();
    ObjectMapper mapper = new ObjectMapper();
    queueManager =
        mapper.convertValue(((List) configMap.get("queueManagers")).get(0), QueueManager.class);
    Map<String, Map<String, WMQMetricOverride>> metricsMap =
        WMQUtil.getMetricsToReportFromConfigYml((List<Map>) configMap.get("mqMetrics"));
    Map<String, WMQMetricOverride> channelMetrics = metricsMap.get(Constants.METRIC_TYPE_CHANNEL);
    Map<String, Map<String, WMQMetricOverride>> metricsByCommand = new HashMap<>();
    assertThat(channelMetrics).isNotEmpty();
    for (Map.Entry<String, WMQMetricOverride> e : channelMetrics.entrySet()) {
      String cmd =
          e.getValue().getIbmCommand() == null
              ? "MQCMD_INQUIRE_CHANNEL_STATUS"
              : e.getValue().getIbmCommand();
      metricsByCommand.putIfAbsent(cmd, new HashMap<>());
      metricsByCommand.get(cmd).put(e.getKey(), e.getValue());
    }
    Map<String, WMQMetricOverride> channelMetricsToReport =
        metricsByCommand.get("MQCMD_INQUIRE_CHANNEL_STATUS");
    pathCaptor = ArgumentCaptor.forClass(List.class);
    metricCreator =
        new MetricCreator(
            monitorContextConfig.getMetricPrefix(), queueManager, ChannelMetricsCollector.ARTIFACT);
    IntAttributesBuilder attributesBuilder = new IntAttributesBuilder(channelMetricsToReport);
    context =
        new MetricsCollectorContext(
            channelMetricsToReport,
            attributesBuilder,
            queueManager,
            pcfMessageAgent,
            metricWriteHelper);
  }

  @Test
  void testPublishMetrics() throws Exception {
    List<String> metricPathsList = Lists.newArrayList();
    metricPathsList.add(
        "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Channels|DEV.ADMIN.SVRCONN|Status");
    metricPathsList.add(
        "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Channels|DEV.APP.SVRCONN|Status");
    metricPathsList.add(
        "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Channels|ActiveChannelsCount");

    when(pcfMessageAgent.send(any(PCFMessage.class)))
        .thenReturn(createPCFResponseForInquireChannelStatusCmd());
    classUnderTest = new ChannelMetricsCollector(context, metricCreator);

    classUnderTest.publishMetrics();

    verify(metricWriteHelper, times(3)).transformAndPrintMetrics(pathCaptor.capture());

    List<List<Metric>> allValues = pathCaptor.getAllValues();
    assertThat(allValues).hasSize(3);
    assertThat(allValues.get(0)).hasSize(6);
    assertThat(allValues.get(1)).hasSize(6);
    assertThat(allValues.get(2)).hasSize(1);

    assertRowWithList(allValues.get(0), "ADMIN");
    assertRowWithList(allValues.get(1), "APP");

    assertThatMetric(allValues.get(2).get(0))
        .hasName("ActiveChannelsCount")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|ActiveChannelsCount")
        .hasValue("2")
        .withPropertiesMatching(standardPropsForAlias("ActiveChannelsCount"));
  }

  void assertRowWithList(List<Metric> metrics, String component) {
    assertThatMetric(metrics.get(0))
        .hasName("Status")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|DEV."
                + component
                + ".SVRCONN|Status")
        .hasValue("3")
        .withPropertiesMatching(standardPropsForAlias("Status"));

    assertThatMetric(metrics.get(1))
        .hasName("Messages")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|DEV."
                + component
                + ".SVRCONN|Messages")
        .hasValue("17")
        .withPropertiesMatching(standardPropsForAlias("Messages"));

    assertThatMetric(metrics.get(2))
        .hasName("BuffersSent")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|DEV."
                + component
                + ".SVRCONN|BuffersSent")
        .hasValue("19")
        .withPropertiesMatching(standardPropsForAlias("Buffers Sent"));

    assertThatMetric(metrics.get(3))
        .hasName("ByteSent")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|DEV."
                + component
                + ".SVRCONN|ByteSent")
        .hasValue("6984")
        .withPropertiesMatching(standardPropsForAlias("Byte Sent"));

    assertThatMetric(metrics.get(4))
        .hasName("BuffersReceived")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|DEV."
                + component
                + ".SVRCONN|BuffersReceived")
        .hasValue("20")
        .withPropertiesMatching(standardPropsForAlias("Buffers Received"));

    assertThatMetric(metrics.get(5))
        .hasName("ByteReceived")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|DEV."
                + component
                + ".SVRCONN|ByteReceived")
        .hasValue("5772")
        .withPropertiesMatching(standardPropsForAlias("Byte Received"));
  }

  /*
     Request
     PCFMessage:
     MQCFH [type: 1, strucLength: 36, version: 1, command: 42 (MQCMD_INQUIRE_CHANNEL_STATUS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 3]
     MQCFST [type: 4, strucLength: 24, parameter: 3501 (MQCACH_FIRST/MQCACH_CHANNEL_NAME), codedCharSetId: 0, stringLength: 1, string: *]
     MQCFIN [type: 3, strucLength: 16, parameter: 1523 (MQIACH_CHANNEL_INSTANCE_TYPE), value: 1011]
     MQCFIL [type: 5, strucLength: 48, parameter: 1524 (MQIACH_CHANNEL_INSTANCE_ATTRS), count: 8, values: {3501, 3506, 1527, 1534, 1538, 1535, 1539, 1536}]

     Response
     PCFMessage:
     MQCFH [type: 2, strucLength: 36, version: 1, command: 42 (MQCMD_INQUIRE_CHANNEL_STATUS), msgSeqNumber: 1, control: 1, compCode: 0, reason: 0, parameterCount: 11]
     MQCFST [type: 4, strucLength: 40, parameter: 3501 (MQCACH_FIRST/MQCACH_CHANNEL_NAME), codedCharSetId: 819, stringLength: 20, string: DEV.ADMIN.SVRCONN   ]
     MQCFIN [type: 3, strucLength: 16, parameter: 1511 (MQIACH_CHANNEL_TYPE), value: 7]
     MQCFIN [type: 3, strucLength: 16, parameter: 1539 (MQIACH_BUFFERS_RCVD/MQIACH_BUFFERS_RECEIVED), value: 20]
     MQCFIN [type: 3, strucLength: 16, parameter: 1538 (MQIACH_BUFFERS_SENT), value: 19]
     MQCFIN [type: 3, strucLength: 16, parameter: 1536 (MQIACH_BYTES_RCVD/MQIACH_BYTES_RECEIVED), value: 5772]
     MQCFIN [type: 3, strucLength: 16, parameter: 1535 (MQIACH_BYTES_SENT), value: 6984]
     MQCFST [type: 4, strucLength: 284, parameter: 3506 (MQCACH_CONNECTION_NAME), codedCharSetId: 819, stringLength: 264, string: 172.17.0.1]
     MQCFIN [type: 3, strucLength: 16, parameter: 1523 (MQIACH_CHANNEL_INSTANCE_TYPE), value: 1011]
     MQCFIN [type: 3, strucLength: 16, parameter: 1534 (MQIACH_MSGS), value: 17]
     MQCFIN [type: 3, strucLength: 16, parameter: 1527 (MQIACH_CHANNEL_STATUS), value: 3]
     MQCFIN [type: 3, strucLength: 16, parameter: 1609 (MQIACH_CHANNEL_SUBSTATE), value: 300]
  */

  private PCFMessage[] createPCFResponseForInquireChannelStatusCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_CHANNEL_STATUS, 1, true);
    response1.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, "DEV.ADMIN.SVRCONN");
    response1.addParameter(CMQCFC.MQIACH_CHANNEL_TYPE, 7);
    response1.addParameter(CMQCFC.MQIACH_BUFFERS_RECEIVED, 20);
    response1.addParameter(CMQCFC.MQIACH_BUFFERS_SENT, 19);
    response1.addParameter(CMQCFC.MQIACH_BYTES_RECEIVED, 5772);
    response1.addParameter(CMQCFC.MQIACH_BYTES_SENT, 6984);
    response1.addParameter(CMQCFC.MQCACH_CONNECTION_NAME, "172.17.0.1 ");
    response1.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_TYPE, 1011);
    response1.addParameter(CMQCFC.MQIACH_MSGS, 17);
    response1.addParameter(CMQCFC.MQIACH_CHANNEL_STATUS, 3);
    response1.addParameter(CMQCFC.MQIACH_CHANNEL_SUBSTATE, 300);

    PCFMessage response2 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_CHANNEL_STATUS, 2, true);
    response2.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, "DEV.APP.SVRCONN");
    response2.addParameter(CMQCFC.MQIACH_CHANNEL_TYPE, 7);
    response2.addParameter(CMQCFC.MQIACH_BUFFERS_RECEIVED, 20);
    response2.addParameter(CMQCFC.MQIACH_BUFFERS_SENT, 19);
    response2.addParameter(CMQCFC.MQIACH_BYTES_RECEIVED, 5772);
    response2.addParameter(CMQCFC.MQIACH_BYTES_SENT, 6984);
    response2.addParameter(CMQCFC.MQCACH_CONNECTION_NAME, "172.17.0.2 ");
    response2.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_TYPE, 1011);
    response2.addParameter(CMQCFC.MQIACH_MSGS, 17);
    response2.addParameter(CMQCFC.MQIACH_CHANNEL_STATUS, 3);
    response2.addParameter(CMQCFC.MQIACH_CHANNEL_SUBSTATE, 300);

    PCFMessage response3 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_CHANNEL_STATUS, 2, true);
    response3.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, "TEST.APP.SVRCONN");
    response3.addParameter(CMQCFC.MQIACH_CHANNEL_TYPE, 7);
    response3.addParameter(CMQCFC.MQIACH_BUFFERS_RECEIVED, 20);
    response3.addParameter(CMQCFC.MQIACH_BUFFERS_SENT, 19);
    response3.addParameter(CMQCFC.MQIACH_BYTES_RECEIVED, 5772);
    response3.addParameter(CMQCFC.MQIACH_BYTES_SENT, 6984);
    response3.addParameter(CMQCFC.MQCACH_CONNECTION_NAME, "172.17.0.2 ");
    response3.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_TYPE, 1011);
    response3.addParameter(CMQCFC.MQIACH_MSGS, 17);
    response3.addParameter(CMQCFC.MQIACH_CHANNEL_STATUS, 3);
    response3.addParameter(CMQCFC.MQIACH_CHANNEL_SUBSTATE, 300);

    return new PCFMessage[] {response1, response2, response3};
  }

  @Test
  void testPublishMetrics_nullResponse() throws Exception {
    when(pcfMessageAgent.send(any(PCFMessage.class))).thenReturn(null);
    classUnderTest = new ChannelMetricsCollector(context, metricCreator);

    classUnderTest.publishMetrics();

    verifyNoInteractions(metricWriteHelper);
  }

  @Test
  void testPublishMetrics_emptyResponse() throws Exception {
    when(pcfMessageAgent.send(any(PCFMessage.class))).thenReturn(new PCFMessage[] {});
    classUnderTest = new ChannelMetricsCollector(context, metricCreator);

    classUnderTest.publishMetrics();

    verifyNoInteractions(metricWriteHelper);
  }

  @ParameterizedTest
  @MethodSource("exceptionsToThrow")
  void testPublishMetrics_pfException(Exception exceptionToThrow) throws Exception {
    when(pcfMessageAgent.send(any(PCFMessage.class))).thenThrow(exceptionToThrow);
    classUnderTest = new ChannelMetricsCollector(context, metricCreator);

    classUnderTest.publishMetrics();

    verify(metricWriteHelper, times(1)).transformAndPrintMetrics(pathCaptor.capture());

    // Even when exception is thrown, the active channels are still reported
    List<List<Metric>> allValues = pathCaptor.getAllValues();
    assertThat(allValues).hasSize(1);
    assertThat(allValues.get(0)).hasSize(1);
    assertThatMetric(allValues.get(0).get(0))
        .hasName("ActiveChannelsCount")
        .hasPath(
            "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|ActiveChannelsCount")
        .hasValue("0")
        .withPropertiesMatching(standardPropsForAlias("ActiveChannelsCount"));
  }

  @Test
  void noMetricsToReport() throws Exception {
    classUnderTest = new ChannelMetricsCollector(context, metricCreator);
    classUnderTest.publishMetrics();
    verifyNoInteractions(metricWriteHelper);
    classUnderTest = new ChannelMetricsCollector(context, metricCreator);
    classUnderTest.publishMetrics();
    verifyNoInteractions(metricWriteHelper);
  }

  static Stream<Arguments> exceptionsToThrow() {
    return Stream.of(
        arguments(new RuntimeException("KBAOOM")),
        arguments(new PCFException(91, MQRCCF_CHL_STATUS_NOT_FOUND, "flimflam")),
        arguments(new PCFException(4, MQRC_SELECTOR_ERROR, "shazbot")),
        arguments(new PCFException(4, 42, "boz")));
  }
}
