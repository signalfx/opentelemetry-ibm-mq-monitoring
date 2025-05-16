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
package com.splunk.ibm.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import io.opentelemetry.api.metrics.LongGauge;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WMQMonitor implements Runnable {

  public static final Logger logger = LoggerFactory.getLogger(WMQMonitor.class);

  private final OpenTelemetryMetricWriteHelper metricWriteHelper;
  private final ExecutorService threadPool;
  private final ConfigWrapper config;
  private final LongGauge heartbeatGauge;

  public WMQMonitor(
      ConfigWrapper config,
      ExecutorService threadPool,
      OpenTelemetryMetricWriteHelper metricWriteHelper) {
    assert (metricWriteHelper != null);
    this.config = config;
    this.threadPool = threadPool;
    this.metricWriteHelper = metricWriteHelper;
    this.heartbeatGauge =
        metricWriteHelper.getMeter().gaugeBuilder("mq.heartbeat").setUnit("1").ofLongs().build();
  }

  @Override
  public void run() {
    configureSecurity();

    List<Map<String, ?>> queueManagers = getQueueManagers();
    ObjectMapper mapper = new ObjectMapper();

    for (Map<String, ?> queueManager : queueManagers) {
      QueueManager qManager = mapper.convertValue(queueManager, QueueManager.class);
      WMQMonitorTask task =
          new WMQMonitorTask(config, metricWriteHelper, qManager, threadPool, heartbeatGauge);
      threadPool.submit(new TaskJob((String) queueManager.get("name"), task));
    }
  }

  @NotNull
  private List<Map<String, ?>> getQueueManagers() {
    List<Map<String, ?>> queueManagers = config.getQueueManagers();
    if (queueManagers.isEmpty()) {
      throw new IllegalStateException(
          "The 'queueManagers' section in config.yml is empty or otherwise incorrect.");
    }
    return queueManagers;
  }

  private void configureSecurity() {
    Map<String, String> sslConnection = config.getSslConnection();
    if (sslConnection.isEmpty()) {
      logger.debug(
          "ssl truststore and keystore are not configured in config.yml, if SSL is enabled, pass them as jvm args");
      return;
    }

    configureTrustStore(sslConnection);
    configureKeyStore(sslConnection);
  }

  private void configureTrustStore(Map<String, String> sslConnection) {
    String trustStorePath = sslConnection.get("trustStorePath");
    if (Strings.isNullOrEmpty(trustStorePath)) {
      logger.debug(
          "trustStorePath is not set in config.yml, ignoring setting trustStorePath as system property");
      return;
    }

    System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    logger.debug("System property set for javax.net.ssl.trustStore is {}", trustStorePath);

    String trustStorePassword = sslConnection.get("trustStorePassword");

    if (!Strings.isNullOrEmpty(trustStorePassword)) {
      System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
      logger.debug("System property set for javax.net.ssl.trustStorePassword is xxxxx");
    }
  }

  private void configureKeyStore(Map<String, String> sslConnection) {
    String keyStorePath = sslConnection.get("keyStorePath");
    if (Strings.isNullOrEmpty(keyStorePath)) {
      logger.debug(
          "keyStorePath is not set in config.yml, ignoring setting keyStorePath as system property");
      return;
    }

    System.setProperty("javax.net.ssl.keyStore", keyStorePath);
    logger.debug("System property set for javax.net.ssl.keyStore is {}", keyStorePath);
    String keyStorePassword = sslConnection.get("keyStorePassword");
    if (!Strings.isNullOrEmpty(keyStorePassword)) {
      System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);
      logger.debug("System property set for javax.net.ssl.keyStorePassword is xxxxx");
    }
  }
}
