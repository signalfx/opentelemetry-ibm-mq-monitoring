package com.appdynamics.extensions.opentelemetry;

import com.appdynamics.extensions.util.YmlUtils;
import com.appdynamics.extensions.webspheremq.WMQMonitor;
import com.appdynamics.extensions.yml.YmlReader;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: Main <config-file>");
      System.exit(1);
    }

    String configFile = args[0];
    Map<String, ?> config = YmlReader.readFromFileAsMap(new File(configFile));

    Config.setUpSSLConnection(config);

    OtlpGrpcMetricExporter exporter = Config.createOtlpGrpcMetricsExporter(config);

    WMQMonitor monitor = new WMQMonitor(new OpenTelemetryMetricWriteHelper(exporter));
    TaskExecutionContext ctxt = new TaskExecutionContext();

    int numberOfThreads = 1;
    int taskDelaySeconds = 60;
    int initialDelaySeconds = 10;
    if (config.get("taskSchedule") instanceof Map) {
      Map taskSchedule = (Map) config.get("taskSchedule");
      numberOfThreads = YmlUtils.getInt(taskSchedule.get("numberOfThreads"), numberOfThreads);
      taskDelaySeconds = YmlUtils.getInt(taskSchedule.get("taskDelaySeconds"), taskDelaySeconds);
      initialDelaySeconds = YmlUtils.getInt(taskSchedule.get("initialDelaySeconds"), initialDelaySeconds);
    }
    final ScheduledExecutorService service = Executors.newScheduledThreadPool(numberOfThreads);
    service.scheduleAtFixedRate(() -> {
      try {
        monitor.execute(new HashMap<String, String>() {{
          put("config-file", configFile);
        }}, ctxt);
      } catch (TaskExecutionException e) {
        throw new RuntimeException(e);
      }
    }, initialDelaySeconds, taskDelaySeconds, TimeUnit.SECONDS);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      service.shutdown();
      exporter.shutdown();
    }));
  }
}
