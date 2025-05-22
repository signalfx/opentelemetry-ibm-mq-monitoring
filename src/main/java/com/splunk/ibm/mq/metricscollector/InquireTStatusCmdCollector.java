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

import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.MQCFIN;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFParameter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InquireTStatusCmdCollector implements MetricsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(InquireTStatusCmdCollector.class);
  static final String ARTIFACT = "Topics";
  private final MetricCreator metricCreator;

  static final String COMMAND = "MQCMD_INQUIRE_TOPIC_STATUS";
  private final MetricsCollectorContext context;

  public InquireTStatusCmdCollector(MetricsCollectorContext context, MetricCreator metricCreator) {
    this.context = context;
    this.metricCreator = metricCreator;
  }

  @Override
  public void publishMetrics() {
    logger.info("Collecting metrics for command {}", COMMAND);
    long entryTime = System.currentTimeMillis();

    if (context.hasNoMetricsToReport()) {
      logger.debug(
          "Topic metrics to report from the config is null or empty, nothing to publish for command {}",
          COMMAND);
      return;
    }
    Set<String> topicGenericNames = context.getTopicIncludeFilterNames();
    //  to query the current status of topics, which is essential for monitoring and managing the
    // publish/subscribe environment in IBM MQ.
    for (String topicGenericName : topicGenericNames) {
      // Request:
      // https://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.ref.adm.doc/q088140_.htm
      // list of all metrics extracted through MQCMD_INQUIRE_TOPIC_STATUS is mentioned here
      // https://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.ref.adm.doc/q088150_.htm
      PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS);
      request.addParameter(CMQC.MQCA_TOPIC_STRING, topicGenericName);

      try {
        processPCFRequestAndPublishQMetrics(topicGenericName, request, COMMAND);
      } catch (PCFException pcfe) {
        logger.error(
            "PCFException caught while collecting metric for Queue: {} for command {}",
            topicGenericName,
            COMMAND,
            pcfe);
        PCFMessage[] msgs = (PCFMessage[]) pcfe.exceptionSource;
        for (PCFMessage msg : msgs) {
          logger.error(msg.toString());
        }
        // Don't throw exception as it will stop queue metric colloection
      } catch (Exception mqe) {
        logger.error("MQException caught", mqe);
        // Dont throw exception as it will stop queuemetric colloection
      }
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug(
        "Time taken to publish metrics for all queues is {} milliseconds for command {}",
        exitTime,
        COMMAND);
  }

  private void processPCFRequestAndPublishQMetrics(
      String topicGenericName, PCFMessage request, String command)
      throws IOException, MQDataException {
    logger.debug(
        "sending PCF agent request to topic metrics for generic topic {} for command {}",
        topicGenericName,
        command);
    long startTime = System.currentTimeMillis();
    List<PCFMessage> response = context.send(request);
    long endTime = System.currentTimeMillis() - startTime;
    logger.debug(
        "PCF agent topic metrics query response for generic topic {} for command {} received in {} milliseconds",
        topicGenericName,
        command,
        endTime);
    if (response.isEmpty()) {
      logger.debug(
          "Unexpected error while PCFMessage.send() for command {}, response is either null or empty",
          command);
      return;
    }

    List<PCFMessage> messages =
        MessageFilter.ofKind("topic")
            .excluding(context.getTopicExcludeFilters())
            .withResourceExtractor(MessageBuddy::topicName)
            .filter(response);

    for (PCFMessage message : messages) {
      String topicName = MessageBuddy.topicName(message);
      logger.debug("Pulling out metrics for topic name {} for command {}", topicName, command);
      List<Metric> responseMetrics = extractMetrics(command, message, topicName);
      context.transformAndPrintMetrics(responseMetrics);
    }
  }

  private List<Metric> extractMetrics(String command, PCFMessage pcfMessage, String topicString)
      throws PCFException {
    List<Metric> responseMetrics = Lists.newArrayList();
    context.forEachMetric(
        (metrickey, wmqOverride) -> {
          try {
            PCFParameter pcfParam = pcfMessage.getParameter(wmqOverride.getConstantValue());
            if (pcfParam instanceof MQCFIN) {
              int metricVal = pcfMessage.getIntParameterValue(wmqOverride.getConstantValue());
              Metric metric =
                  metricCreator.createMetric(
                      metrickey, metricVal, wmqOverride, topicString, metrickey);
              responseMetrics.add(metric);
            }
          } catch (PCFException pcfe) {
            logger.error(
                "PCFException caught while collecting metric for Topic: {} for metric: {} in command {}",
                topicString,
                wmqOverride.getIbmCommand(),
                command,
                pcfe);
          }
        });
    return responseMetrics;
  }
}
