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

import com.splunk.ibm.mq.WMQMonitor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
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

    try {
      Main.class.getClassLoader().loadClass("com.ibm.mq.headers.MQDataException");
    } catch (ClassNotFoundException e) {
      System.err.println("IBM MQ jar is missing from classpath.");
      System.exit(1);
    }

    String configFile = args[0];

    ConfigWrapper config = ConfigWrapper.parse(configFile);

    Thread.UncaughtExceptionHandler handler =
        (t, e) -> logger.error("Unhandled exception in thread pool", e);
    logger.debug("Initializing thread pool with {} threads", config.getNumberOfThreads());
    ScheduledExecutorService service =
        Executors.newScheduledThreadPool(
            config.getNumberOfThreads(),
            r -> {
              Thread thread = new Thread(r);
              thread.setUncaughtExceptionHandler(handler);
              return thread;
            });

    Config.configureSecurity(config);
    Config.setUpSSLConnection(config._exposed());
    MetricExporter exporter = Config.createOtlpHttpMetricsExporter(config._exposed());

    run(config, service, exporter);
  }

  public static void run(
      ConfigWrapper config, final ScheduledExecutorService service, final MetricExporter exporter) {

    MetricReader reader = PeriodicMetricReader.builder(exporter).build();

    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .setResource(Resource.empty())
            .registerMetricReader(reader)
            .build();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  meterProvider.shutdown();
                  reader.shutdown();
                  service.shutdown();
                  exporter.shutdown();
                }));

    service.scheduleAtFixedRate(
        () -> {
          WMQMonitor monitor = new WMQMonitor(config, service, meterProvider.get("websphere/mq"));
          monitor.run();
          reader.forceFlush().whenComplete(exporter::flush);
        },
        config.getTaskInitialDelaySeconds(),
        config.getTaskDelaySeconds(),
        TimeUnit.SECONDS);
  }
}
