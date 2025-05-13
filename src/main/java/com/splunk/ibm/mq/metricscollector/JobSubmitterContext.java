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
package com.splunk.ibm.mq.metricscollector;

import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * JobSubmitterContext is a bundle class used by MetricsPublisher instances in order to bundle
 * together collaborators and to encapsulate some common repeated functionality.
 */
public final class JobSubmitterContext {

  private final MonitorContextConfiguration monitorContextConfig;
  private final MetricsCollectorContext collectorContext;

  public JobSubmitterContext(
      MonitorContextConfiguration monitorContextConfig, MetricsCollectorContext collectorContext) {
    this.monitorContextConfig = monitorContextConfig;
    this.collectorContext = collectorContext;
  }

  String getMetricPrefix() {
    return monitorContextConfig.getMetricPrefix();
  }

  void submitPublishJob(MetricsPublisher publisher, CountDownLatch latch) {
    MetricsPublisherJob job = new MetricsPublisherJob(publisher, latch);
    monitorContextConfig.getContext().getExecutorService().execute(publisher.getName(), job);
  }

  int getConfigInt(String key, int defaultValue) {
    Object result = monitorContextConfig.getConfigYml().get(key);
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
