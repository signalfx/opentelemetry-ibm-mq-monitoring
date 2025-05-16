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

import com.appdynamics.extensions.Constants;
import com.appdynamics.extensions.util.CryptoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
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

  public WMQMonitor(
      ConfigWrapper config,
      ExecutorService threadPool,
      OpenTelemetryMetricWriteHelper metricWriteHelper) {
    assert (metricWriteHelper != null);
    this.config = config;
    this.threadPool = threadPool;
    this.metricWriteHelper = metricWriteHelper;
  }

  protected String getDefaultMetricPrefix() {
    return "Custom Metrics|WMQMonitor|";
  }

  public String getMonitorName() {
    return "WMQMonitor";
  }

  @Override
  public void run() {
    configureSecurity();

    List<Map<String, ?>> queueManagers = getQueueManagers();
    ObjectMapper mapper = new ObjectMapper();

    for (Map<String, ?> queueManager : queueManagers) {
      QueueManager qManager = mapper.convertValue(queueManager, QueueManager.class);
      WMQMonitorTask task = new WMQMonitorTask(config, metricWriteHelper, qManager, threadPool);
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

    String encryptionKey = config.getEncryptionKey();

    configureTrustStore(sslConnection, encryptionKey);
    configureKeyStore(sslConnection, encryptionKey);
  }

  private void configureTrustStore(Map<String, String> sslConnection, String encryptionKey) {
    String trustStorePath = sslConnection.get("trustStorePath");
    if (Strings.isNullOrEmpty(trustStorePath)) {
      logger.debug(
          "trustStorePath is not set in config.yml, ignoring setting trustStorePath as system property");
      return;
    }

    System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    logger.debug("System property set for javax.net.ssl.trustStore is {}", trustStorePath);

    String trustStorePassword =
        getPassword(
            sslConnection.get("trustStorePassword"),
            sslConnection.get("trustStoreEncryptedPassword"),
            encryptionKey);

    if (!Strings.isNullOrEmpty(trustStorePassword)) {
      System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
      logger.debug("System property set for javax.net.ssl.trustStorePassword is xxxxx");
    }
  }

  private void configureKeyStore(Map<String, String> sslConnection, String encryptionKey) {
    String keyStorePath = sslConnection.get("keyStorePath");
    if (Strings.isNullOrEmpty(keyStorePath)) {
      logger.debug(
          "keyStorePath is not set in config.yml, ignoring setting keyStorePath as system property");
      return;
    }

    System.setProperty("javax.net.ssl.keyStore", keyStorePath);
    logger.debug("System property set for javax.net.ssl.keyStore is {}", keyStorePath);
    String keyStorePassword =
        getPassword(
            sslConnection.get("keyStorePassword"),
            sslConnection.get("keyStoreEncryptedPassword"),
            encryptionKey);
    if (!Strings.isNullOrEmpty(keyStorePassword)) {
      System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);
      logger.debug("System property set for javax.net.ssl.keyStorePassword is xxxxx");
    }
  }

  private String getPassword(String password, String encryptedPassword, String encryptionKey) {
    if (!Strings.isNullOrEmpty(password)) {
      return password;
    }

    if (!Strings.isNullOrEmpty(encryptionKey) && !Strings.isNullOrEmpty(encryptedPassword)) {
      Map<String, String> cryptoMap = Maps.newHashMap();
      cryptoMap.put(Constants.ENCRYPTED_PASSWORD, encryptedPassword);
      cryptoMap.put(Constants.ENCRYPTION_KEY, encryptionKey);
      return CryptoUtils.getPassword(cryptoMap);
    }
    return null;
  }
}
