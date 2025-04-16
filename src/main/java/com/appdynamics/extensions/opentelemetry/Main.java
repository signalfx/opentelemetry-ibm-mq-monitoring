package com.appdynamics.extensions.opentelemetry;

import static io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil.DATA_TYPE_METRICS;

import com.appdynamics.extensions.webspheremq.WMQMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Main {

  public static void main(String[] args) {
    String configFile = args[0]; // TODO args checks.

    OtlpGrpcMetricExporterBuilder builder = OtlpGrpcMetricExporter.builder();
    DefaultConfigProperties config = DefaultConfigProperties.create(new HashMap<>());

    OtlpConfigUtil.configureOtlpExporterBuilder(
        DATA_TYPE_METRICS,
        config,
        builder::setEndpoint,
        builder::addHeader,
        builder::setCompression,
        builder::setTimeout,
        builder::setTrustedCertificates,
        builder::setClientTls,
        builder::setRetryPolicy,
        builder::setMemoryMode);

    WMQMonitor monitor = new WMQMonitor(new OpenTelemetryMetricWriteHelper(builder.build()));
    TaskExecutionContext ctxt = new TaskExecutionContext();

    Timer timer = new Timer();
    TimerTask timerTask = new TimerTask() {
      public void run() {
        try {
          monitor.execute(new HashMap<String, String>() {{
            put("config-file", configFile);
          }}, ctxt);
        } catch (TaskExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    };
    timer.schedule(timerTask, 0, 60000); // TODO make this configurable.
  }
}
