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

import com.appdynamics.extensions.util.YmlUtils;
import com.appdynamics.extensions.yml.YmlReader;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.splunk.ibm.mq.WMQMonitor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    int numberOfThreads = 1;
    int taskDelaySeconds = 60;
    int initialDelaySeconds = 10;
    if (config.get("taskSchedule") instanceof Map) {
      Map taskSchedule = (Map) config.get("taskSchedule");
      numberOfThreads = YmlUtils.getInt(taskSchedule.get("numberOfThreads"), numberOfThreads);
      taskDelaySeconds = YmlUtils.getInt(taskSchedule.get("taskDelaySeconds"), taskDelaySeconds);
      initialDelaySeconds =
          YmlUtils.getInt(taskSchedule.get("initialDelaySeconds"), initialDelaySeconds);
    }
    final ScheduledExecutorService service = Executors.newScheduledThreadPool(numberOfThreads);

    Config.setUpSSLConnection(config);

    OtlpGrpcMetricExporter exporter = Config.createOtlpGrpcMetricsExporter(config);

    MetricReader reader =
        PeriodicMetricReader.builder(exporter)
            .setExecutor(service)
            .setInterval(Duration.of(taskDelaySeconds, ChronoUnit.SECONDS))
            .build();

    List<String> queueManagerNames = new ArrayList<>();
    for (Object queueManagerConfig : (List) config.get("queueManagers")) {
      String queueManagerName = (String) ((Map<String, ?>) queueManagerConfig).get("name");
      queueManagerNames.add(queueManagerName);
    }
    Map<String, SdkMeterProvider> providers =
        createSdkMeterProviders(reader, queueManagerNames.toArray(new String[0]));
    Map<String, Meter> meters = new HashMap<>();
    for (Map.Entry<String, SdkMeterProvider> e : providers.entrySet()) {
      meters.put(e.getKey(), e.getValue().get("opentelemetry.io/mq"));
    }

    WMQMonitor monitor = new WMQMonitor(new OpenTelemetryMetricWriteHelper(exporter, meters));
    TaskExecutionContext taskExecCtx = new TaskExecutionContext();

    try {

      service.scheduleAtFixedRate(
          () -> {
            try {
              Map<String, String> taskArguments = new HashMap<>();
              taskArguments.put("config-file", configFile);
              monitor.execute(taskArguments, taskExecCtx);
            } catch (TaskExecutionException e) {
              throw new RuntimeException(e);
            }
          },
          initialDelaySeconds,
          taskDelaySeconds,
          TimeUnit.SECONDS);

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    service.shutdown();
                    exporter.shutdown();
                  }));
    } finally {
      for (SdkMeterProvider c : providers.values()) {
        c.close();
      }
    }
  }

  public static Map<String, SdkMeterProvider> createSdkMeterProviders(
      MetricReader reader, String... queueManagerNames) {
    Map<String, SdkMeterProvider> providers = new HashMap<>();
    for (String q : queueManagerNames) {
      Attributes attrs = Attributes.of(AttributeKey.stringKey("queue.manager"), q);
      SdkMeterProvider meterProvider =
          SdkMeterProvider.builder()
              .registerMetricReader(reader)
              .setResource(Resource.create(attrs))
              .build();
      providers.put(q, meterProvider);
    }
    return providers;
  }
}
