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

import static org.assertj.core.api.Assertions.fail;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.yml.YmlReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.integration.opentelemetry.TestResultMetricExporter;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

  @BeforeAll
  public static void sendClientMessages() throws Exception {
    String configFile = getConfigFile("conf/test-config.yml");
    Map<String, ?> config = YmlReader.readFromFileAsMap(new File(configFile));
    Map<String, ?> queueManagerConfig =
        (Map<String, ?>) ((List) config.get("queueManagers")).get(0);
    ObjectMapper mapper = new ObjectMapper();
    QueueManager qManager = mapper.convertValue(queueManagerConfig, QueueManager.class);
    JakartaPutGet.runPutGet(qManager, "myqueue", 10, 1);

    service.submit(() -> JakartaPutGet.runPutGet(qManager, "myqueue", 1000000, 100));
  }

  @AfterAll
  public static void stopSendingClientMessages() throws Exception {
    service.shutdown();
  }

  @Test
  void test_monitor_with_full_config() throws Exception {
    logger.info("\n\n\n\n\n\nRunning test: test_monitor_with_full_config");
    TestResultMetricExporter testExporter = new TestResultMetricExporter();
    MetricWriteHelper metricWriteHelper = new OpenTelemetryMetricWriteHelper(testExporter);
    String configFile = getConfigFile("conf/test-config.yml");

    TestWMQMonitor monitor = new TestWMQMonitor(configFile, metricWriteHelper);
    monitor.testrun();
  }

  @Test
  void test_wmqmonitor() throws Exception {
    logger.info("\n\n\n\n\n\nRunning test: test_wmqmonitor");
    TestResultMetricExporter testExporter = new TestResultMetricExporter();
    MetricWriteHelper metricWriteHelper = new OpenTelemetryMetricWriteHelper(testExporter);
    String configFile = getConfigFile("conf/test-queuemgr-config.yml");

    TestWMQMonitor monitor = new TestWMQMonitor(configFile, metricWriteHelper);
    monitor.testrun();
  }
}
