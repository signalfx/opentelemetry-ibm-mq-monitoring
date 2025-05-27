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

import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for queue manager metric collection. */
public final class QueueManagerMetricsCollector implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(QueueManagerMetricsCollector.class);
  private final MetricsCollectorContext context;
  private final LongGauge statusGauge;
  private final LongGauge connectionCountGauge;
  private final LongGauge restartLogSizeGauge;
  private final LongGauge reuseLogSizeGauge;
  private final LongGauge archiveLogSizeGauge;
  private final LongGauge maxActiveChannelsGauge;

  public QueueManagerMetricsCollector(MetricsCollectorContext context) {
    this.context = context;
    this.statusGauge =
        context
            .getMetricWriteHelper()
            .getMeter()
            .gaugeBuilder("mq.manager.status")
            .ofLongs()
            .build();
    this.connectionCountGauge =
        context
            .getMetricWriteHelper()
            .getMeter()
            .gaugeBuilder("mq.connection.count")
            .ofLongs()
            .build();
    this.restartLogSizeGauge =
        context
            .getMetricWriteHelper()
            .getMeter()
            .gaugeBuilder("mq.restart.log.size")
            .ofLongs()
            .build();
    this.reuseLogSizeGauge =
        context
            .getMetricWriteHelper()
            .getMeter()
            .gaugeBuilder("mq.reusable.log.size")
            .ofLongs()
            .build();
    this.archiveLogSizeGauge =
        context
            .getMetricWriteHelper()
            .getMeter()
            .gaugeBuilder("mq.archive.log.size")
            .ofLongs()
            .build();
    this.maxActiveChannelsGauge =
        context
            .getMetricWriteHelper()
            .getMeter()
            .gaugeBuilder("mq.manager.max.active.channels")
            .ofLongs()
            .build();
  }

  @Override
  public void run() {
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
      {
        int status = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_Q_MGR_STATUS);
        statusGauge.set(
            status,
            Attributes.of(AttributeKey.stringKey("queue.manager"), context.getQueueManagerName()));
      }
      {
        int count = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_CONNECTION_COUNT);
        connectionCountGauge.set(
            count,
            Attributes.of(AttributeKey.stringKey("queue.manager"), context.getQueueManagerName()));
      }
      {
        int logSize = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_RESTART_LOG_SIZE);
        restartLogSizeGauge.set(
            logSize,
            Attributes.of(AttributeKey.stringKey("queue.manager"), context.getQueueManagerName()));
      }
      {
        int logSize = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_REUSABLE_LOG_SIZE);
        reuseLogSizeGauge.set(
            logSize,
            Attributes.of(AttributeKey.stringKey("queue.manager"), context.getQueueManagerName()));
      }
      {
        int logSize = responses.get(0).getIntParameterValue(CMQCFC.MQIACF_ARCHIVE_LOG_SIZE);
        archiveLogSizeGauge.set(
            logSize,
            Attributes.of(AttributeKey.stringKey("queue.manager"), context.getQueueManagerName()));
      }
      {
        int maxActiveChannels = this.context.getQueueManager().getMaxActiveChannels();
        maxActiveChannelsGauge.set(
            maxActiveChannels,
            Attributes.of(AttributeKey.stringKey("queue.manager"), context.getQueueManagerName()));
      }
    } catch (Exception e) {
      logger.error(e.getMessage());
      throw new RuntimeException(e);
    } finally {
      long exitTime = System.currentTimeMillis() - entryTime;
      logger.debug("Time taken to publish metrics for queuemanager is {} milliseconds", exitTime);
    }
  }
}
