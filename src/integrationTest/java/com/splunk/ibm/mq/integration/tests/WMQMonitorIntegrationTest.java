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

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.yml.YmlReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibm.mq.pcf.CMQC;
import com.splunk.ibm.mq.WMQMonitorTask;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.integration.opentelemetry.TestResultMetricExporter;
import com.splunk.ibm.mq.opentelemetry.Main;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
          1,
          r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(
                (t, e) -> {
                  logger.error("Uncaught exception", e);
                  fail(e.getMessage());
                });
            thread.setDaemon(true);
            return thread;
          });

  private static QueueManager getQueueManagerConfig() throws URISyntaxException {
    String configFile = getConfigFile("conf/test-config.yml");
    Map<String, ?> config = YmlReader.readFromFileAsMap(new File(configFile));
    Map<String, ?> queueManagerConfig =
        (Map<String, ?>) ((List) config.get("queueManagers")).get(0);
    ObjectMapper mapper = new ObjectMapper();
    QueueManager qManager = mapper.convertValue(queueManagerConfig, QueueManager.class);
    return qManager;
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
    MQQueueManager ibmQueueManager = WMQMonitorTask.connectToQueueManager(manager, null);
    PCFMessageAgent agent = WMQMonitorTask.initPCFMesageAgent(manager, ibmQueueManager);
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
    service.shutdown();
  }

  @BeforeEach
  void setUpEvents() throws Exception {
    QueueManager qManager = getQueueManagerConfig();

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
    Map<String, SdkMeterProvider> providers = Main.createSdkMeterProviders(reader, "QM1");
    Map<String, Meter> meters = new HashMap<>();
    for (Map.Entry<String, SdkMeterProvider> e : providers.entrySet()) {
      meters.put(e.getKey(), e.getValue().get("opentelemetry.io/mq"));
    }
    MetricWriteHelper metricWriteHelper = new OpenTelemetryMetricWriteHelper(testExporter, meters);
    String configFile = getConfigFile("conf/test-config.yml");

    TestWMQMonitor monitor = new TestWMQMonitor(configFile, metricWriteHelper);
    monitor.testrun();

    reader.forceFlush().join(5, TimeUnit.SECONDS);
    for (SdkMeterProvider provider : providers.values()) {
      provider.close();
    }
    List<MetricData> data = testExporter.getExportedMetrics();
    Set<String> metricNames = new HashSet<>();
    for (MetricData metricData : data) {
      metricNames.add(metricData.getName());
    }
    // this value is read from the configuration queue.
    assertThat(metricNames).contains("mq.manager.max.handles");
    // this value is read from the performance event queue.
    assertThat(metricNames).contains("mq.queue.depth.full.event");
    // this value is read from the performance event queue.
    assertThat(metricNames).contains("mq.queue.depth.high.event");
    assertThat(metricNames).contains("mq.queue.depth.low.event");
  }

  @Test
  void test_wmqmonitor() throws Exception {
    logger.info("\n\n\n\n\n\nRunning test: test_wmqmonitor");
    TestResultMetricExporter testExporter = new TestResultMetricExporter();
    MetricReader reader =
        PeriodicMetricReader.builder(testExporter)
            .setExecutor(Executors.newScheduledThreadPool(1))
            .build();
    Map<String, SdkMeterProvider> providers = Main.createSdkMeterProviders(reader, "QM1");
    Map<String, Meter> meters = new HashMap<>();
    for (Map.Entry<String, SdkMeterProvider> e : providers.entrySet()) {
      meters.put(e.getKey(), e.getValue().get("opentelemetry.io/mq"));
    }
    MetricWriteHelper metricWriteHelper = new OpenTelemetryMetricWriteHelper(testExporter, meters);
    String configFile = getConfigFile("conf/test-queuemgr-config.yml");

    TestWMQMonitor monitor = new TestWMQMonitor(configFile, metricWriteHelper);
    monitor.testrun();
  }
}
