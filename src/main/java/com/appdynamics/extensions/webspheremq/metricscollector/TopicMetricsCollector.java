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
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class TopicMetricsCollector extends MetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(TopicMetricsCollector.class);
    private final Map<String, WMQMetricOverride> metrics;

    public TopicMetricsCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper, CountDownLatch countDownLatch) {
        super(monitorContextConfig, agent, metricWriteHelper, queueManager, countDownLatch);
        this.metrics = metricsToReport;
    }

    @Override
    public void publishMetrics() throws TaskExecutionException {
        logger.info("Collecting Topic metrics...");
        List<Future> futures = Lists.newArrayList();

        //  to query the current status of topics, which is essential for monitoring and managing the publish/subscribe environment in IBM MQ.
        Map<String, WMQMetricOverride> metricsForInquireTStatusCmd = getMetricsToReport(InquireTStatusCmdCollector.COMMAND);
        if (!metricsForInquireTStatusCmd.isEmpty()) {
            MetricCreator metricCreator = new MetricCreator(monitorContextConfig.getMetricPrefix(), queueManager, InquireTStatusCmdCollector.ARTIFACT);
            InquireTStatusCmdCollector metricsPublisher = new InquireTStatusCmdCollector(this, metricsForInquireTStatusCmd, metricCreator);
            MetricsPublisherJob job = new MetricsPublisherJob(metricsPublisher, countDownLatch);
            futures.add(monitorContextConfig.getContext().getExecutorService()
                    .submit("Topic Status Cmd Collector", job));
        }
        for (Future f : futures) {
            try {
                long timeout = 20;
                if (monitorContextConfig.getConfigYml().get("topicMetricsCollectionTimeoutInSeconds") != null) {
                    timeout = (Integer) monitorContextConfig.getConfigYml().get("topicMetricsCollectionTimeoutInSeconds");
                }
                f.get(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("The thread was interrupted ", e);
            } catch (ExecutionException e) {
                logger.error("Something unforeseen has happened ", e);
            } catch (TimeoutException e) {
                logger.error("Thread timed out ", e);
            }
        }
    }

    private Map<String, WMQMetricOverride> getMetricsToReport(String command) {
        Map<String, WMQMetricOverride> commandMetrics = Maps.newHashMap();
        if (metrics == null || metrics.isEmpty()) {
            logger.debug("There are no metrics configured for {}", command);
            return commandMetrics;
        }
        Iterator<String> itr = metrics.keySet().iterator();
        while (itr.hasNext()) {
            String metricKey = itr.next();
            WMQMetricOverride wmqOverride = metrics.get(metricKey);
            if (wmqOverride.getIbmCommand().equalsIgnoreCase(command)) {
                commandMetrics.put(metricKey, wmqOverride);
            }
        }
        return commandMetrics;
    }
}
