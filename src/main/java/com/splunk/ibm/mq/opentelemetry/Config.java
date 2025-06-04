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
package com.splunk.ibm.mq.opentelemetry;

import com.google.common.base.Strings;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities reading configuration and create domain objects */
class Config {

  private static final Logger logger = LoggerFactory.getLogger(Config.class);

  // TODO: Delete this boneyard. Are we confident we can do all of this?
  // TOOD: What about the magical aggregation selector? Can't we just use the default? If not, why
  // not?
  //  static MetricExporter createOtlpHttpMetricsExporter(Map<String, ?> config) {
  //    OtlpHttpMetricExporterBuilder builder = OtlpHttpMetricExporter.builder();
  //
  //    Map<String, String> props = new HashMap<>();
  //    if (config.get("otlpExporter") instanceof Map) {
  //      Map otlpConfig = (Map) config.get("otlpExporter");
  //      for (Object key : otlpConfig.keySet()) {
  //        if (key instanceof String && otlpConfig.get(key) instanceof String) {
  //          props.put((String) key, (String) otlpConfig.get(key));
  //        }
  //      }
  //    }
  //
  //    // TODO: Don't use internal classes from opentelemetry
  //    OtlpConfigUtil.configureOtlpExporterBuilder(
  //        DATA_TYPE_METRICS,
  //        DefaultConfigProperties.create(props),
  //        builder::setEndpoint,
  //        builder::addHeader,
  //        builder::setCompression,
  //        builder::setTimeout,
  //        builder::setTrustedCertificates,
  //        builder::setClientTls,
  //        builder::setRetryPolicy,
  //        builder::setMemoryMode);
  //
  //    builder.setDefaultAggregationSelector((instrumentType) -> Aggregation.lastValue());
  //    return builder.build();
  //  }

  static void setUpSSLConnection(Map<String, ?> config) {
    if (config.get("sslConnection") instanceof Map) {
      Map otlpConfig = (Map) config.get("sslConnection");

      getConfigValueAndSetSystemProperty(otlpConfig, "keyStorePath", "javax.net.ssl.keyStore");
      getConfigValueAndSetSystemProperty(
          otlpConfig, "keyStorePassword", "javax.net.ssl.keyStorePassword");
      getConfigValueAndSetSystemProperty(
          otlpConfig, "trustStorePath", "javax.net.ssl.trustStorePath");
      getConfigValueAndSetSystemProperty(
          otlpConfig, "trustStorePassword", "javax.net.ssl.trustStorePassword");
    }
  }

  private static void getConfigValueAndSetSystemProperty(
      Map otlpConfig, String configKey, String systemKey) {
    Object configValue = otlpConfig.get(configKey);
    if (configValue instanceof String && !((String) configValue).trim().isEmpty()) {
      System.setProperty(systemKey, (String) configValue);
    }
  }

  static void configureSecurity(ConfigWrapper config) {
    Map<String, String> sslConnection = config.getSslConnection();
    if (sslConnection.isEmpty()) {
      logger.debug(
          "ssl truststore and keystore are not configured in config.yml, if SSL is enabled, pass them as jvm args");
      return;
    }

    configureTrustStore(sslConnection);
    configureKeyStore(sslConnection);
  }

  private static void configureTrustStore(Map<String, String> sslConnection) {
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

  private static void configureKeyStore(Map<String, String> sslConnection) {
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
