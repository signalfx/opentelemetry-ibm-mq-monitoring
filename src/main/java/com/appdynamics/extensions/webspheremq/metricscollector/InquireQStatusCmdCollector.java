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

import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

final class InquireQStatusCmdCollector extends QueueMetricsCollector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(InquireQStatusCmdCollector.class);

    static final String COMMAND = "MQCMD_INQUIRE_Q_STATUS";

    public InquireQStatusCmdCollector(QueueMetricsCollector collector, Map<String, WMQMetricOverride> metricsToReport,
                                      QueueCollectorSharedState sharedState, MetricCreator metricCreator){
        super(metricsToReport, collector.monitorContextConfig, collector.agent, collector.metricWriteHelper,
                collector.queueManager, collector.countDownLatch, sharedState, metricCreator);
    }

    @Override
    public void run() {
        try {
            publishMetrics();
        } catch (TaskExecutionException e) {
            logger.error("Something unforeseen has happened ",e);
        }
    }

    @Override
    public void publishMetrics() throws TaskExecutionException {
        logger.info("Collecting metrics for command {}", COMMAND);
        long entryTime = System.currentTimeMillis();

        if (getMetricsToReport() == null || getMetricsToReport().isEmpty()) {
            logger.debug("Queue metrics to report from the config is null or empty, nothing to publish for command {}", COMMAND);
            return;
        }

        //
        // attrs = { CMQC.MQCA_Q_NAME, MQIACF_OLDEST_MSG_AGE, MQIACF_Q_TIME_INDICATOR };
        //
        int[] attrs = getIntAttributesArray(CMQC.MQCA_Q_NAME);
        if (logger.isDebugEnabled()) {
            logger.debug("Attributes being sent along PCF agent request to query queue metrics: {} for command {}", Arrays.toString(attrs), COMMAND);
        }

        Set<String> queueGenericNames = this.queueManager.getQueueFilters().getInclude();
        for(String queueGenericName : queueGenericNames){
            // list of all metrics extracted through MQCMD_INQUIRE_Q_STATUS is mentioned here https://www.ibm.com/support/knowledgecenter/SSFKSJ_8.0.0/com.ibm.mq.ref.adm.doc/q087880_.htm
            PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_STATUS);
            request.addParameter(CMQC.MQCA_Q_NAME, queueGenericName);
            request.addParameter(CMQCFC.MQIACF_Q_STATUS_ATTRS, attrs);
            try {
                processPCFRequestAndPublishQMetrics(queueGenericName, request,COMMAND);
            } catch (PCFException pcfe) {
                logger.error("PCFException caught while collecting metric for Queue: {} for command {}",queueGenericName,COMMAND, pcfe);
                PCFMessage[] msgs = (PCFMessage[]) pcfe.exceptionSource;
                for (PCFMessage msg : msgs) {
                    logger.error(msg.toString());
                }
                // Dont throw exception as it will stop queuemetric colloection
            } catch (Exception mqe) {
                logger.error("MQException caught", mqe);
                // Dont throw exception as it will stop queuemetric colloection
            }
        }
        long exitTime = System.currentTimeMillis() - entryTime;
        logger.debug("Time taken to publish metrics for all queues is {} milliseconds for command {}", exitTime,COMMAND);
    }
}
