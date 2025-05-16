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

import static io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil.DATA_TYPE_METRICS;

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.HashMap;
import java.util.Map;

/** Utilities reading configuration and create domain objects */
class Config {

  static MetricExporter createOtlpHttpMetricsExporter(Map<String, ?> config) {
    OtlpHttpMetricExporterBuilder builder = OtlpHttpMetricExporter.builder();

    Map<String, String> props = new HashMap<>();
    if (config.get("otlpExporter") instanceof Map) {
      Map otlpConfig = (Map) config.get("otlpExporter");
      for (Object key : otlpConfig.keySet()) {
        if (key instanceof String && otlpConfig.get(key) instanceof String) {
          props.put((String) key, (String) otlpConfig.get(key));
        }
      }
    }

    // TODO: Don't use internal classes from opentelemetry
    OtlpConfigUtil.configureOtlpExporterBuilder(
        DATA_TYPE_METRICS,
        DefaultConfigProperties.create(props),
        builder::setEndpoint,
        builder::addHeader,
        builder::setCompression,
        builder::setTimeout,
        builder::setTrustedCertificates,
        builder::setClientTls,
        builder::setRetryPolicy,
        builder::setMemoryMode);

    builder.setDefaultAggregationSelector((instrumentType) -> Aggregation.lastValue());
    return builder.build();
  }

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
}
