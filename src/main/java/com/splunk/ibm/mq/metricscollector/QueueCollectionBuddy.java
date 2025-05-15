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

import static com.ibm.mq.constants.CMQC.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.appdynamics.extensions.metrics.Metric;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.*;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collaborator buddy of the queue collectors that helps them to send a message, process the
 * response, and generate metrics.
 */
final class QueueCollectionBuddy {
  private static final Logger logger = LoggerFactory.getLogger(QueueCollectionBuddy.class);

  private final MetricsCollectorContext context;
  private final QueueCollectorSharedState sharedState;
  private final MetricCreator metricCreator;
  private final String command;

  QueueCollectionBuddy(
      MetricsCollectorContext context,
      QueueCollectorSharedState sharedState,
      MetricCreator metricCreator,
      String command) {
    this.context = context;
    this.sharedState = sharedState;
    this.metricCreator = metricCreator;
    this.command = command;
  }

  /**
   * Sends a PCFMessage request, reads the response, and generates metrics from the response. It
   * handles all exceptions.
   */
  void processPCFRequestAndPublishQMetrics(PCFMessage request, String queueGenericName) {
    try {
      doProcessPCFRequestAndPublishQMetrics(request, queueGenericName);
    } catch (PCFException pcfe) {
      logger.error(
          "PCFException caught while collecting metric for Queue: {} for command {}",
          queueGenericName,
          command,
          pcfe);
      if (pcfe.exceptionSource instanceof PCFMessage[]) {
        PCFMessage[] msgs = (PCFMessage[]) pcfe.exceptionSource;
        for (PCFMessage msg : msgs) {
          logger.error(msg.toString());
        }
      }
      if (pcfe.exceptionSource instanceof PCFMessage) {
        PCFMessage msg = (PCFMessage) pcfe.exceptionSource;
        logger.error(msg.toString());
      }
      // Don't throw exception as it will stop queue metric collection
    } catch (Exception mqe) {
      logger.error("MQException caught", mqe);
      // Don't throw exception as it will stop queue metric collection
    }
  }

  private void doProcessPCFRequestAndPublishQMetrics(PCFMessage request, String queueGenericName)
      throws IOException, MQDataException {
    logger.debug(
        "sending PCF agent request to query metrics for generic queue {} for command {}",
        queueGenericName,
        command);
    long startTime = System.currentTimeMillis();
    List<PCFMessage> response = context.send(request);
    long endTime = System.currentTimeMillis() - startTime;
    logger.debug(
        "PCF agent queue metrics query response for generic queue {} for command {} received in {} milliseconds",
        queueGenericName,
        command,
        endTime);
    if (response.isEmpty()) {
      logger.debug(
          "Unexpected error while PCFMessage.send() for command {}, response is empty", command);
      return;
    }

    List<PCFMessage> messages =
        MessageFilter.ofKind("queue")
            .excluding(context.getQueueExcludeFilters())
            .withResourceExtractor(MessageBuddy::queueName)
            .filter(response);

    for (PCFMessage message : messages) {
      handleMessage(message);
    }
  }

  private void handleMessage(PCFMessage message) throws PCFException {
    String queueName = MessageBuddy.queueName(message);
    String queueType = getQueueTypeFromName(message, queueName);
    if (queueType == null) {
      logger.info("Unable to determine queue type for queue name = {}", queueName);
      return;
    }

    logger.debug("Pulling out metrics for queue name {} for command {}", queueName, command);
    List<Metric> responseMetrics = getMetrics(message, queueName, queueType);
    context.transformAndPrintMetrics(responseMetrics);
  }

  private String getQueueTypeFromName(PCFMessage message, String queueName) throws PCFException {
    if (message.getParameterValue(CMQC.MQIA_Q_TYPE) == null) {
      return sharedState.getType(queueName);
    }

    String queueType = getQueueType(message);
    sharedState.putQueueType(queueName, queueType);
    return queueType;
  }

  private static String getQueueType(PCFMessage message) throws PCFException {
    String baseQueueType = getBaseQueueType(message);
    return maybeAppendUsage(message, baseQueueType);
  }

  private static String maybeAppendUsage(PCFMessage message, String baseQueueType)
      throws PCFException {
    if (message.getParameter(CMQC.MQIA_USAGE) == null) {
      return baseQueueType;
    }
    switch (message.getIntParameterValue(CMQC.MQIA_USAGE)) {
      case CMQC.MQUS_NORMAL:
        return baseQueueType + "-normal";
      case CMQC.MQUS_TRANSMISSION:
        return baseQueueType + "-transmission";
    }
    return baseQueueType;
  }

  private static String getBaseQueueType(PCFMessage message) throws PCFException {
    switch (message.getIntParameterValue(CMQC.MQIA_Q_TYPE)) {
      case MQQT_LOCAL:
        return "local";
      case MQQT_ALIAS:
        return "alias";
      case MQQT_REMOTE:
        return "remote";
      case MQQT_CLUSTER:
        return "cluster";
      case MQQT_MODEL:
        return "model";
    }
    logger.warn("Unknown type of queue {}", message.getIntParameterValue(CMQC.MQIA_Q_TYPE));
    return "unknown";
  }

  private List<Metric> getMetrics(PCFMessage pcfMessage, String queueName, String queueType)
      throws PCFException {
    List<Metric> responseMetrics = Lists.newArrayList();
    context.forEachMetric(
        (metricKey, wmqOverride) -> {
          try {
            List<Metric> tempMetrics =
                buildMetrics(pcfMessage, queueName, queueType, metricKey, wmqOverride);
            responseMetrics.addAll(tempMetrics);
          } catch (PCFException pcfe) {
            logger.error(
                "PCFException caught while collecting metric for Queue: {} for metric: {} in command {}",
                queueName,
                wmqOverride.getIbmCommand(),
                command,
                pcfe);
          }
        });
    return responseMetrics;
  }

  private List<Metric> buildMetrics(
      PCFMessage pcfMessage,
      String queueName,
      String queueType,
      String metricKey,
      WMQMetricOverride wmqOverride)
      throws PCFException {
    PCFParameter pcfParam = pcfMessage.getParameter(wmqOverride.getConstantValue());
    if (pcfParam == null) {
      logger.warn(
          "PCF parameter {} is null in response for Queue: {} for metric: {} in command {}",
          wmqOverride.getConstantValue(),
          queueName,
          wmqOverride.getIbmCommand(),
          command);
      return emptyList();
    }
    if (pcfParam instanceof MQCFIN) {
      int metricVal = pcfMessage.getIntParameterValue(wmqOverride.getConstantValue());
      Metric metric =
          metricCreator.createMetric(
              metricKey, metricVal, wmqOverride, queueName, queueType, metricKey);
      return singletonList(metric);
    }
    List<Metric> tempMetrics = new ArrayList<>();
    if (pcfParam instanceof MQCFIL) {
      int[] metricVals = pcfMessage.getIntListParameterValue(wmqOverride.getConstantValue());
      if (metricVals == null) {
        return emptyList();
      }

      int count = 0;
      for (int val : metricVals) {
        count++;
        String metricName = metricKey + "_" + count;
        Metric metric =
            metricCreator.createMetric(metricName, val, wmqOverride, queueName, metricName);
        tempMetrics.add(metric);
      }
    }
    return tempMetrics;
  }
}
