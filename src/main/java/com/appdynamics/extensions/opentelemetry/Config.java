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

package com.appdynamics.extensions.opentelemetry;

import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;

import java.util.HashMap;
import java.util.Map;

import static io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil.DATA_TYPE_METRICS;

/**
 * Utilities reading configuration and create domain objects
 */
class Config {

  static OtlpGrpcMetricExporter createOtlpGrpcMetricsExporter(Map<String, ?> config) {
    OtlpGrpcMetricExporterBuilder builder = OtlpGrpcMetricExporter.builder();

    Map<String, String> props = new HashMap<>();
    if (config.get("otlpExporter") instanceof Map) {
      Map otlpConfig = (Map) config.get("otlpExporter");
      for (Object key : otlpConfig.keySet()) {
        if (key instanceof String && otlpConfig.get(key) instanceof String) {
          props.put((String) key, (String) otlpConfig.get(key));
        }
      }
    }

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

    return builder.build();
  }

  static void setUpSSLConnection(Map<String, ?> config) {
    if (config.get("sslConnection") instanceof Map) {
      Map otlpConfig = (Map) config.get("sslConnection");

      {
        Object keyStorePath = otlpConfig.get("keyStorePath");
        if (keyStorePath instanceof String && !((String) keyStorePath).trim().isEmpty()) {
          System.setProperty("javax.net.ssl.keyStore", (String) keyStorePath);
        }
      }

      {
        Object keyStorePassword = otlpConfig.get("keyStorePassword");
        if (keyStorePassword instanceof String && !((String) keyStorePassword).trim().isEmpty()) {
          System.setProperty("javax.net.ssl.keyStorePassword", (String) keyStorePassword);
        }
      }

      {
        Object trustStorePath = otlpConfig.get("trustStorePath");
        if (trustStorePath instanceof String && !((String) trustStorePath).trim().isEmpty()) {
          System.setProperty("javax.net.ssl.trustStorePath", (String) trustStorePath);
        }
      }

      {
        Object trustStorePassword = otlpConfig.get("trustStorePassword");
        if (trustStorePassword instanceof String && !((String) trustStorePassword).trim().isEmpty()) {
          System.setProperty("javax.net.ssl.trustStorePassword", (String) trustStorePassword);
        }
      }
    }
  }
}
