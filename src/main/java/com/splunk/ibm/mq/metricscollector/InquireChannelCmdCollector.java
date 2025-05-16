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

import com.appdynamics.extensions.metrics.Metric;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.MQCFIL;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class is responsible for channel inquiry metric collection. */
public final class InquireChannelCmdCollector implements MetricsPublisher {

  public static final Logger logger = LoggerFactory.getLogger(InquireChannelCmdCollector.class);
  public static final String ARTIFACT = "Channels";
  private final MetricCreator metricCreator;
  private final MetricsCollectorContext context;

  public InquireChannelCmdCollector(MetricsCollectorContext context, MetricCreator metricCreator) {
    this.metricCreator = metricCreator;
    this.context = context;
  }

  @Override
  public void publishMetrics() {
    long entryTime = System.currentTimeMillis();

    if (context.hasNoMetricsToReport()) {
      logger.debug(
          "Channel metrics to report from the config is null or empty, nothing to publish");
      return;
    }

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
          logger.debug("Pulling out metrics for channel name {}", channelName);
          List<Metric> responseMetrics = getMetrics(message, channelName);
          context.transformAndPrintMetrics(responseMetrics);
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

  private @NotNull List<Metric> getMetrics(PCFMessage message, String channelName)
      throws PCFException {
    List<Metric> responseMetrics = Lists.newArrayList();
    context.forEachMetric(
        (metrickey, wmqOverride) -> {
          if (message.getParameter(wmqOverride.getConstantValue()) == null) {
            logger.debug("Missing property {} on {}", metrickey, channelName);
            return;
          }
          int metricVal = message.getIntParameterValue(wmqOverride.getConstantValue());
          Metric metric =
              metricCreator.createMetric(metrickey, metricVal, wmqOverride, channelName, metrickey);
          responseMetrics.add(metric);
        });
    return responseMetrics;
  }
}
