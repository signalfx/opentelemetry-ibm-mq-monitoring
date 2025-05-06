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
package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * JobSubmitterContext is a bundle class used by MetricsPublisher instances in order to bundle
 * together collaborators and to encapsulate some common repeated functionality.
 */
public final class JobSubmitterContext {

  private final String metricsPrefix;
  private final CountDownLatch countDownLatch;
  private final MetricsCollectorContext collectorContext;
  private final ScheduledExecutorService service;
  private final Map<String, ?> configMap;

  public JobSubmitterContext(
      String metricsPrefix,
      CountDownLatch countDownLatch,
      MetricsCollectorContext collectorContext,
      ScheduledExecutorService service,
      Map<String, ?> configMap) {
    this.metricsPrefix = metricsPrefix;
    this.service = service;
    this.configMap = configMap;
    this.countDownLatch = countDownLatch;
    this.collectorContext = collectorContext;
  }

  String getMetricPrefix() {
    return this.metricsPrefix;
  }

  Future<?> submitPublishJob(String name, MetricsPublisher publisher) {
    MetricsPublisherJob job = new MetricsPublisherJob(publisher, countDownLatch);
    return this.service.submit(job);
  }

  int getConfigInt(String key, int defaultValue) {
    Object result = this.configMap.get(key);
    if (result == null) {
      return defaultValue;
    }
    return (Integer) result;
  }

  MetricCreator newMetricCreator(String firstPathComponent) {
    return new MetricCreator(
        getMetricPrefix(), collectorContext.getQueueManagerName(), firstPathComponent);
  }

  MetricsCollectorContext newCollectorContext(Map<String, WMQMetricOverride> newMetrics) {
    IntAttributesBuilder attributesBuilder = new IntAttributesBuilder(newMetrics);
    return new MetricsCollectorContext(
        newMetrics,
        attributesBuilder,
        collectorContext.getQueueManager(),
        collectorContext.getAgent(),
        collectorContext.getMetricWriteHelper());
  }

  Map<String, WMQMetricOverride> getMetricsForCommand(String command) {
    return collectorContext.getMetricsForCommand(command);
  }
}
