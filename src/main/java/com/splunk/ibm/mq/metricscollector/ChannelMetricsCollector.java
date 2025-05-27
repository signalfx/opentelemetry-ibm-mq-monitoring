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

import static com.ibm.mq.constants.CMQC.MQRC_SELECTOR_ERROR;
import static com.ibm.mq.constants.CMQCFC.MQRCCF_CHL_STATUS_NOT_FOUND;

import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for channel metric collection. */
public final class ChannelMetricsCollector implements Runnable {

  private static final Logger logger = LoggerFactory.getLogger(ChannelMetricsCollector.class);

  private final MetricCreator metricCreator;
  private final MetricsCollectorContext context;

  /*
   * The Channel Status values are mentioned here http://www.ibm.com/support/knowledgecenter/SSFKSJ_7.5.0/com.ibm.mq.ref.dev.doc/q090880_.htm
   */
  public ChannelMetricsCollector(MetricsCollectorContext context, MetricCreator metricCreator) {
    this.context = context;
    this.metricCreator = metricCreator;
  }

  @Override
  public void run() {
    logger.info("Collecting metrics for command MQCMD_INQUIRE_CHANNEL_STATUS");
    long entryTime = System.currentTimeMillis();

    int[] attrs =
        new int[] {
          CMQCFC.MQCACH_CHANNEL_NAME,
          CMQCFC.MQCACH_CONNECTION_NAME,
          CMQCFC.MQIACH_MSGS,
          CMQCFC.MQIACH_CHANNEL_STATUS,
          CMQCFC.MQIACH_BYTES_SENT,
          CMQCFC.MQIACH_BYTES_RECEIVED,
          CMQCFC.MQIACH_BUFFERS_SENT,
          CMQCFC.MQIACH_BUFFERS_RECEIVED,
          CMQCFC.MQIACH_CURRENT_SHARING_CONVS,
          CMQCFC.MQIACH_MAX_SHARING_CONVS
        };
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Attributes being sent along PCF agent request to query channel metrics: {}",
          Arrays.toString(attrs));
    }

    Set<String> channelGenericNames = context.getChannelIncludeFilterNames();

    //
    // The MQCMD_INQUIRE_CHANNEL_STATUS command queries the current operational status of channels.
    // This includes information about whether a channel is running, stopped, or in another state,
    // as well as details about the channel’s performance and usage.
    List<String> activeChannels = Lists.newArrayList();
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
          logger.debug("Unexpected error while PCFMessage.send(), response is empty");
          return;
        }

        List<PCFMessage> messages =
            MessageFilter.ofKind("channel")
                .excluding(context.getChannelExcludeFilters())
                .withResourceExtractor(MessageBuddy::channelName)
                .filter(response);

        for (PCFMessage message : messages) {
          String channelName = MessageBuddy.channelName(message);

          logger.debug("Pulling out metrics for channel name {}", channelName);
          List<Metric> responseMetrics = getMetrics(message, channelName, activeChannels);
          context.transformAndPrintMetrics(responseMetrics);
        }
      } catch (PCFException pcfe) {
        if (pcfe.getReason() == MQRCCF_CHL_STATUS_NOT_FOUND) {
          String errorMsg = "Channel- " + channelGenericName + " :";
          errorMsg +=
              "Could not collect channel information as channel is stopped or inactive: Reason '3065'\n";
          errorMsg +=
              "If the channel type is MQCHT_RECEIVER, MQCHT_SVRCONN or MQCHT_CLUSRCVR, then the only action is to enable the channel, not start it.";
          logger.error(errorMsg, pcfe);
        } else if (pcfe.getReason() == MQRC_SELECTOR_ERROR) {
          logger.error(
              "Invalid metrics passed while collecting channel metrics, check config.yaml: Reason '2067'",
              pcfe);
        }
      } catch (Exception e) {
        logger.error(
            "Unexpected error occurred while collecting metrics for channel " + channelGenericName,
            e);
      }
    }

    logger.info(
        "Active Channels in queueManager {} are {}", context.getQueueManagerName(), activeChannels);
    Metric activeChannelsCountMetric =
        metricCreator.createMetric(
            "mq.manager.active.channels", activeChannels.size(), Attributes.empty());
    context.transformAndPrintMetric(activeChannelsCountMetric);

    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug("Time taken to publish metrics for all channels is {} milliseconds", exitTime);
  }

  private @NotNull List<Metric> getMetrics(
      PCFMessage message, String channelName, List<String> activeChannels) throws PCFException {
    List<Metric> responseMetrics = Lists.newArrayList();
    {
      int received = message.getIntParameterValue(CMQCFC.MQIACH_MSGS);
      Metric metric =
          metricCreator.createMetric(
              "mq.message.received.count",
              received,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
    }
    {
      int status = message.getIntParameterValue(CMQCFC.MQIACH_CHANNEL_STATUS);
      Metric metric =
          metricCreator.createMetric(
              "mq.status",
              status,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
      // We follow the definition of active channel as documented in
      // https://www.ibm.com/docs/en/ibm-mq/9.2.x?topic=states-current-active
      if (status != CMQCFC.MQCHS_RETRYING
          && status != CMQCFC.MQCHS_STOPPED
          && status != CMQCFC.MQCHS_STARTING) {
        activeChannels.add(channelName);
      }
    }
    {
      int bytesSent = message.getIntParameterValue(CMQCFC.MQIACH_BYTES_SENT);
      Metric metric =
          metricCreator.createMetric(
              "mq.byte.sent",
              bytesSent,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
    }
    {
      int bytesReceived = message.getIntParameterValue(CMQCFC.MQIACH_BYTES_RECEIVED);
      Metric metric =
          metricCreator.createMetric(
              "mq.byte.received",
              bytesReceived,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
    }
    {
      int buffersSent = message.getIntParameterValue(CMQCFC.MQIACH_BUFFERS_SENT);
      Metric metric =
          metricCreator.createMetric(
              "mq.buffers.sent",
              buffersSent,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
    }
    {
      int buffersReceived = message.getIntParameterValue(CMQCFC.MQIACH_BUFFERS_RECEIVED);
      Metric metric =
          metricCreator.createMetric(
              "mq.buffers.received",
              buffersReceived,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
    }
    {
      int currentSharingConvs = 0;
      if (message.getParameter(CMQCFC.MQIACH_CURRENT_SHARING_CONVS) != null) {
        currentSharingConvs = message.getIntParameterValue(CMQCFC.MQIACH_CURRENT_SHARING_CONVS);
      }
      Metric metric =
          metricCreator.createMetric(
              "mq.current.sharing.conversations",
              currentSharingConvs,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
    }
    {
      int maxSharingConvs = 0;
      if (message.getParameter(CMQCFC.MQIACH_MAX_SHARING_CONVS) != null) {
        maxSharingConvs = message.getIntParameterValue(CMQCFC.MQIACH_MAX_SHARING_CONVS);
      }
      Metric metric =
          metricCreator.createMetric(
              "mq.max.sharing.conversations",
              maxSharingConvs,
              Attributes.of(AttributeKey.stringKey("channel.name"), channelName));
      responseMetrics.add(metric);
    }
    return responseMetrics;
  }
}
