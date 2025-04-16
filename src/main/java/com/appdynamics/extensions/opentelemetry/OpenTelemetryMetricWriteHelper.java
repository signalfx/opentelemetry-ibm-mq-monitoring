package com.appdynamics.extensions.opentelemetry;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.metrics.transformers.Transformer;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.data.MetricData;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class OpenTelemetryMetricWriteHelper extends MetricWriteHelper {

  private final OtlpGrpcMetricExporter exporter;

  public OpenTelemetryMetricWriteHelper(OtlpGrpcMetricExporter otlpGrpcMetricExporter) {
    this.exporter = otlpGrpcMetricExporter;
  }

  public void printMetric(String metricPath, BigDecimal value, String metricType) {
    String metricName = metricPath.substring(metricPath.lastIndexOf('|'));
    transformAndPrintMetrics(Collections.singletonList(new Metric(metricName, value.toString(), metricPath)));
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

  @Override
  public void onComplete() {
    this.exporter.flush(); // TODO await?
  }
}
