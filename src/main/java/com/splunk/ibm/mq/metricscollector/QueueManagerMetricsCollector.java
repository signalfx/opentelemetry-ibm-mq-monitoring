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
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for queue manager metric collection. */
public final class QueueManagerMetricsCollector implements MetricsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(QueueManagerMetricsCollector.class);
  public static final String ARTIFACT = "Queue Manager";
  private final MetricsCollectorContext context;
  private final MetricCreator metricCreator;

  public QueueManagerMetricsCollector(
      MetricsCollectorContext context, MetricCreator metricCreator) {
    this.context = context;
    this.metricCreator = metricCreator;
  }

  @Override
  public void publishMetrics() {
    long entryTime = System.currentTimeMillis();
    logger.debug(
        "publishMetrics entry time for queuemanager {} is {} milliseconds",
        context.getAgentQueueManagerName(),
        entryTime);
    // CMQCFC.MQCMD_INQUIRE_Q_MGR_STATUS is 161
    PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_MGR_STATUS);
    // CMQCFC.MQIACF_Q_MGR_STATUS_ATTRS is 1229
    request.addParameter(CMQCFC.MQIACF_Q_MGR_STATUS_ATTRS, new int[] {CMQCFC.MQIACF_ALL});
    try {
      // Note that agent.send() method is synchronized
      logger.debug(
          "sending PCF agent request to query queuemanager {}", context.getAgentQueueManagerName());
      long startTime = System.currentTimeMillis();
      List<PCFMessage> responses = context.send(request);
      long endTime = System.currentTimeMillis() - startTime;
      logger.debug(
          "PCF agent queuemanager metrics query response for {} received in {} milliseconds",
          context.getAgentQueueManagerName(),
          endTime);
      if (responses.isEmpty()) {
        logger.debug("Unexpected error while PCFMessage.send(), response is empty");
        return;
      }
      List<Metric> responseMetrics = Lists.newArrayList();
      {
        int status = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_Q_MGR_STATUS);
        Metric metric = metricCreator.createMetric("mq.manager.status", status, Attributes.empty());
        responseMetrics.add(metric);
      }
      {
        int count = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_CONNECTION_COUNT);
        Metric metric =
            metricCreator.createMetric("mq.connection.count", count, Attributes.empty());
        responseMetrics.add(metric);
      }
      {
        int logSize = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_RESTART_LOG_SIZE);
        Metric metric =
            metricCreator.createMetric("mq.restart.log.size", logSize, Attributes.empty());
        responseMetrics.add(metric);
      }
      {
        int logSize = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_REUSABLE_LOG_SIZE);
        Metric metric =
            metricCreator.createMetric("mq.reusable.log.size", logSize, Attributes.empty());
        responseMetrics.add(metric);
      }
      {
        int logSize = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_ARCHIVE_LOG_SIZE);
        Metric metric =
            metricCreator.createMetric("mq.archive.log.size", logSize, Attributes.empty());
        responseMetrics.add(metric);
      }
      {
        int maxActiveChannels = this.context.getQueueManager().getMaxActiveChannels();
        Metric metric =
            metricCreator.createMetric(
                "mq.manager.max.active.channels", maxActiveChannels, Attributes.empty());
        responseMetrics.add(metric);
      }
      context.transformAndPrintMetrics(responseMetrics);
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new RuntimeException(e);
    } finally {
      long exitTime = System.currentTimeMillis() - entryTime;
      logger.debug("Time taken to publish metrics for queuemanager is {} milliseconds", exitTime);
    }
  }
}
