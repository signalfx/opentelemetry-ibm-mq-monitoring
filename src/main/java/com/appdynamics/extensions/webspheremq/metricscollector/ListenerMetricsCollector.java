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

import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.config.ExcludeFilters;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ListenerMetricsCollector is a specialized implementation of the MetricsCollector that is
 * responsible for collecting and publishing metrics related to IBM MQ Listeners.
 *
 * <p>This class interacts with PCFMessageAgent to query metrics for specific listeners, applies
 * "include:" and "exclude:" listenerFilters defined in config yaml, and uses MetricWriteHelper to
 * publish the collected metrics in the required format.
 *
 * <p>Key functionalities include: • query using PCF Command: MQCMD_INQUIRE_LISTENER_STATUS to get
 * the status of one or more listeners on a queue manager. • retrieve tcp/ip listeners runtime
 * information such as: - listener is running or stopped - port number and transport type - last
 * error codes - associated command server •
 *
 * <p>It utilizes WMQMetricOverride to map metrics from the configuration to their IBM MQ constants.
 */
public final class ListenerMetricsCollector implements MetricsPublisher {

  private static final Logger logger = LoggerFactory.getLogger(ListenerMetricsCollector.class);
  public static final String ARTIFACT = "Listeners";
  private final MetricCreator metricCreator;
  private final MetricsCollectorContext context;

  public ListenerMetricsCollector(MetricsCollectorContext context, MetricCreator metricCreator) {
    this.metricCreator = metricCreator;
    this.context = context;
  }

  @Override
  public void publishMetrics() {
    long entryTime = System.currentTimeMillis();

    if (context.hasNoMetricsToReport()) {
      logger.debug(
          "Listener metrics to report from the config is null or empty, nothing to publish");
      return;
    }

    int[] attrs = context.buildIntAttributesArray(CMQCFC.MQCACH_LISTENER_NAME);
    logger.debug(
        "Attributes being sent along PCF agent request to query channel metrics: "
            + Arrays.toString(attrs));

    Set<String> listenerGenericNames = context.getListenerIncludeFilterNames();
    for (String listenerGenericName : listenerGenericNames) {
      PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_LISTENER_STATUS);
      request.addParameter(CMQCFC.MQCACH_LISTENER_NAME, listenerGenericName);
      request.addParameter(CMQCFC.MQIACF_LISTENER_STATUS_ATTRS, attrs);
      try {
        logger.debug(
            "sending PCF agent request to query metrics for generic listener {}",
            listenerGenericName);
        long startTime = System.currentTimeMillis();
        PCFMessage[] response = context.send(request);
        long endTime = System.currentTimeMillis() - startTime;
        logger.debug(
            "PCF agent listener metrics query response for generic listener {} received in {} milliseconds",
            listenerGenericName,
            endTime);
        if (response == null || response.length <= 0) {
          logger.debug(
              "Unexpected Error while PCFMessage.send(), response is either null or empty");
          return;
        }
        for (PCFMessage pcfMessage : response) {
          String listenerName =
              pcfMessage.getStringParameterValue(CMQCFC.MQCACH_LISTENER_NAME).trim();
          Set<ExcludeFilters> excludeFilters = context.getListenerExcludeFilters();
          if (!ExcludeFilters.isExcluded(
              listenerName, excludeFilters)) { // check for exclude filters
            logger.debug("Pulling out metrics for listener name {}", listenerName);
            List<Metric> responseMetrics = Lists.newArrayList();
            context.forEachMetric(
                (metricKey, wmqOverride) -> {
                  int metricVal = pcfMessage.getIntParameterValue(wmqOverride.getConstantValue());
                  Metric metric =
                      metricCreator.createMetric(
                          metricKey, metricVal, wmqOverride, listenerName, metricKey);
                  responseMetrics.add(metric);
                });
            context.transformAndPrintMetrics(responseMetrics);
          } else {
            logger.debug("Listener name {} is excluded.", listenerName);
          }
        }
      } catch (Exception e) {
        logger.error(
            "Unexpected Error occurred while collecting metrics for listener "
                + listenerGenericName,
            e);
      }
    }
    long exitTime = System.currentTimeMillis() - entryTime;
    logger.debug("Time taken to publish metrics for all listener is {} milliseconds", exitTime);
  }
}
