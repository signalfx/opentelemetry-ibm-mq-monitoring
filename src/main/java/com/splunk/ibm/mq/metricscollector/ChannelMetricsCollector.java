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

import static com.ibm.mq.constants.CMQCFC.MQIACH_CHANNEL_STATUS;

import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for channel metric collection. */
public final class ChannelMetricsCollector implements MetricsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(ChannelMetricsCollector.class);

  public static final String ARTIFACT = "Channels";
  private final MetricsCollectorContext context;
  private final ChannelMetrics channelMetrics;

  /*
   * The Channel Status values are mentioned here http://www.ibm.com/support/knowledgecenter/SSFKSJ_7.5.0/com.ibm.mq.ref.dev.doc/q090880_.htm
   */
  public ChannelMetricsCollector(MetricsCollectorContext context) {
    this(context, ChannelMetrics.create());
  }

  public ChannelMetricsCollector(MetricsCollectorContext context, ChannelMetrics channelMetrics) {
    this.context = context;
    this.channelMetrics = channelMetrics;
  }

  @Override
  public void publishMetrics() {
    logger.info("Collecting metrics for command MQCMD_INQUIRE_CHANNEL_STATUS");
    long entryTime = System.currentTimeMillis();

    if (context.hasNoMetricsToReport()) {
      logger.debug(
          "Channel metrics to report from the config is null or empty, nothing to publish");
      return;
    }

    int[] attrs =
        context.buildIntAttributesArray(CMQCFC.MQCACH_CHANNEL_NAME, CMQCFC.MQCACH_CONNECTION_NAME);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Attributes being sent along PCF agent request to query channel metrics: {}",
          Arrays.toString(attrs));
    }

    Set<String> channelGenericNames = context.getChannelIncludeFilterNames();

    Set<String> activeChannels = queryAllChannels(channelGenericNames, attrs);

    logger.info(
        "Active Channels in queueManager {} are {}", context.getQueueManagerName(), activeChannels);

    channelMetrics.setActiveChannels(activeChannels.size());

    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug("Time taken to publish metrics for all channels is {} milliseconds", exitTime);
  }

  @NotNull
  private Set<String> queryAllChannels(Set<String> channelGenericNames, int[] attrs) {
    // The MQCMD_INQUIRE_CHANNEL_STATUS command queries the current operational status of channels.
    // This includes information about whether a channel is running, stopped, or in another state,
    // as well as details about the channel’s performance and usage.
    Set<String> activeChannels = new HashSet<>();
    for (String channelGenericName : channelGenericNames) {
      PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_CHANNEL_STATUS);
      request.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, channelGenericName);
      request.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_TYPE, CMQC.MQOT_CURRENT_CHANNEL);
      request.addParameter(CMQCFC.MQIACH_CHANNEL_INSTANCE_ATTRS, attrs);
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
        }

        List<PCFMessage> messages =
            MessageFilter.ofKind("channel")
                .excluding(context.getChannelExcludeFilters())
                .withResourceExtractor(MessageBuddy::channelName)
                .filter(response);

        for (PCFMessage message : messages) {
          channelMetrics.setAll(constant -> MessageBuddy.getIntParameterValue(message, constant));
        }

        if (channelIsActive(messages)) {
          activeChannels.add(channelGenericName);
        }

      } catch (Exception e) {
        logger.error(
            "Unexpected error occurred while collecting metrics for channel " + channelGenericName,
            e);
      }
    }
    return activeChannels;
  }

  private boolean channelIsActive(List<PCFMessage> messages) {
    return messages.stream()
        .map(
            msg -> {
              return MessageBuddy.getIntParameterValue(msg, MQIACH_CHANNEL_STATUS);
            })
        .filter(Objects::nonNull)
        .filter(
            // TODO: Reassess this. There are other values, like CMQCFC.MQCHS_INACTIVE, for example
            metricVal ->
                metricVal != CMQCFC.MQCHS_RETRYING
                    && metricVal != CMQCFC.MQCHS_STOPPED
                    && metricVal != CMQCFC.MQCHS_STARTING)
        .map(x -> true)
        .findFirst()
        .orElse(false);
  }
}
