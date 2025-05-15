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

import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.splunk.ibm.mq.WMQMonitor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: Main <config-file>");
      System.exit(1);
    }

    String configFile = args[0];

    ConfigWrapper config = ConfigWrapper.parse(configFile);

    Thread.UncaughtExceptionHandler handler =
        (t, e) -> logger.error("Unhandled exception in thread pool", e);
    final ScheduledExecutorService service =
        Executors.newScheduledThreadPool(
            config.getNumberOfThreads(),
            r -> {
              Thread thread = new Thread(r);
              thread.setUncaughtExceptionHandler(handler);
              return thread;
            });

    Config.setUpSSLConnection(config._exposed());

    MetricExporter exporter = Config.createOtlpHttpMetricsExporter(config._exposed());

    MetricReader reader =
        PeriodicMetricReader.builder(exporter)
            .setExecutor(service)
            .setInterval(config.getTaskDelay())
            .build();

    List<String> queueManagerNames = config.getQueueManagerNames();
    Map<String, SdkMeterProvider> providers = createSdkMeterProviders(reader, queueManagerNames);
    Map<String, Meter> meters = new HashMap<>();
    for (Map.Entry<String, SdkMeterProvider> e : providers.entrySet()) {
      meters.put(e.getKey(), e.getValue().get("opentelemetry.io/mq"));
    }

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  for (SdkMeterProvider c : providers.values()) {
                    c.close();
                  }
                  service.shutdown();
                  exporter.shutdown();
                }));

    service.scheduleAtFixedRate(
        () -> {
          try {
            WMQMonitor monitor =
                new WMQMonitor(
                    config, service, new OpenTelemetryMetricWriteHelper(exporter, meters));
            TaskExecutionContext taskExecCtx = new TaskExecutionContext();
            Map<String, String> taskArguments = new HashMap<>();
            taskArguments.put("config-file", configFile);
            monitor.execute(taskArguments, taskExecCtx);
          } catch (TaskExecutionException e) {
            throw new RuntimeException(e);
          }
        },
        config.getTaskInitialDelaySeconds(),
        config.getTaskDelaySeconds(),
        TimeUnit.SECONDS);
  }

  public static Map<String, SdkMeterProvider> createSdkMeterProviders(
      MetricReader reader, List<String> queueManagerNames) {
    Map<String, SdkMeterProvider> providers = new HashMap<>();
    for (String queueManagerName : queueManagerNames) {
      Attributes attrs = Attributes.of(AttributeKey.stringKey("queue.manager"), queueManagerName);
      SdkMeterProvider meterProvider =
          SdkMeterProvider.builder()
              .registerMetricReader(reader)
              // TODO: We should not be making multiple resources. :(
              .setResource(Resource.create(attrs))
              .build();
      providers.put(queueManagerName, meterProvider);
    }
    return providers;
  }
}
