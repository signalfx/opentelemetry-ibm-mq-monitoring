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
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReadConfigurationEventQueueCollector implements MetricsPublisher {

  private static final Logger logger =
      LoggerFactory.getLogger(ReadConfigurationEventQueueCollector.class);
  private final OpenTelemetryMetricWriteHelper metricWriteHelper;
  private final QueueManager queueManager;
  private final PCFMessageAgent agent;
  private final MQQueueManager mqQueueManager;
  private final Map<String, WMQMetricOverride> metricsToReport;
  private final long bootTime;
  private final MetricCreator metricCreator;

  public ReadConfigurationEventQueueCollector(
      Map<String, WMQMetricOverride> metricsToReport,
      PCFMessageAgent agent,
      MQQueueManager mqQueueManager,
      QueueManager queueManager,
      OpenTelemetryMetricWriteHelper metricWriteHelper,
      MetricCreator metricCreator) {
    this.metricsToReport = metricsToReport;
    this.agent = agent;
    this.mqQueueManager = mqQueueManager;
    this.queueManager = queueManager;
    this.metricWriteHelper = metricWriteHelper;
    this.bootTime = System.currentTimeMillis();
    this.metricCreator = metricCreator;
  }

  private PCFMessage findLastUpdate(long entryTime, String configurationQueueName)
      throws Exception {
    // find the last update:
    PCFMessage candidate = null;

    boolean consumeEvents =
        this.queueManager.getConsumeConfigurationEventInterval() > 0
            && (entryTime - this.bootTime)
                    % this.queueManager.getConsumeConfigurationEventInterval()
                == 0;

    MQQueue queue = null;
    try {
      int queueAccessOptions = MQConstants.MQOO_FAIL_IF_QUIESCING | MQConstants.MQOO_INPUT_SHARED;
      if (!consumeEvents) {
        // we are not consuming the events.
        queueAccessOptions |= MQConstants.MQOO_BROWSE;
      }
      queue = mqQueueManager.accessQueue(configurationQueueName, queueAccessOptions);
      int maxSequenceNumber = 0;
      // keep going until receiving the exception MQConstants.MQRC_NO_MSG_AVAILABLE
      while (true) {
        try {
          MQGetMessageOptions getOptions = new MQGetMessageOptions();
          getOptions.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_FAIL_IF_QUIESCING;
          if (!consumeEvents) {
            getOptions.options |= MQConstants.MQGMO_BROWSE_NEXT;
          }
          MQMessage message = new MQMessage();

          queue.get(message, getOptions);
          PCFMessage received = new PCFMessage(message);
          if (received.getMsgSeqNumber() > maxSequenceNumber) {
            maxSequenceNumber = received.getMsgSeqNumber();
            candidate = received;
          }

        } catch (MQException e) {
          if (e.reasonCode != MQConstants.MQRC_NO_MSG_AVAILABLE) {
            logger.error(e.getMessage(), e);
          }
          break;
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
          break;
        }
      }
    } finally {
      if (queue != null) {
        queue.close();
      }
    }
    return candidate;
  }

  @Override
  public void publishMetrics() {
    long entryTime = System.currentTimeMillis();
    String configurationQueueName = this.queueManager.getConfigurationQueueName();
    logger.info(
        "sending PCF agent request to read configuration events from queue {}",
        configurationQueueName);
    try {

      PCFMessage candidate = findLastUpdate(entryTime, configurationQueueName);

      if (candidate == null) {
        if (queueManager.isRefreshQueueManagerConfigurationEnabled()) {
          // no event found.
          // we issue a refresh request, which will generate a configuration event on the
          // configuration event queue.
          // note this may incur a performance cost to the queue manager.
          PCFMessage request = new PCFMessage(CMQCFC.MQCMD_REFRESH_Q_MGR);
          request.addParameter(CMQCFC.MQIACF_REFRESH_TYPE, CMQCFC.MQRT_CONFIGURATION);
          request.addParameter(CMQCFC.MQIACF_OBJECT_TYPE, CMQC.MQOT_Q_MGR);
          agent.send(request);
          // try again:
          candidate = findLastUpdate(entryTime, configurationQueueName);
        }
      }

      if (candidate != null) {
        List<Metric> metrics = Lists.newArrayList();
        Iterator<String> overrideItr = metricsToReport.keySet().iterator();
        while (overrideItr.hasNext()) {
          String metrickey = overrideItr.next();
          WMQMetricOverride wmqOverride = metricsToReport.get(metrickey);
          if (candidate.getParameter(wmqOverride.getConstantValue()) != null) {
            int metricValue = candidate.getIntParameterValue(wmqOverride.getConstantValue());
            Metric m = metricCreator.createMetric(metrickey, metricValue, wmqOverride, metrickey);
            metrics.add(m);
          }
        }
        this.metricWriteHelper.transformAndPrintMetrics(metrics);
      }

    } catch (Exception e) {
      logger.error(
          "Unexpected error occurred while collecting configuration events for queue "
              + configurationQueueName,
          e);
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug(
        "Time taken to publish metrics for configuration events is {} milliseconds", exitTime);
  }
}
