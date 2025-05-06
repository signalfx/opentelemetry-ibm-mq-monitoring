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
package com.appdynamics.extensions.webspheremq.metricscollector;

import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ResetQStatsCmdCollector implements MetricsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(ResetQStatsCmdCollector.class);

  static final String COMMAND = "MQCMD_RESET_Q_STATS";
  private final MetricsCollectorContext context;
  private final QueueCollectionBuddy queueBuddy;

  ResetQStatsCmdCollector(MetricsCollectorContext context, QueueCollectionBuddy queueBuddy) {
    this.context = context;
    this.queueBuddy = queueBuddy;
  }

  @Override
  public void publishMetrics() {
    logger.info("Collecting metrics for command {}", COMMAND);
    /*
     * attrs = { CMQC.MQCA_Q_NAME, MQIA_HIGH_Q_DEPTH,MQIA_MSG_DEQ_COUNT, MQIA_MSG_ENQ_COUNT };
     */
    long entryTime = System.currentTimeMillis();

    if (context.hasNoMetricsToReport()) {
      logger.debug(
          "Queue metrics to report from the config is null or empty, nothing to publish for command {}",
          COMMAND);
      return;
    }

    int[] attrs = context.buildIntAttributesArray(CMQC.MQCA_Q_NAME);
    logger.debug(
        "Attributes being sent along PCF agent request to query queue metrics: {} for command {}",
        Arrays.toString(attrs),
        COMMAND);

    Set<String> queueGenericNames = context.getQueueIncludeFilterNames();
    for (String queueGenericName : queueGenericNames) {
      // list of all metrics extracted through MQCMD_RESET_Q_STATS is mentioned here
      // https://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.ref.adm.doc/q088310_.htm
      PCFMessage request = new PCFMessage(CMQCFC.MQCMD_RESET_Q_STATS);
      request.addParameter(CMQC.MQCA_Q_NAME, queueGenericName);
      queueBuddy.processPCFRequestAndPublishQMetrics(request, queueGenericName);
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug(
        "Time taken to publish metrics for all queues is {} milliseconds for command {}",
        exitTime,
        COMMAND);
  }
}
