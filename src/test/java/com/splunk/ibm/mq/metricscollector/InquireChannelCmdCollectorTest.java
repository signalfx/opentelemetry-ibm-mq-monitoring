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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.CMQXC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.integration.opentelemetry.TestResultMetricExporter;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import com.splunk.ibm.mq.opentelemetry.Writer;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InquireChannelCmdCollectorTest {

  InquireChannelCmdCollector classUnderTest;

  @Mock PCFMessageAgent pcfMessageAgent;

  Writer metricWriteHelper;

  MetricsCollectorContext context;
  private TestResultMetricExporter testExporter;
  private PeriodicMetricReader reader;

  @BeforeEach
  public void setup() throws Exception {
    ConfigWrapper config = ConfigWrapper.parse("src/test/resources/conf/config.yml");
    ObjectMapper mapper = new ObjectMapper();
    QueueManager queueManager =
        mapper.convertValue(config.getQueueManagers().get(0), QueueManager.class);
    testExporter = new TestResultMetricExporter();
    reader =
        PeriodicMetricReader.builder(testExporter)
            .setExecutor(Executors.newScheduledThreadPool(1))
            .build();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    metricWriteHelper = new Writer(reader, testExporter, meterProvider.get("opentelemetry.io/mq"));
    context = new MetricsCollectorContext(queueManager, pcfMessageAgent, metricWriteHelper);
  }

  @Test
  public void testProcessPCFRequestAndPublishQMetricsForInquireQStatusCmd() throws Exception {
    when(pcfMessageAgent.send(any(PCFMessage.class)))
        .thenReturn(createPCFResponseForInquireChannelCmd());
    classUnderTest = new InquireChannelCmdCollector(context);
    classUnderTest.run();
    reader.forceFlush().join(1, TimeUnit.SECONDS);
    List<String> metricsList =
        Lists.newArrayList(
            "mq.message.retry.count", "mq.message.received.count", "mq.message.sent.count");
    for (MetricData metric : testExporter.getExportedMetrics()) {
      if (metricsList.remove(metric.getName())) {
        if (metric.getName().equals("mq.message.retry.count")) {
          assertThat(metric.getLongGaugeData().getPoints().iterator().next().getValue())
              .isEqualTo(22);
        }
        if (metric.getName().equals("mq.message.received.count")) {
          assertThat(metric.getLongGaugeData().getPoints().iterator().next().getValue())
              .isEqualTo(42);
        }
        if (metric.getName().equals("mq.message.sent.count")) {
          assertThat(metric.getLongGaugeData().getPoints().iterator().next().getValue())
              .isEqualTo(64);
        }
      }
    }
    assertThat(metricsList).isEmpty();
  }

  private PCFMessage[] createPCFResponseForInquireChannelCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_CHANNEL, 1, true);
    response1.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, "my.channel");
    response1.addParameter(CMQCFC.MQIACH_CHANNEL_TYPE, CMQXC.MQCHT_SVRCONN);
    response1.addParameter(CMQCFC.MQIACH_MR_COUNT, 22);
    response1.addParameter(CMQCFC.MQIACH_MSGS_RECEIVED, 42);
    response1.addParameter(CMQCFC.MQIACH_MSGS_SENT, 64);
    response1.addParameter(CMQCFC.MQIACH_MAX_INSTANCES, 3);
    response1.addParameter(CMQCFC.MQIACH_MAX_INSTS_PER_CLIENT, 3);

    return new PCFMessage[] {response1};
  }
}
