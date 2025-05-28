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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splunk.ibm.mq.WMQMonitorTask;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * The TestWMQMonitor class extends the WMQMonitor class and provides a test implementation of the
 * WebSphere MQ monitoring functionality. It is intended for internal integration test purposes and
 * facilitates custom configuration through a test configuration file and a test metric write
 * helper.
 */
class TestWMQMonitor {

  private final ConfigWrapper config;
  private final ExecutorService threadPool;
  private final Meter meter;

  TestWMQMonitor(ConfigWrapper config, Meter meter, ExecutorService service) {
    this.config = config;
    this.threadPool = service;
    this.meter = meter;
  }

  /**
   * Executes a test run for monitoring WebSphere MQ queue managers based on the provided
   * configuration "testConfigFile".
   *
   * <p>The method retrieves "queueManagers" from the yml configuration file and uses a custom
   * MetricWriteHelper if provided, initializes a TasksExecutionServiceProvider, and executes the
   * WMQMonitorTask
   */
  void runTest() {
    List<Map<String, ?>> queueManagers = config.getQueueManagers();
    assertThat(queueManagers).isNotNull();
    ObjectMapper mapper = new ObjectMapper();

    LongGauge gauge = meter.gaugeBuilder("mq.heartbeat").setUnit("1").ofLongs().build();

    // we override this helper to pass in our opentelemetry helper instead.
    for (Map<String, ?> queueManager : queueManagers) {
      QueueManager qManager = mapper.convertValue(queueManager, QueueManager.class);
      WMQMonitorTask wmqTask = new WMQMonitorTask(config, meter, qManager, threadPool, gauge);
      wmqTask.run();
    }
  }
}
