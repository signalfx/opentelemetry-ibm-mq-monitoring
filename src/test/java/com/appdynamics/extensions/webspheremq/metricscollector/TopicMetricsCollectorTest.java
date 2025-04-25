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

import com.appdynamics.extensions.AMonitorJob;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.PathResolver;
import com.appdynamics.extensions.webspheremq.common.Constants;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TopicMetricsCollectorTest {
    private TopicMetricsCollector classUnderTest;

    @Mock
    private AMonitorJob aMonitorJob;

    @Mock
    private PCFMessageAgent pcfMessageAgent;

    @Mock
    private MetricWriteHelper metricWriteHelper;

    private MonitorContextConfiguration monitorContextConfig;
    private Map<String, WMQMetricOverride> topicMetricsToReport;
    private QueueManager queueManager;
    ArgumentCaptor<List<Metric>> pathCaptor = ArgumentCaptor.forClass(List.class);

    @BeforeEach
    void setup() {
        monitorContextConfig = new MonitorContextConfiguration("WMQMonitor", "Custom Metrics|WMQMonitor|", PathResolver.resolveDirectory(AManagedMonitor.class), aMonitorJob);
        monitorContextConfig.setConfigYml("src/test/resources/conf/config.yml");
        Map<String, ?> configMap = monitorContextConfig.getConfigYml();
        ObjectMapper mapper = new ObjectMapper();
        queueManager = mapper.convertValue(((List)configMap.get("queueManagers")).get(0), QueueManager.class);
        Map<String, Map<String, WMQMetricOverride>> metricsMap = WMQUtil.getMetricsToReportFromConfigYml((List<Map>) configMap.get("mqMetrics"));
        topicMetricsToReport = metricsMap.get(Constants.METRIC_TYPE_TOPIC);
    }

    @Test
    void testpublishMetrics() throws Exception {
        when(pcfMessageAgent.send(any(PCFMessage.class))).thenReturn(createPCFResponseForInquireTopicStatusCmd());
        classUnderTest = new TopicMetricsCollector(topicMetricsToReport, monitorContextConfig, pcfMessageAgent, queueManager, metricWriteHelper, Mockito.mock(CountDownLatch.class));
        classUnderTest.publishMetrics();
        verify(metricWriteHelper, times(2)).transformAndPrintMetrics(pathCaptor.capture());
        List<String> metricPathsList = Lists.newArrayList();
        metricPathsList.add("Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Topics|test|PublishCount");
        metricPathsList.add("Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Topics|dev|SubscriptionCount");
        metricPathsList.add("Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Topics|system|PublishCount");

        List<List<Metric>> allValues = pathCaptor.getAllValues();
        assertThat(allValues).hasSize(2);
        assertThat(allValues.get(0)).hasSize(2);
        assertThat(allValues.get(1)).hasSize(2);

        for (List<Metric> metricList : allValues) {
            /* TODO: Note -- the list could be the right size but the data inside completely borked
               and this poorly written test would still pass. Please fix some day. */
            for (Metric metric : metricList) {
                if (metricPathsList.contains(metric.getMetricPath())) {
                    if (metric.getMetricPath().equals("Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|Topics|test|PublishCount")) {
                        assertThat(metric.getMetricValue()).isEqualTo("2");
                        assertThat(metric.getMetricValue()).isNotEqualTo("10");
                    }
                    if (metric.getMetricPath().equals("Server|Component:Tier1|Custom Metrics|WebsphereMQ|QM1|dev|SubscriptionCount")) {
                        assertThat(metric.getMetricValue()).isEqualTo("4");
                    }
                }
            }
        }
    }

    private PCFMessage[] createPCFResponseForInquireTopicStatusCmd() {
        PCFMessage response1 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, 1, false);
        response1.addParameter(CMQC.MQCA_TOPIC_STRING, "test");
        response1.addParameter(CMQC.MQIA_PUB_COUNT, 2);
        response1.addParameter(CMQC.MQIA_SUB_COUNT, 3);

        PCFMessage response2 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, 2, false);
        response2.addParameter(CMQC.MQCA_TOPIC_STRING, "dev");
        response2.addParameter(CMQC.MQIA_PUB_COUNT, 3);
        response2.addParameter(CMQC.MQIA_SUB_COUNT, 4);

        PCFMessage response3 = new PCFMessage(2, CMQCFC.MQCMD_INQUIRE_TOPIC_STATUS, 3, false);
        response3.addParameter(CMQC.MQCA_TOPIC_STRING, "system");
        response3.addParameter(CMQC.MQIA_PUB_COUNT, 5);
        response3.addParameter(CMQC.MQIA_SUB_COUNT, 6);

        return new PCFMessage[]{response1, response2, response3};
    }


}
