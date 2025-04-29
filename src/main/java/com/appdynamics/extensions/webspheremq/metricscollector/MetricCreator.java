package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;

public final class MetricCreator {

    private final MonitorContextConfiguration monitorContextConfig;
    private final QueueManager queueManager;

    public MetricCreator(MonitorContextConfiguration monitorContextConfig, QueueManager queueManager) {
        this.monitorContextConfig = monitorContextConfig;
        this.queueManager = queueManager;
    }

    Metric createMetric(String metricName, int metricValue, WMQMetricOverride wmqOverride, String... pathelements) {
        String queueManagerName = WMQUtil.getQueueManagerNameFromConfig(queueManager);
        String metricPath = getMetricsName(queueManagerName, pathelements);
        if (wmqOverride != null && wmqOverride.getMetricProperties() != null) {
            return new Metric(metricName, String.valueOf(metricValue), metricPath, wmqOverride.getMetricProperties());
        }
        return new Metric(metricName, String.valueOf(metricValue), metricPath);
    }

    private String getMetricsName(String qmNameToBeDisplayed, String... pathelements) {
        StringBuilder pathBuilder = new StringBuilder(monitorContextConfig.getMetricPrefix());
        pathBuilder.append("|")
                .append(qmNameToBeDisplayed)
                .append("|");
        for (int i = 0; i < pathelements.length; i++) {
            pathBuilder.append(pathelements[i]);
            if (i != pathelements.length - 1) {
                pathBuilder.append("|");
            }
        }
        return pathBuilder.toString();
    }
}
