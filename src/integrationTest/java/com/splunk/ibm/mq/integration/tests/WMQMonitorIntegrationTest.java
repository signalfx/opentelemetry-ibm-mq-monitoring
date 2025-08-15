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
package com.splunk.ibm.mq.integration.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.integration.opentelemetry.TestResultMetricExporter;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import com.splunk.ibm.mq.opentelemetry.Main;
import com.splunk.ibm.mq.util.WMQUtil;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration Test for WMQMonitor */
class WMQMonitorIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(WMQMonitorIntegrationTest.class);

  private static final ExecutorService service =
      Executors.newFixedThreadPool(
          4, /* one gets burned with our @BeforeAll message uzi, 4 is faster than 2 */
          r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(
                (t, e) -> {
                  logger.error("Uncaught exception", e);
                  fail(e.getMessage());
                });
            thread.setDaemon(true);
            thread.setName("WMQMonitorIntegrationTest");
            return thread;
          });

  private static QueueManager getQueueManagerConfig() throws Exception {
    String configFile = getConfigFile("conf/test-config.yml");
    ConfigWrapper wrapper = ConfigWrapper.parse(configFile);
    Map<String, ?> queueManagerConfig = wrapper.getQueueManagers().get(0);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(queueManagerConfig, QueueManager.class);
  }

  @NotNull
  private static String getConfigFile(String resourcePath) throws URISyntaxException {
    URL resource = WMQMonitorIntegrationTest.class.getClassLoader().getResource(resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("file not found!");
    }

    File file = Paths.get(resource.toURI()).toFile();
    logger.info("Config file: {}", file.getAbsolutePath());
    return file.getAbsolutePath();
  }

  private static void configureQueueManager(QueueManager manager) {
    MQQueueManager ibmQueueManager = WMQUtil.connectToQueueManager(manager);
    PCFMessageAgent agent = WMQUtil.initPCFMessageAgent(manager, ibmQueueManager);
    PCFMessage request = new PCFMessage(CMQCFC.MQCMD_CHANGE_Q_MGR);
    // turn on emitting authority events
    request.addParameter(CMQC.MQIA_AUTHORITY_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting configuration events
    request.addParameter(CMQC.MQIA_CONFIGURATION_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting channel auto-definition events
    request.addParameter(CMQC.MQIA_CHANNEL_AUTO_DEF_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting channel events
    request.addParameter(CMQC.MQIA_CHANNEL_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting command events
    request.addParameter(CMQC.MQIA_COMMAND_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting inhibit events
    request.addParameter(CMQC.MQIA_INHIBIT_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting local events
    request.addParameter(CMQC.MQIA_LOCAL_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting performance events
    request.addParameter(CMQC.MQIA_PERFORMANCE_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting remote events
    request.addParameter(CMQC.MQIA_REMOTE_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting SSL events
    request.addParameter(CMQC.MQIA_SSL_EVENT, CMQCFC.MQEVR_ENABLED);
    // turn on emitting start/stop events
    request.addParameter(CMQC.MQIA_START_STOP_EVENT, CMQCFC.MQEVR_ENABLED);
    try {
      agent.send(request);
    } catch (Exception e) {
      if (e instanceof PCFException) {
        PCFMessage[] msgs = (PCFMessage[]) ((PCFException) e).exceptionSource;
        for (PCFMessage msg : msgs) {
          logger.error(msg.toString());
        }
      }
      throw new RuntimeException(e);
    }
  }

  @BeforeAll
  public static void sendClientMessages() throws Exception {
    QueueManager qManager = getQueueManagerConfig();
    configureQueueManager(qManager);

    // create a queue and fill it up past its capacity.
    JakartaPutGet.createQueue(qManager, "smallqueue", 10);

    JakartaPutGet.runPutGet(qManager, "myqueue", 10, 1);

    service.submit(() -> JakartaPutGet.runPutGet(qManager, "myqueue", 1000000, 100));
  }

  @AfterAll
  public static void stopSendingClientMessages() throws Exception {
    QueueManager qManager = getQueueManagerConfig();
    configureQueueManager(qManager);

    service.shutdown();
  }

  @BeforeEach
  void setUpEvents() throws Exception {
    QueueManager qManager = getQueueManagerConfig();
    // try to login with a bad password:
    JakartaPutGet.tryLoginWithBadPassword(qManager);

    JakartaPutGet.sendMessages(qManager, "smallqueue", 1);
    Thread.sleep(1000);
    JakartaPutGet.sendMessages(qManager, "smallqueue", 8);
    Thread.sleep(1000);
    JakartaPutGet.sendMessages(qManager, "smallqueue", 5);
  }

  @AfterEach
  void clearQueue() throws Exception {
    // clear the full queue.
    JakartaPutGet.readMessages(getQueueManagerConfig(), "smallqueue");
  }

  @Test
  void test_monitor_with_full_config() throws Exception {
    logger.info("\n\n\n\n\n\nRunning test: test_monitor_with_full_config");
    TestResultMetricExporter testExporter = new TestResultMetricExporter();
    MetricReader reader =
        PeriodicMetricReader.builder(testExporter)
            .setExecutor(Executors.newScheduledThreadPool(1))
            .build();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    String configFile = getConfigFile("conf/test-config.yml");

    ConfigWrapper config = ConfigWrapper.parse(configFile);
    TestWMQMonitor monitor =
        new TestWMQMonitor(config, meterProvider.get("opentelemetry.io/mq"), service);
    monitor.runTest();

    reader.forceFlush().join(5, TimeUnit.SECONDS);
    meterProvider.close();
    List<MetricData> data = testExporter.getExportedMetrics();
    Map<String, MetricData> metrics = new HashMap<>();
    for (MetricData metricData : data) {
      metrics.put(metricData.getName(), metricData);
    }
    Set<String> metricNames = metrics.keySet();
    // this value is read from the active channels count:
    assertThat(metricNames).contains("mq.manager.active.channels");
    // this value is read from the configuration queue.
    assertThat(metricNames).contains("mq.manager.max.handles");
    // this value is read from the queue manager events, for unauthorized events.
    assertThat(metricNames).contains("mq.unauthorized.event");
    // this value is read from the performance event queue.
    assertThat(metricNames).contains("mq.queue.depth.full.event");
    // this value is read from the performance event queue.
    assertThat(metricNames).contains("mq.queue.depth.high.event");
    assertThat(metricNames).contains("mq.queue.depth.low.event");
    // reads a value from the heartbeat gauge
    assertThat(metricNames).contains("mq.heartbeat");
    assertThat(metricNames).contains("mq.oldest.msg.age");
    if (metrics.get("mq.oldest.msg.age") != null) {
      Set<String> queueNames =
          metrics.get("mq.oldest.msg.age").getLongGaugeData().getPoints().stream()
              .map(pt -> pt.getAttributes().get(AttributeKey.stringKey("queue.name")))
              .collect(Collectors.toSet());
      assertThat(queueNames).contains("smallqueue");
    }
    // make sure we get MQ manager status
    assertThat(metricNames).contains("mq.manager.status");
    if (metrics.get("mq.manager.status") != null) {
      Set<String> queueManagers =
          metrics.get("mq.manager.status").getLongGaugeData().getPoints().stream()
              .map(pt -> pt.getAttributes().get(AttributeKey.stringKey("queue.manager")))
              .collect(Collectors.toSet());
      assertThat(queueManagers).contains("QM1");
    }

    assertThat(metricNames).contains("mq.onqtime.2");
    if (metrics.get("mq.onqtime.2") != null) {
      Set<String> queueNames =
          metrics.get("mq.onqtime.2").getLongGaugeData().getPoints().stream()
              .map(pt -> pt.getAttributes().get(AttributeKey.stringKey("queue.name")))
              .collect(Collectors.toSet());
      assertThat(queueNames).contains("smallqueue");
      Set<String> queueManagers =
          metrics.get("mq.manager.status").getLongGaugeData().getPoints().stream()
              .map(pt -> pt.getAttributes().get(AttributeKey.stringKey("queue.manager")))
              .collect(Collectors.toSet());
      assertThat(queueManagers).contains("QM1");
    }
  }

  @Test
  void test_wmqmonitor() throws Exception {
    logger.info("\n\n\n\n\n\nRunning test: test_wmqmonitor");
    TestResultMetricExporter testExporter = new TestResultMetricExporter();
    MetricReader reader =
        PeriodicMetricReader.builder(testExporter)
            .setExecutor(Executors.newScheduledThreadPool(1))
            .build();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    String configFile = getConfigFile("conf/test-queuemgr-config.yml");
    ConfigWrapper config = ConfigWrapper.parse(configFile);

    TestWMQMonitor monitor =
        new TestWMQMonitor(config, meterProvider.get("opentelemetry.io/mq"), service);
    monitor.runTest();
  }

  @Test
  void test_otlphttp() throws Exception {
    logger.info("\n\n\n\n\n\nRunning test: test_otlphttp");
    ConfigWrapper config =
        ConfigWrapper.parse(WMQMonitorIntegrationTest.getConfigFile("conf/test-config.yml"));
    ScheduledExecutorService service =
        Executors.newScheduledThreadPool(config.getNumberOfThreads());
    TestResultMetricExporter exporter = new TestResultMetricExporter();
    Main.run(config, service, exporter);
    Thread.sleep(5000);
    service.shutdown();
    service.awaitTermination(10, TimeUnit.SECONDS);

    List<MetricData> data = exporter.getExportedMetrics();
    Set<String> metricNames = new HashSet<>();
    for (MetricData metricData : data) {
      metricNames.add(metricData.getName());
    }
    // this value is read from the active channels count:
    assertThat(metricNames).contains("mq.manager.active.channels");
    // this value is read from the configuration queue.
    assertThat(metricNames).contains("mq.manager.max.handles");
    // this value is read from the queue manager events, for unauthorized events.
    assertThat(metricNames).contains("mq.unauthorized.event");
    // this value is read from the performance event queue.
    assertThat(metricNames).contains("mq.queue.depth.full.event");
    // this value is read from the performance event queue.
    assertThat(metricNames).contains("mq.queue.depth.high.event");
    assertThat(metricNames).contains("mq.queue.depth.low.event");
    // reads a value from the heartbeat gauge
    assertThat(metricNames).contains("mq.heartbeat");
  }
}
