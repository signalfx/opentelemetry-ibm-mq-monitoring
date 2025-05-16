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

import com.splunk.ibm.mq.TaskJob;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

/**
 * JobSubmitterContext is a bundle class used by MetricsPublisher instances in order to bundle
 * together collaborators and to encapsulate some common repeated functionality.
 */
public final class JobSubmitterContext {

  private final MetricsCollectorContext collectorContext;
  private final ConfigWrapper config;
  private final ExecutorService threadPool;

  public JobSubmitterContext(
      MetricsCollectorContext collectorContext, ExecutorService threadPool, ConfigWrapper config) {
    this.collectorContext = collectorContext;
    this.config = config;
    this.threadPool = threadPool;
  }

  void submitPublishJob(MetricsPublisher publisher, CountDownLatch latch) {
    MetricsPublisherJob job = new MetricsPublisherJob(publisher, latch);
    Runnable wrappedJob = new TaskJob(publisher.getName(), job);
    threadPool.submit(wrappedJob);
  }

  MetricCreator newMetricCreator(String firstPathComponent) {
    return new MetricCreator(
        config.getMetricPrefix(), collectorContext.getQueueManagerName(), firstPathComponent);
  }

  public int getConfigInt(String key, int defaultValue) {
    return config.getInt(key, defaultValue);
  }

  MetricsCollectorContext newCollectorContext(Map<String, WMQMetricOverride> newMetrics) {
    return new MetricsCollectorContext(
        newMetrics,
        collectorContext.getQueueManager(),
        collectorContext.getAgent(),
        collectorContext.getMetricWriteHelper());
  }

  Map<String, WMQMetricOverride> getMetricsForCommand(String command) {
    return collectorContext.getMetricsForCommand(command);
  }
}
