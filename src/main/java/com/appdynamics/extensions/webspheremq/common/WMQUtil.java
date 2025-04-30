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

package com.appdynamics.extensions.webspheremq.common;

import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class WMQUtil {
    public static final Logger logger = LoggerFactory.getLogger(WMQUtil.class);
    /**
     * Returns master data structure, this map will contain only those metrics which are configured in the yaml config file.<br>
     * It contains metric type as key and a map of metric and WMQMetricOverride as value,<br>
     *    Map <metric type, Map<metric name, WMQMetricOverride> >
     * EntrySet of internal map implicitly represents metrics to be reported.
     */
    public static Map<String, Map<String, WMQMetricOverride>> getMetricsToReportFromConfigYml(List<Map> mqMetrics) {
        Map<String, Map<String, WMQMetricOverride>> metricsMap = Maps.newHashMap();
        //
        // mqMetrics represents "mqMetrics:" defined in config yaml.
        //    i.e:  - metricsType: "..." \ metrics: \ include: \
        //                           <the metric name>:  alias: "" ibmConstant: "com.ibm.mq.constants.CMQCFC.MQIACF_Q_MGR_STATUS"
        //                           ibmCommand="MQCMD_" aggregationType: "" timeRollUpType: "" clusterRollUpType:"" clusterRollUpType=""
        //
        for (Map mqMetric : mqMetrics) {
            String metricType = (String) mqMetric.get("metricsType");
            List includeMetrics = (List) ((Map) mqMetric.get("metrics")).get("include");
            Map<String, WMQMetricOverride> metricToReport = Maps.newHashMap();
            if (includeMetrics != null) {
                metricToReport = gatherMetricNamesByApplyingIncludeFilter(includeMetrics);
            }
            metricsMap.put(metricType, metricToReport);
        }
        return metricsMap;
    }

    private static Map<String, WMQMetricOverride> gatherMetricNamesByApplyingIncludeFilter(List includeMetrics) {
        Map<String, WMQMetricOverride> overrideMap = Maps.newHashMap();
        for (Object inc : includeMetrics) {
            Map metric = (Map) inc;
            // Get the First Entry which is the metric
            Map.Entry firstEntry = (Map.Entry) metric.entrySet().iterator().next();
            String metricName = firstEntry.getKey().toString();
            Map<String, ?> metricPropsMap = (Map<String, ?>) metric.get(metricName);
            WMQMetricOverride override = new WMQMetricOverride();
            override.setIbmCommand((String) metricPropsMap.get("ibmCommand"));
            override.setIbmConstant((String) metricPropsMap.get("ibmConstant"));
            override.setMetricProperties(metricPropsMap);
            if (override.getConstantValue() == -1) {
                // Only add the metric which is valid, if constant value
                // resolutes to -1 then it is invalid.
                logger.warn("{} is not a valid valid metric, this metric will not be processed", override.getIbmConstant());
            } else {
                overrideMap.put(metricName, override);
            }
            logger.debug("Override Definition: {}", override);
        }
        return overrideMap;
    }

    public static String getQueueManagerNameFromConfig(QueueManager queueManager) {
        if (!Strings.isNullOrEmpty(queueManager.getDisplayName())) {
            return queueManager.getDisplayName();
        } else {
            return queueManager.getName();
        }
    }
}
