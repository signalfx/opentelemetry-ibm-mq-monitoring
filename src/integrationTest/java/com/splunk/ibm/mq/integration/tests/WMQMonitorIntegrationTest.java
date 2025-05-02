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

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.opentelemetry.OpenTelemetryMetricWriteHelper;
import com.splunk.ibm.mq.integration.opentelemetry.TestResultMetricExporter;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Integration Test for WMQMonitor */
class WMQMonitorIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(WMQMonitorIntegrationTest.class);

  @NotNull
  private String getConfigFile(String resourcePath) throws URISyntaxException {
    URL resource = getClass().getClassLoader().getResource(resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("file not found!");
    }

    File file = Paths.get(resource.toURI()).toFile();
    logger.info("Config file: {}", file.getAbsolutePath());
    return file.getAbsolutePath();
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
