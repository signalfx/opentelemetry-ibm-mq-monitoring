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

import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.MQCFIL;
import com.ibm.mq.headers.pcf.MQCFIN;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFParameter;
import com.splunk.ibm.mq.metrics.Metrics;
import com.splunk.ibm.mq.metrics.MetricsConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@FunctionalInterface
interface MetricCallback {

  void accept(PCFMessage message, Attributes attributes) throws PCFException;
}
/**
 * A collaborator buddy of the queue collectors that helps them to send a message, process the
 * response, and generate metrics.
 */
final class QueueCollectionBuddy {
  private static final Logger logger = LoggerFactory.getLogger(QueueCollectionBuddy.class);

  private final QueueCollectorSharedState sharedState;

  QueueCollectionBuddy(QueueCollectorSharedState sharedState) {
    this.sharedState = sharedState;
  }



  /**
   * Sends a PCFMessage request, reads the response, and generates metrics from the response. It
   * handles all exceptions.
   */
  void processPCFRequestAndPublishQMetrics(
      MetricsCollectorContext context, PCFMessage request, String queueGenericName, MetricCallback callback) {
    try {
      doProcessPCFRequestAndPublishQMetrics(context, request, queueGenericName, callback);
    } catch (PCFException pcfe) {
      logger.error(
          "PCFException caught while collecting metric for Queue: {}", queueGenericName, pcfe);
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

  private void doProcessPCFRequestAndPublishQMetrics(
      MetricsCollectorContext context, PCFMessage request, String queueGenericName, MetricCallback callback)
      throws IOException, MQDataException {
    logger.debug(
        "sending PCF agent request to query metrics for generic queue {}", queueGenericName);
    long startTime = System.currentTimeMillis();
    List<PCFMessage> response = context.send(request);
    long endTime = System.currentTimeMillis() - startTime;
    logger.debug(
        "PCF agent queue metrics query response for generic queue {} received in {} milliseconds",
        queueGenericName,
        endTime);
    if (response.isEmpty()) {
      logger.debug("Unexpected error while PCFMessage.send(), response is empty");
      return;
    }

    List<PCFMessage> messages =
        MessageFilter.ofKind("queue")
            .excluding(context.getQueueExcludeFilters())
            .withResourceExtractor(MessageBuddy::queueName)
            .filter(response);

    for (PCFMessage message : messages) {
      handleMessage(context, message, callback);
    }
  }

  private void handleMessage(MetricsCollectorContext context, PCFMessage message, MetricCallback callback)
      throws PCFException {
    String queueName = MessageBuddy.queueName(message);
    String queueType = getQueueTypeFromName(message, queueName);
    if (queueType == null) {
      logger.info("Unable to determine queue type for queue name = {}", queueName);
      return;
    }

    logger.debug("Pulling out metrics for queue name {}", queueName);

    Attributes attributes =
            Attributes.of(
                    AttributeKey.stringKey("queue.name"),
                    queueName,
                    AttributeKey.stringKey("queue.type"),
                    queueType,
                    AttributeKey.stringKey("queue.manager"),
                    context.getQueueManagerName());
    callback.accept(message, attributes);
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

}
