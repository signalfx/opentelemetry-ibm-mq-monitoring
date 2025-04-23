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
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.config.ExcludeFilters;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.*;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class TopicMetricsCollector extends MetricsCollector implements Runnable {
    private static final Logger logger = ExtensionsLoggerFactory.getLogger(TopicMetricsCollector.class);
    private final String artifact = "Topics";

    public TopicMetricsCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper, CountDownLatch countDownLatch) {
        this.metricsToReport = metricsToReport;
        this.monitorContextConfig = monitorContextConfig;
        this.agent = agent;
        this.metricWriteHelper = metricWriteHelper;
        this.queueManager = queueManager;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        try {
            this.process();
        } catch (TaskExecutionException e) {
            logger.error("Error in TopicMetricsCollector ", e);
        } finally {
            countDownLatch.countDown();
        }
    }

    @Override
    protected void publishMetrics() throws TaskExecutionException {
        logger.info("Collecting Topic metrics...");
        List<Future> futures = Lists.newArrayList();

        Map<String, WMQMetricOverride>  metricsForInquireTStatusCmd = getMetricsToReport(InquireTStatusCmdCollector.COMMAND);
        if(!metricsForInquireTStatusCmd.isEmpty()){
            futures.add(monitorContextConfig.getContext().getExecutorService().submit("Topic Status Cmd Collector", new InquireTStatusCmdCollector(this, metricsForInquireTStatusCmd)));
        }
        for(Future f: futures){
            try {
                long timeout = 20;
                if(monitorContextConfig.getConfigYml().get("topicMetricsCollectionTimeoutInSeconds") != null){
                    timeout = (Integer)monitorContextConfig.getConfigYml().get("topicMetricsCollectionTimeoutInSeconds");
                }
                f.get(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("The thread was interrupted ",e);
            } catch (ExecutionException e) {
                logger.error("Something unforeseen has happened ",e);
            } catch (TimeoutException e) {
                logger.error("Thread timed out ",e);
            }
        }
    }



    private Map<String, WMQMetricOverride> getMetricsToReport(String command) {
        Map<String, WMQMetricOverride> commandMetrics = Maps.newHashMap();
        if (getMetricsToReport() == null || getMetricsToReport().isEmpty()) {
            logger.debug("There are no metrics configured for {}",command);
            return commandMetrics;
        }
        Iterator<String> itr = getMetricsToReport().keySet().iterator();
        while (itr.hasNext()) {
            String metrickey = itr.next();
            WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
            if(wmqOverride.getIbmCommand().equalsIgnoreCase(command)){
                commandMetrics.put(metrickey,wmqOverride);
            }
        }
        return commandMetrics;
    }

    @Override
    public String getArtifact() {
        return artifact;
    }

    @Override
    public Map<String, WMQMetricOverride> getMetricsToReport() {
        return this.metricsToReport;
    }
}
