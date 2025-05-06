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
package com.appdynamics.extensions.webspheremq;

import com.appdynamics.extensions.Constants;
import com.appdynamics.extensions.opentelemetry.OpenTelemetryMetricWriteHelper;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.extensions.util.CryptoUtils;
import com.appdynamics.extensions.util.PathResolver;
import com.appdynamics.extensions.util.StringUtils;
import com.appdynamics.extensions.util.TimeUtils;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.yml.YmlReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WMQMonitor {

  public static final Logger logger = LoggerFactory.getLogger(WMQMonitor.class);

  protected final OpenTelemetryMetricWriteHelper overrideHelper;
  protected final Map<String, ?> configMap;

  public WMQMonitor(OpenTelemetryMetricWriteHelper overrideHelper, Map<String, String> args) {
    this.overrideHelper = overrideHelper;
    File installDir = getInstallDirectory();
    configMap = readConfig(installDir, args.get("config-file"));
    List<Map> queueManagers = (List<Map>) configMap.get("queueManagers");
    AssertUtils.assertNotNull(
        queueManagers, "The 'queueManagers' section in config.yml is not initialised");
    init();
  }

  public static File resolvePath(File installDir, String path) {
    File file = PathResolver.getFile(path, installDir);
    if (file != null && file.exists()) {
      return file;
    } else {
      throw new IllegalArgumentException("The path [" + path + "] cannot be resolved to a file");
    }
  }

  public static Map<String, ?> readConfig(File installDir, String path) {
    File configFile = resolvePath(installDir, path);
    logger.info("Loading the configuration from {}", configFile.getAbsolutePath());
    return YmlReader.readFromFileAsMap(configFile);
  }

  public static File getInstallDirectory() {
    File installDir = PathResolver.resolveDirectory(WMQMonitor.class);
    if (installDir == null) {
      throw new RuntimeException("The install directory cannot be null");
    }
    return installDir;
  }

  protected String getDefaultMetricPrefix() {
    return "Custom Metrics|WMQMonitor|";
  }

  protected void doRun(ScheduledExecutorService service) {
    List<Map> queueManagers = (List<Map>) this.configMap.get("queueManagers");
    ObjectMapper mapper = new ObjectMapper();

    String metricsPrefix = (String) configMap.get("metricPrefix");
    if (metricsPrefix == null || metricsPrefix.isEmpty()) {
      metricsPrefix = getDefaultMetricPrefix();
    }
    metricsPrefix = StringUtils.trim(metricsPrefix.trim(), "|");
    for (Map queueManager : queueManagers) {
      QueueManager qManager = mapper.convertValue(queueManager, QueueManager.class);
      WMQMonitorTask wmqTask =
          new WMQMonitorTask(this.overrideHelper, this.configMap, qManager, metricsPrefix, service);
      service.submit(wmqTask);
    }
  }

  private void init() {
    Map<String, String> sslConnection = (Map<String, String>) configMap.get("sslConnection");

    if (sslConnection != null) {
      String encryptionKey = (String) configMap.get("encryptionKey");
      logger.debug("Encryption key from config.yml set for ssl connection is {}", encryptionKey);

      String trustStorePath = sslConnection.get("trustStorePath");
      if (!Strings.isNullOrEmpty(trustStorePath)) {
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
      } else {
        logger.debug(
            "trustStorePath is not set in config.yml, ignoring setting trustStorePath as system property");
      }

      String keyStorePath = sslConnection.get("keyStorePath");
      if (!Strings.isNullOrEmpty(keyStorePath)) {
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
      } else {
        logger.debug(
            "keyStorePath is not set in config.yml, ignoring setting keyStorePath as system property");
      }
    } else {
      logger.debug(
          "ssl truststore and keystore are not configured in config.yml, if SSL is enabled, pass them as jvm args");
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

  public void execute(ScheduledExecutorService service) {
    long startTime = System.currentTimeMillis();
    logger.info(
        "Started executing WMQMonitor at {}",
        TimeUtils.getFormattedTimestamp(startTime, "yyyy-MM-dd HH:mm:ss z"));
    logger.info(
        "Using WMQMonitor Version [{}]", WMQMonitor.class.getPackage().getImplementationTitle());
    try {
      doRun(service);
    } finally {
      this.overrideHelper.onComplete();
      long endTime = System.currentTimeMillis();
      logger.info(
          "End executing WMQMonitor at {}, total execution time: {}",
          TimeUtils.getFormattedTimestamp(endTime, "yyyy-MM-dd HH:mm:ss z"),
          endTime - startTime);
    }
  }
}
