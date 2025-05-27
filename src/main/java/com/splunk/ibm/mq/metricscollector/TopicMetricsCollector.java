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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TopicMetricsCollector implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(TopicMetricsCollector.class);
  private final JobSubmitterContext context;

  public TopicMetricsCollector(JobSubmitterContext context) {
    this.context = context;
  }

  @Override
  public void run() {
    logger.info("Collecting Topic metrics...");
    List<Runnable> publishers = Lists.newArrayList();

    //  to query the current status of topics, which is essential for monitoring and managing the
    // publish/subscribe environment in IBM MQ.
    MetricCreator metricCreator = context.newMetricCreator();
    MetricsCollectorContext collectorContext = context.newCollectorContext();
    InquireTStatusCmdCollector metricsPublisher =
        new InquireTStatusCmdCollector(collectorContext, metricCreator);
    publishers.add(metricsPublisher);
    CountDownLatch latch = new CountDownLatch(publishers.size());
    for (Runnable publisher : publishers) {
      context.submitPublishJob(publisher, latch);
    }
    try {
      int timeout = context.getConfigInt("topicMetricsCollectionTimeoutInSeconds", 20);
      latch.await(timeout, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.error("The thread was interrupted ", e);
    }
  }
}
