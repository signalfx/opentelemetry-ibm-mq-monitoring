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

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.config.ExcludeFilters;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;


final public class ListenerMetricsCollector extends MetricsCollector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ListenerMetricsCollector.class);
    private final static String ARTIFACT = "Listeners";

    public ListenerMetricsCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper, CountDownLatch countDownLatch) {
        super(metricsToReport, monitorContextConfig, agent, metricWriteHelper, queueManager, countDownLatch, ARTIFACT);
    }

    @Override
    public void run() {
        try {
            super.process();
        } catch (TaskExecutionException e) {
            logger.error("Error in ListenerMetricsCollector ", e);
        } finally {
            countDownLatch.countDown();
        }
    }

    @Override
    protected void publishMetrics() throws TaskExecutionException {
        long entryTime = System.currentTimeMillis();

        if (getMetricsToReport() == null || getMetricsToReport().isEmpty()) {
            logger.debug("Listener metrics to report from the config is null or empty, nothing to publish");
            return;
        }

        int[] attrs = getIntAttributesArray(CMQCFC.MQCACH_LISTENER_NAME);
        logger.debug("Attributes being sent along PCF agent request to query channel metrics: " + Arrays.toString(attrs));

        Set<String> listenerGenericNames = this.queueManager.getListenerFilters().getInclude();
        for(String listenerGenericName : listenerGenericNames){
            PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_LISTENER_STATUS);
            request.addParameter(CMQCFC.MQCACH_LISTENER_NAME, listenerGenericName);
            request.addParameter(CMQCFC.MQIACF_LISTENER_STATUS_ATTRS, attrs);
            try {
                logger.debug("sending PCF agent request to query metrics for generic listener {}", listenerGenericName);
                long startTime = System.currentTimeMillis();
                PCFMessage[] response = agent.send(request);
                long endTime = System.currentTimeMillis() - startTime;
                logger.debug("PCF agent listener metrics query response for generic listener {} received in {} milliseconds", listenerGenericName, endTime);
                if (response == null || response.length <= 0) {
                    logger.debug("Unexpected Error while PCFMessage.send(), response is either null or empty");
                    return;
                }
                for (PCFMessage pcfMessage : response) {
                    String listenerName = pcfMessage.getStringParameterValue(CMQCFC.MQCACH_LISTENER_NAME).trim();
                    Set<ExcludeFilters> excludeFilters = this.queueManager.getListenerFilters().getExclude();
                    if (!ExcludeFilters.isExcluded(listenerName, excludeFilters)) { //check for exclude filters
                        logger.debug("Pulling out metrics for listener name {}", listenerName);
                        Iterator<String> itr = getMetricsToReport().keySet().iterator();
                        List<Metric> metrics = Lists.newArrayList();
                        while (itr.hasNext()) {
                            String metrickey = itr.next();
                            WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
                            int metricVal = pcfMessage.getIntParameterValue(wmqOverride.getConstantValue());
                            Metric metric = createMetric(queueManager, metrickey, metricVal, wmqOverride, getArtifact(), listenerName, metrickey);
                            metrics.add(metric);
                        }
                        metricWriteHelper.transformAndPrintMetrics(metrics);
                    } else {
                        logger.debug("Listener name {} is excluded.", listenerName);
                    }
                }
            }
            catch (Exception e) {
                logger.error("Unexpected Error occoured while collecting metrics for listener " + listenerGenericName, e);
            }
        }
        long exitTime = System.currentTimeMillis() - entryTime;
        logger.debug("Time taken to publish metrics for all listener is {} milliseconds", exitTime);

    }
}
