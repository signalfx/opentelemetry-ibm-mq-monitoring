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

import com.appdynamics.extensions.metrics.Metric;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.splunk.ibm.mq.config.ExcludeFilters;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for channel metric collection. */
public final class ChannelMetricsCollector implements MetricsPublisher {

  public static final Logger logger = LoggerFactory.getLogger(ChannelMetricsCollector.class);
  public static final String ARTIFACT = "Channels";
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
  public void publishMetrics() {
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
        PCFMessage[] response = context.send(request);
        long endTime = System.currentTimeMillis() - startTime;
        logger.debug(
            "PCF agent queue metrics query response for generic queue {} received in {} milliseconds",
            channelGenericName,
            endTime);
        if (response == null || response.length <= 0) {
          logger.debug(
              "Unexpected Error while PCFMessage.send(), response is either null or empty");
          return;
        }
        for (PCFMessage pcfMessage : response) {
          String channelName =
              pcfMessage.getStringParameterValue(CMQCFC.MQCACH_CHANNEL_NAME).trim();
          Set<ExcludeFilters> excludeFilters = context.getChannelExcludeFilters();
          if (!ExcludeFilters.isExcluded(
              channelName, excludeFilters)) { // check for exclude filters
            logger.debug("Pulling out metrics for channel name {}", channelName);
            List<Metric> responseMetrics = Lists.newArrayList();
            context.forEachMetric(
                (metrickey, wmqOverride) -> {
                  int metricVal = pcfMessage.getIntParameterValue(wmqOverride.getConstantValue());
                  Metric metric =
                      metricCreator.createMetric(
                          metrickey, metricVal, wmqOverride, channelName, metrickey);
                  responseMetrics.add(metric);
                  // We follow the definition of active channel as documented in
                  // https://www.ibm.com/docs/en/ibm-mq/9.2.x?topic=states-current-active
                  if ("Status".equals(metrickey)
                      && metricVal != CMQCFC.MQCHS_RETRYING
                      && metricVal != CMQCFC.MQCHS_STOPPED
                      && metricVal != CMQCFC.MQCHS_STARTING) {
                    activeChannels.add(channelName);
                  }
                });
            context.transformAndPrintMetrics(responseMetrics);
          } else {
            logger.debug("Channel name {} is excluded.", channelName);
          }
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
            "Unexpected Error occurred while collecting metrics for channel " + channelGenericName,
            e);
      }
    }

    logger.info(
        "Active Channels in queueManager {} are {}", context.getQueueManagerName(), activeChannels);
    Metric activeChannelsCountMetric =
        metricCreator.createMetric(
            "ActiveChannelsCount", activeChannels.size(), null, "ActiveChannelsCount");
    context.transformAndPrintMetric(activeChannelsCountMetric);

    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug("Time taken to publish metrics for all channels is {} milliseconds", exitTime);
  }
}
