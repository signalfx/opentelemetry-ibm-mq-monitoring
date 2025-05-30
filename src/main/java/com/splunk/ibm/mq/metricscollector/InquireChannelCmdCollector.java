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
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.MQCFIL;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.splunk.ibm.mq.metrics.Metrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for channel inquiry metric collection. */
public final class InquireChannelCmdCollector implements Consumer<MetricsCollectorContext> {

  public static final Logger logger = LoggerFactory.getLogger(InquireChannelCmdCollector.class);
  private final LongGauge maxClientsGauge;
  private final LongGauge instancesPerClientGauge;
  private final LongGauge messageRetryCountGauge;
  private final LongGauge messageReceivedCountGauge;
  private final LongGauge messageSentCountGauge;

  public InquireChannelCmdCollector(Meter meter) {
    this.maxClientsGauge = Metrics.createMqMaxInstances(meter);
    this.instancesPerClientGauge = Metrics.createMqInstancesPerClient(meter);
    this.messageRetryCountGauge = Metrics.createMqMessageRetryCount(meter);
    this.messageReceivedCountGauge = Metrics.createMqMessageReceivedCount(meter);
    this.messageSentCountGauge = Metrics.createMqMessageSentCount(meter);
  }

  @Override
  public void accept(MetricsCollectorContext context) {
    long entryTime = System.currentTimeMillis();

    Set<String> channelGenericNames = context.getChannelIncludeFilterNames();

    for (String channelGenericName : channelGenericNames) {
      PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_CHANNEL);
      request.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, channelGenericName);
      request.addParameter(
          new MQCFIL(MQConstants.MQIACF_CHANNEL_ATTRS, new int[] {MQConstants.MQIACF_ALL}));
      try {
        logger.debug(
            "sending PCF agent request to query metrics for generic channel {}",
            channelGenericName);
        long startTime = System.currentTimeMillis();
        List<PCFMessage> response = context.send(request);
        long endTime = System.currentTimeMillis() - startTime;
        logger.debug(
            "PCF agent queue metrics query response for generic queue {} received in {} milliseconds",
            channelGenericName,
            endTime);
        if (response.isEmpty()) {
          logger.warn("Unexpected error while PCFMessage.send(), response is empty");
          return;
        }

        List<PCFMessage> messages =
            MessageFilter.ofKind("channel")
                .excluding(context.getChannelExcludeFilters())
                .withResourceExtractor(MessageBuddy::channelName)
                .filter(response);

        for (PCFMessage message : messages) {
          String channelName = MessageBuddy.channelName(message);
          String channelType = MessageBuddy.channelType(message);
          logger.debug("Pulling out metrics for channel name {}", channelName);
          updateMetrics(message, channelName, channelType, context);
        }
      } catch (PCFException pcfe) {
        if (pcfe.getReason() == MQConstants.MQRCCF_CHL_STATUS_NOT_FOUND) {
          String errorMsg = "Channel- " + channelGenericName + " :";
          errorMsg +=
              "Could not collect channel information as channel is stopped or inactive: Reason '3065'\n";
          errorMsg +=
              "If the channel type is MQCHT_RECEIVER, MQCHT_SVRCONN or MQCHT_CLUSRCVR, then the only action is to enable the channel, not start it.";
          logger.error(errorMsg, pcfe);
        } else if (pcfe.getReason() == MQConstants.MQRC_SELECTOR_ERROR) {
          logger.error(
              "Invalid metrics passed while collecting channel metrics, check config.yaml: Reason '2067'",
              pcfe);
        }
        logger.error(pcfe.getMessage(), pcfe);
      } catch (Exception e) {
        logger.error(
            "Unexpected error while collecting metrics for channel " + channelGenericName, e);
      }
    }

    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug("Time taken to publish metrics for all channels is {} milliseconds", exitTime);
  }

  private void updateMetrics(
      PCFMessage message, String channelName, String channelType, MetricsCollectorContext context)
      throws PCFException {
    Attributes attributes =
        Attributes.builder()
            .put("channel.name", channelName)
            .put("channel.type", channelType)
            .put("queue.manager", context.getQueueManagerName())
            .build();
    if (context.getMetricsConfig().isMqMaxInstancesEnabled()
        && message.getParameter(CMQCFC.MQIACH_MAX_INSTANCES) != null) {
      this.maxClientsGauge.set(
          message.getIntParameterValue(CMQCFC.MQIACH_MAX_INSTANCES), attributes);
    }
    if (context.getMetricsConfig().isMqInstancesPerClientEnabled()
        && message.getParameter(CMQCFC.MQIACH_MAX_INSTS_PER_CLIENT) != null) {
      this.instancesPerClientGauge.set(
          message.getIntParameterValue(CMQCFC.MQIACH_MAX_INSTS_PER_CLIENT), attributes);
    }
    if (context.getMetricsConfig().isMqMessageRetryCountEnabled()) {
      int count = 0;
      if (message.getParameter(CMQCFC.MQIACH_MR_COUNT) != null) {
        count = message.getIntParameterValue(CMQCFC.MQIACH_MR_COUNT);
      }
      this.messageRetryCountGauge.set(count, attributes);
    }
    if (context.getMetricsConfig().isMqInstancesPerClientEnabled()) {
      int received = 0;
      if (message.getParameter(CMQCFC.MQIACH_MSGS_RECEIVED) != null) {
        received = message.getIntParameterValue(CMQCFC.MQIACH_MSGS_RECEIVED);
      }
      this.messageReceivedCountGauge.set(received, attributes);
    }
    if (context.getMetricsConfig().isMqMessageSentCountEnabled()) {
      int sent = 0;
      if (message.getParameter(CMQCFC.MQIACH_MSGS_SENT) != null) {
        sent = message.getIntParameterValue(CMQCFC.MQIACH_MSGS_SENT);
      }
      this.messageSentCountGauge.set(sent, attributes);
    }
  }
}
