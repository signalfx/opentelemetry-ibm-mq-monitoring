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

import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InquireQCmdCollector implements MetricsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(InquireQCmdCollector.class);
  static final String COMMAND = "MQCMD_INQUIRE_Q";
  private final MetricsCollectorContext context;
  private final QueueCollectionBuddy queueBuddy;

  public InquireQCmdCollector(MetricsCollectorContext context, QueueCollectionBuddy queueBuddy) {
    this.context = context;
    this.queueBuddy = queueBuddy;
  }

  @Override
  public void publishMetrics() {
    logger.info("Collecting metrics for command {}", COMMAND);
    /*
     * attrs = { CMQC.MQCA_Q_NAME, CMQC.MQIA_CURRENT_Q_DEPTH, CMQC.MQIA_MAX_Q_DEPTH, CMQC.MQIA_OPEN_INPUT_COUNT, CMQC.MQIA_OPEN_OUTPUT_COUNT };
     */
    long entryTime = System.currentTimeMillis();

    Set<String> queueGenericNames = context.getQueueIncludeFilterNames();
    for (String queueGenericName : queueGenericNames) {
      // list of all metrics extracted through MQCMD_INQUIRE_Q is mentioned here
      // https://www.ibm.com/support/knowledgecenter/SSFKSJ_7.5.0/com.ibm.mq.ref.adm.doc/q087810_.htm
      PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
      request.addParameter(CMQC.MQCA_Q_NAME, queueGenericName);
      request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_ALL);
      request.addParameter(CMQCFC.MQIACF_Q_ATTRS, CMQCFC.MQIACF_ALL);

      queueBuddy.processPCFRequestAndPublishQMetrics(request, queueGenericName);
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug(
        "Time taken to publish metrics for all queues is {} milliseconds for command {}",
        exitTime,
        COMMAND);
  }
}
