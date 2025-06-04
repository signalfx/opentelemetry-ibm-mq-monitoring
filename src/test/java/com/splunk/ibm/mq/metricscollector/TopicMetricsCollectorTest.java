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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.integration.opentelemetry.TestResultMetricExporter;
import com.splunk.ibm.mq.metrics.MetricsConfig;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TopicMetricsCollectorTest {
  private TopicMetricsCollector classUnderTest;

  @Mock private PCFMessageAgent pcfMessageAgent;

  private QueueManager queueManager;
  private ConfigWrapper config;
  private ExecutorService threadPool;

  @BeforeEach
  void setup() throws Exception {
    config = ConfigWrapper.parse("src/test/resources/conf/config.yml");
    threadPool = Executors.newFixedThreadPool(config.getNumberOfThreads());
    ObjectMapper mapper = new ObjectMapper();
    queueManager = mapper.convertValue(config.getQueueManagers().get(0), QueueManager.class);
  }

  @Test
  void testPublishMetrics() throws Exception {
    TestResultMetricExporter testExporter = new TestResultMetricExporter();
    PeriodicMetricReader reader =
        PeriodicMetricReader.builder(testExporter)
            .setExecutor(Executors.newScheduledThreadPool(1))
            .build();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();

    MetricsCollectorContext context =
        new MetricsCollectorContext(
            queueManager, pcfMessageAgent, null, new MetricsConfig(config._exposed()));
    classUnderTest = new TopicMetricsCollector(meterProvider.get("opentelemetry.io/mq"));

    when(pcfMessageAgent.send(any(PCFMessage.class)))
        .thenReturn(createPCFResponseForInquireTopicStatusCmd());

    classUnderTest.accept(context);
    reader.forceFlush().join(1, TimeUnit.SECONDS);

    List<String> metricsList = Arrays.asList("mq.publish.count", "mq.subscription.count");

    for (MetricData metric : testExporter.getExportedMetrics()) {
      if (metricsList.remove(metric.getName())) {
        if (metric.getName().equals("mq.publish.count")) {
          Set<Long> values = new HashSet<>();
          values.add(2L);
          values.add(3L);
          assertThat(
                  metric.getLongGaugeData().getPoints().stream()
                      .map(LongPointData::getValue)
                      .collect(Collectors.toSet()))
              .isEqualTo(values);
        }
        if (metric.getName().equals("mq.subscription.count")) {
          Set<Long> values = new HashSet<>();
          values.add(3L);
          values.add(4L);
          assertThat(
                  metric.getLongGaugeData().getPoints().stream()
                      .map(LongPointData::getValue)
                      .collect(Collectors.toSet()))
              .isEqualTo(values);
        }
      }
    }
    assertThat(metricsList).isEmpty();
  }

  private PCFMessage[] createPCFResponseForInquireTopicStatusCmd() {
    PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, 1, false);
    response1.addParameter(CMQC.MQCA_TOPIC_STRING, "test");
    response1.addParameter(CMQC.MQIA_PUB_COUNT, 2);
    response1.addParameter(CMQC.MQIA_SUB_COUNT, 3);

    PCFMessage response2 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, 2, false);
    response2.addParameter(CMQC.MQCA_TOPIC_STRING, "dev");
    response2.addParameter(CMQC.MQIA_PUB_COUNT, 3);
    response2.addParameter(CMQC.MQIA_SUB_COUNT, 4);

    PCFMessage response3 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, 3, false);
    response3.addParameter(CMQC.MQCA_TOPIC_STRING, "system");
    response3.addParameter(CMQC.MQIA_PUB_COUNT, 5);
    response3.addParameter(CMQC.MQIA_SUB_COUNT, 6);

    return new PCFMessage[] {response1, response2, response3};
  }
}
