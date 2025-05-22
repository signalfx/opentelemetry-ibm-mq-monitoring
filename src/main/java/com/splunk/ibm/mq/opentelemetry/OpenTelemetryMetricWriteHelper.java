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

import com.splunk.ibm.mq.metricscollector.Metric;
import com.splunk.ibm.mq.metricscollector.Transformer;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryMetricWriteHelper {

  private static final Logger logger =
      LoggerFactory.getLogger(OpenTelemetryMetricWriteHelper.class);

  private final MetricExporter exporter;
  private final MetricReader reader;
  private final Meter meter;

  public OpenTelemetryMetricWriteHelper(
      MetricReader reader, MetricExporter otlpGrpcMetricExporter, Meter meter) {
    this.exporter = otlpGrpcMetricExporter;
    this.meter = meter;
    this.reader = reader;
  }

  public void printMetric(String metricPath, BigDecimal value, String metricType) {
    String metricName = metricPath.substring(metricPath.lastIndexOf('|'));
    transformAndPrintMetrics(
        Collections.singletonList(new Metric(metricName, value.toString(), metricPath)));
  }

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

  public Meter getMeter() {
    return meter;
  }

  public void flush() {
    this.reader.forceFlush().whenComplete(this.exporter::flush);
  }
}
