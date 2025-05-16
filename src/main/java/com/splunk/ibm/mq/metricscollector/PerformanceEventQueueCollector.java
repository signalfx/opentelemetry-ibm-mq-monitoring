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

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Captures metrics from events logged to the queue manager performance event queue.
public final class PerformanceEventQueueCollector implements MetricsPublisher {

  private static final Logger logger =
      LoggerFactory.getLogger(PerformanceEventQueueCollector.class);
  private final QueueManager queueManager;
  private final MQQueueManager mqQueueManager;
  private final LongCounter fullQueueDepthCounter;
  private final LongCounter highQueueDepthCounter;
  private final LongCounter lowQueueDepthCounter;

  public PerformanceEventQueueCollector(
      MQQueueManager mqQueueManager,
      QueueManager queueManager,
      OpenTelemetryMetricWriteHelper openTelemetryMetricWriteHelper) {
    this.mqQueueManager = mqQueueManager;
    this.queueManager = queueManager;
    this.fullQueueDepthCounter =
        openTelemetryMetricWriteHelper
            .getMeter(queueManager.getName())
            .counterBuilder("mq.queue.depth.full.event")
            .setUnit("1")
            .build();
    this.highQueueDepthCounter =
        openTelemetryMetricWriteHelper
            .getMeter(queueManager.getName())
            .counterBuilder("mq.queue.depth.high.event")
            .setUnit("1")
            .build();
    this.lowQueueDepthCounter =
        openTelemetryMetricWriteHelper
            .getMeter(queueManager.getName())
            .counterBuilder("mq.queue.depth.low.event")
            .setUnit("1")
            .build();
  }

  private void readEvents(String performanceEventsQueueName) throws Exception {

    MQQueue queue = null;
    try {
      int queueAccessOptions = MQConstants.MQOO_FAIL_IF_QUIESCING | MQConstants.MQOO_INPUT_SHARED;
      queue = mqQueueManager.accessQueue(performanceEventsQueueName, queueAccessOptions);
      // keep going until receiving the exception MQConstants.MQRC_NO_MSG_AVAILABLE
      while (true) {
        try {
          MQGetMessageOptions getOptions = new MQGetMessageOptions();
          getOptions.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_FAIL_IF_QUIESCING;
          MQMessage message = new MQMessage();

          queue.get(message, getOptions);
          PCFMessage receivedMsg = new PCFMessage(message);
          incrementCounterByEventType(receivedMsg);

        } catch (MQException e) {
          if (e.reasonCode != MQConstants.MQRC_NO_MSG_AVAILABLE) {
            logger.error(e.getMessage(), e);
          }
          break;
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
          break;
        }
      }
    } finally {
      if (queue != null) {
        queue.close();
      }
    }
  }

  private void incrementCounterByEventType(PCFMessage receivedMsg) throws PCFException {
    switch (receivedMsg.getReason()) {
      case CMQC.MQRC_Q_FULL:
        {
          String queueName = receivedMsg.getStringParameterValue(CMQC.MQCA_BASE_OBJECT_NAME);
          fullQueueDepthCounter.add(
              1, Attributes.of(AttributeKey.stringKey("queue.name"), queueName));
        }
        break;
      case CMQC.MQRC_Q_DEPTH_HIGH:
        {
          String queueName = receivedMsg.getStringParameterValue(CMQC.MQCA_BASE_OBJECT_NAME);
          highQueueDepthCounter.add(
              1, Attributes.of(AttributeKey.stringKey("queue.name"), queueName));
        }
        break;
      case CMQC.MQRC_Q_DEPTH_LOW:
        {
          String queueName = receivedMsg.getStringParameterValue(CMQC.MQCA_BASE_OBJECT_NAME);
          lowQueueDepthCounter.add(
              1, Attributes.of(AttributeKey.stringKey("queue.name"), queueName));
        }
        break;
      default:
        logger.debug("Unknown event reason {}", receivedMsg.getReason());
    }
  }

  @Override
  public void publishMetrics() {
    long entryTime = System.currentTimeMillis();
    String performanceEventsQueueName = this.queueManager.getPerformanceEventsQueueName();
    logger.info(
        "sending PCF agent request to read performance events from queue {}",
        performanceEventsQueueName);
    try {
      readEvents(performanceEventsQueueName);
    } catch (Exception e) {
      logger.error(
          "Unexpected error occurred while collecting performance events for queue "
              + performanceEventsQueueName,
          e);
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug(
        "Time taken to publish metrics for performance events is {} milliseconds", exitTime);
  }
}
