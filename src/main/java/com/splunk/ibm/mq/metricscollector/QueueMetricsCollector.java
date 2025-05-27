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

import com.google.common.collect.Lists;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QueueMetricsCollector implements MetricsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(QueueMetricsCollector.class);

  // hack to share state of queue type between collectors.
  // The queue information is only available as response of some commands.
  private final QueueCollectorSharedState sharedState;
  private final MetricCreator metricCreator;
  private final JobSubmitterContext context;

  public QueueMetricsCollector(QueueCollectorSharedState sharedState, JobSubmitterContext context) {
    this.sharedState = sharedState;
    this.context = context;
    this.metricCreator = context.newMetricCreator();
  }

  @Override
  public void publishMetrics() {
    logger.info("Collecting queue metrics...");

    List<MetricsPublisher> publishers = Lists.newArrayList();
    // first collect all queue types.
    {
      Map<String, WMQMetricOverride> metricsForInquireQCmd =
          context.getMetricsForCommand(InquireQCmdCollector.COMMAND);
      MetricsCollectorContext collectorContext = context.newCollectorContext(metricsForInquireQCmd);
      QueueCollectionBuddy queueBuddy =
          new QueueCollectionBuddy(
              collectorContext, sharedState, metricCreator, InquireQCmdCollector.COMMAND);
      MetricsPublisher publisher = new InquireQCmdCollector(collectorContext, queueBuddy);
      publisher.publishMetrics();
    }

    // schedule all other jobs in parallel.
    Map<String, WMQMetricOverride> metricsForInquireQStatusCmd =
        context.getMetricsForCommand(InquireQStatusCmdCollector.COMMAND);
    if (!metricsForInquireQStatusCmd.isEmpty()) {
      MetricsCollectorContext collectorContext =
          context.newCollectorContext(metricsForInquireQStatusCmd);
      QueueCollectionBuddy queueBuddy =
          new QueueCollectionBuddy(
              collectorContext, sharedState, metricCreator, InquireQStatusCmdCollector.COMMAND);
      MetricsPublisher publisher = new InquireQStatusCmdCollector(collectorContext, queueBuddy);
      publishers.add(publisher);
    }
    Map<String, WMQMetricOverride> metricsForResetQStatsCmd =
        context.getMetricsForCommand(ResetQStatsCmdCollector.COMMAND);
    if (!metricsForResetQStatsCmd.isEmpty()) {
      MetricsCollectorContext collectorContext =
          context.newCollectorContext(metricsForResetQStatsCmd);
      QueueCollectionBuddy queueBuddy =
          new QueueCollectionBuddy(
              collectorContext, sharedState, metricCreator, ResetQStatsCmdCollector.COMMAND);
      MetricsPublisher collector = new ResetQStatsCmdCollector(collectorContext, queueBuddy);
      publishers.add(collector);
    }

    CountDownLatch latch = new CountDownLatch(publishers.size());
    for (MetricsPublisher publisher : publishers) {
      context.submitPublishJob(publisher, latch);
    }

    try {
      int timeout = context.getConfigInt("queueMetricsCollectionTimeoutInSeconds", 20);
      latch.await(timeout, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.error("The thread was interrupted ", e);
    }
  }
}
