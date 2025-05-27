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
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Reads queue manager events and counts them as metrics
public final class QueueManagerEventCollector implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(QueueManagerEventCollector.class);
  private final QueueManager queueManager;
  private final MQQueueManager mqQueueManager;
  private final LongCounter authorityEventCounter;

  public QueueManagerEventCollector(
      MQQueueManager mqQueueManager,
      QueueManager queueManager,
      OpenTelemetryMetricWriteHelper metricWriteHelper) {
    this.mqQueueManager = mqQueueManager;
    this.queueManager = queueManager;
    this.authorityEventCounter =
        metricWriteHelper.getMeter().counterBuilder("mq.unauthorized.event").setUnit("1").build();
  }

  private void readEvents(String queueManagerEventsQueueName) throws Exception {

    MQQueue queue = null;
    try {
      int queueAccessOptions = MQConstants.MQOO_FAIL_IF_QUIESCING | MQConstants.MQOO_INPUT_SHARED;
      queue = mqQueueManager.accessQueue(queueManagerEventsQueueName, queueAccessOptions);
      // keep going until receiving the exception MQConstants.MQRC_NO_MSG_AVAILABLE
      while (true) {
        try {
          MQGetMessageOptions getOptions = new MQGetMessageOptions();
          getOptions.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_FAIL_IF_QUIESCING;
          MQMessage message = new MQMessage();

          queue.get(message, getOptions);
          PCFMessage received = new PCFMessage(message);
          if (received.getReason() == CMQC.MQRC_NOT_AUTHORIZED) {
            String username = received.getStringParameterValue(CMQCFC.MQCACF_USER_IDENTIFIER);
            String applicationName = received.getStringParameterValue(CMQCFC.MQCACF_APPL_NAME);

            authorityEventCounter.add(
                1,
                Attributes.of(
                    AttributeKey.stringKey("queue.manager"),
                    queueManager.getName(),
                    AttributeKey.stringKey("user.name"),
                    username,
                    AttributeKey.stringKey("application.name"),
                    applicationName));
          } else {
            logger.debug("Unknown event reason {}", received.getReason());
          }

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

  @Override
  public void run() {
    long entryTime = System.currentTimeMillis();
    String queueManagerEventsQueueName = this.queueManager.getQueueManagerEventsQueueName();
    logger.info(
        "sending PCF agent request to read queue manager events from queue {}",
        queueManagerEventsQueueName);
    try {
      readEvents(queueManagerEventsQueueName);
    } catch (Exception e) {
      logger.error(
          "Unexpected error occurred while collecting queue manager events for queue "
              + queueManagerEventsQueueName,
          e);
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug(
        "Time taken to publish metrics for queue manager events is {} milliseconds", exitTime);
  }
}
