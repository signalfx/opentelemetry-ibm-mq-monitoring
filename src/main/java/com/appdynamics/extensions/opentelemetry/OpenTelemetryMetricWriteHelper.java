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

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.metrics.transformers.Transformer;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class OpenTelemetryMetricWriteHelper extends MetricWriteHelper {

  private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryMetricWriteHelper.class);

  private final MetricExporter exporter;

  public OpenTelemetryMetricWriteHelper(MetricExporter otlpGrpcMetricExporter) {
    this.exporter = otlpGrpcMetricExporter;
  }

  @Override
  public void printMetric(String metricPath, BigDecimal value, String metricType) {
    String metricName = metricPath.substring(metricPath.lastIndexOf('|'));
    transformAndPrintMetrics(Collections.singletonList(new Metric(metricName, value.toString(), metricPath)));
  }

    @Override
    public void transformAndPrintMetrics(List<Metric> metrics) {
    Transformer transformer = new Transformer(metrics);
    transformer.transform();
    MQOtelTranslator mqOtelTransformer = new MQOtelTranslator();
    Collection<MetricData> translated = mqOtelTransformer.translate(metrics);
    this.exportMetrics(translated);
  }

  public void exportMetrics(Collection<MetricData> metrics) {
    this.exporter.export(metrics);
  }

  @Override
  public void onComplete() {
    this.exporter.flush(); // TODO await?
  }
}
