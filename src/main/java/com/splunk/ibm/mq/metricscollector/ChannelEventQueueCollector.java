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

import com.ibm.mq.*;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.Writer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Captures metrics from events logged to the queue manager performance event queue.
public final class ChannelEventQueueCollector implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(ChannelEventQueueCollector.class);
  private final QueueManager queueManager;
  private final MQQueueManager mqQueueManager;
  private final LongCounter channelActivatedCounter;
  private final LongCounter channelConvErrorCounter;
  private final LongCounter channelNotActivatedCounter;
  private final LongCounter channelStoppedCounter;
  private final LongCounter channelStoppedByUserCounter;
  private final LongCounter channelBlockedCounter;

  public ChannelEventQueueCollector(
      MQQueueManager mqQueueManager, QueueManager queueManager, Writer writer) {
    this.mqQueueManager = mqQueueManager;
    this.queueManager = queueManager;
    this.channelActivatedCounter =
        writer.getMeter().counterBuilder("mq.channel.activated.event").setUnit("1").build();
    this.channelConvErrorCounter =
        writer.getMeter().counterBuilder("mq.channel.conv.error.event").setUnit("1").build();
    this.channelNotActivatedCounter =
        writer.getMeter().counterBuilder("mq.channel.not.activated.event").setUnit("1").build();
    this.channelStoppedCounter =
        writer.getMeter().counterBuilder("mq.channel.stopped.event").setUnit("1").build();
    this.channelStoppedByUserCounter =
        writer.getMeter().counterBuilder("mq.channel.stopped.by.user.event").setUnit("1").build();
    this.channelBlockedCounter =
        writer.getMeter().counterBuilder("mq.channel.blocked.event").setUnit("1").build();
  }

  private void readEvents(String channelEventsQueueName) throws Exception {

    MQQueue queue = null;
    int counter = 0;
    try {
      int queueAccessOptions = MQConstants.MQOO_FAIL_IF_QUIESCING | MQConstants.MQOO_INPUT_SHARED;
      queue = mqQueueManager.accessQueue(channelEventsQueueName, queueAccessOptions);
      // keep going until receiving the exception MQConstants.MQRC_NO_MSG_AVAILABLE
      logger.debug("Start reading events from channel queue {}", channelEventsQueueName);
      while (true) {
        try {
          MQGetMessageOptions getOptions = new MQGetMessageOptions();
          getOptions.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_FAIL_IF_QUIESCING;
          MQMessage message = new MQMessage();

          queue.get(message, getOptions);
          PCFMessage receivedMsg = new PCFMessage(message);
          incrementCounterByEventType(receivedMsg);
          counter++;
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
    logger.debug("Read {} events from channel queue {}", counter, channelEventsQueueName);
  }

  private void incrementCounterByEventType(PCFMessage receivedMsg) throws PCFException {
    String channelName = receivedMsg.getStringParameterValue(CMQCFC.MQCACH_CHANNEL_NAME).trim();
    Attributes attributes =
        Attributes.of(
            AttributeKey.stringKey("queue.manager"),
            queueManager.getName(),
            AttributeKey.stringKey("channel.name"),
            channelName);
    switch (receivedMsg.getReason()) {
      case CMQC.MQRC_CHANNEL_ACTIVATED:
        channelActivatedCounter.add(1, attributes);
        break;
      case CMQC.MQRC_CHANNEL_CONV_ERROR:
        channelConvErrorCounter.add(1, attributes);
        break;
      case CMQC.MQRC_CHANNEL_NOT_ACTIVATED:
        channelNotActivatedCounter.add(1, attributes);
        break;
      case CMQC.MQRC_CHANNEL_STOPPED:
        channelStoppedCounter.add(1, attributes);
        break;
      case CMQC.MQRC_CHANNEL_STOPPED_BY_USER:
        channelStoppedByUserCounter.add(1, attributes);
        break;
      case CMQC.MQRC_CHANNEL_BLOCKED:
        switch (receivedMsg.getIntParameterValue(CMQCFC.MQIACF_REASON_QUALIFIER)) {
          case CMQCFC.MQRQ_CHANNEL_BLOCKED_NOACCESS:
            attributes =
                Attributes.builder().putAll(attributes).put("blocked.reason", "noaccess").build();
            break;
          case CMQCFC.MQRQ_CHANNEL_BLOCKED_ADDRESS:
            attributes =
                Attributes.builder().putAll(attributes).put("blocked.reason", "address").build();
            break;
          case CMQCFC.MQRQ_CHANNEL_BLOCKED_USERID:
            attributes =
                Attributes.builder().putAll(attributes).put("blocked.reason", "userid").build();
            break;
        }
        channelBlockedCounter.add(1, attributes);
        break;
      default:
        logger.debug("Unknown event reason {}", receivedMsg.getReason());
    }
  }

  @Override
  public void run() {
    long entryTime = System.currentTimeMillis();
    String channelEventsQueueName = this.queueManager.getChannelEventsQueueName();
    logger.info(
        "sending PCF agent request to read channel events from queue {}", channelEventsQueueName);
    try {
      readEvents(channelEventsQueueName);
    } catch (Exception e) {
      logger.error(
          "Unexpected error occurred while collecting channel events for queue "
              + channelEventsQueueName,
          e);
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug("Time taken to publish metrics for channel events is {} milliseconds", exitTime);
  }
}
