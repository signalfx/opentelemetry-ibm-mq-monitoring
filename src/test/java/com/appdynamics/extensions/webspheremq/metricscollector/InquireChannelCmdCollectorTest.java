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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InquireChannelCmdCollectorTest {

  InquireChannelCmdCollector classUnderTest;

  @Mock AMonitorJob aMonitorJob;

  @Mock PCFMessageAgent pcfMessageAgent;

  @Mock MetricWriteHelper metricWriteHelper;

  MonitorContextConfiguration monitorContextConfig;

  ArgumentCaptor<List> pathCaptor;
  MetricCreator metricCreator;
  MetricsCollectorContext context;

  @BeforeEach
  public void setup() {
    monitorContextConfig =
        new MonitorContextConfiguration(
            "WMQMonitor",
            "Custom Metrics|WMQMonitor|",
            PathResolver.resolveDirectory(InquireChannelCmdCollectorTest.class),
            aMonitorJob);
    monitorContextConfig.setConfigYml("src/test/resources/conf/config.yml");
    Map<String, ?> configMap = monitorContextConfig.getConfigYml();
    ObjectMapper mapper = new ObjectMapper();
    QueueManager queueManager =
        mapper.convertValue(((List) configMap.get("queueManagers")).get(0), QueueManager.class);
    Map<String, Map<String, WMQMetricOverride>> metricsMap =
        WMQUtil.getMetricsToReportFromConfigYml((List<Map>) configMap.get("mqMetrics"));
    Map<String, WMQMetricOverride> channelMetrics = metricsMap.get(Constants.METRIC_TYPE_CHANNEL);
    Map<String, Map<String, WMQMetricOverride>> metricsByCommand = new HashMap<>();
    for (String key : channelMetrics.keySet()) {
      WMQMetricOverride wmqOverride = channelMetrics.get(key);
      String cmd =
          wmqOverride.getIbmCommand() == null
              ? "MQCMD_INQUIRE_CHANNEL_STATUS"
              : wmqOverride.getIbmCommand();
      metricsByCommand.putIfAbsent(cmd, new HashMap<>());
      metricsByCommand.get(cmd).put(key, wmqOverride);
    }
    Map<String, WMQMetricOverride> channelMetricsToReport =
        metricsByCommand.get("MQCMD_INQUIRE_CHANNEL");
    pathCaptor = ArgumentCaptor.forClass(List.class);
    metricCreator =
        new MetricCreator(
            monitorContextConfig.getMetricPrefix(),
            queueManager,
            InquireChannelCmdCollector.ARTIFACT);
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
  public void testProcessPCFRequestAndPublishQMetricsForInquireQStatusCmd() throws Exception {
    when(pcfMessageAgent.send(any(PCFMessage.class)))
        .thenReturn(createPCFResponseForInquireChannelCmd());
    classUnderTest = new InquireChannelCmdCollector(context, metricCreator);
    classUnderTest.publishMetrics();
    verify(metricWriteHelper, times(1)).transformAndPrintMetrics(pathCaptor.capture());
    List<String> metricPathsList = Lists.newArrayList();
    metricPathsList.add(
        "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|my.channel|MsgRetryCount");
    metricPathsList.add(
        "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|my.channel|MsgsReceived");
    metricPathsList.add(
        "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|my.channel|MsgsSent");

    for (List<Metric> metricList : pathCaptor.getAllValues()) {
      for (Metric metric : metricList) {
        if (metricPathsList.contains(metric.getMetricPath())) {
          if (metric
              .getMetricPath()
              .equals(
                  "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|my.channel|MsgRetryCount")) {
            assertThat(metric.getMetricValue()).isEqualTo("22");
          }
          if (metric
              .getMetricPath()
              .equals(
                  "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|my.channel|MsgsReceived")) {
            assertThat(metric.getMetricValue()).isEqualTo("42");
          }
          if (metric
              .getMetricPath()
              .equals(
                  "Server|Component:Tier1|Custom Metrics|WebsphereMQ|QueueManager1|Channels|my.channel|MsgsSent")) {
            assertThat(metric.getMetricValue()).isEqualTo("64");
          }
        }
      }
    }
  }

  private PCFMessage[] createPCFResponseForInquireChannelCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_CHANNEL, 1, true);
    response1.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, "my.channel");
    response1.addParameter(CMQCFC.MQIACH_MR_COUNT, 22);
    response1.addParameter(CMQCFC.MQIACH_MSGS_RECEIVED, 42);
    response1.addParameter(CMQCFC.MQIACH_MSGS_SENT, 64);

    return new PCFMessage[] {response1};
  }
}
