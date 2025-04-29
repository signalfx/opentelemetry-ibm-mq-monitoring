package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.annotation.Nullable;

public final class MetricCreator {

    private final MonitorContextConfiguration monitorContextConfig;
    private final QueueManager queueManager;
    @Nullable
    private final String firstPathComponent;

    public MetricCreator(MonitorContextConfiguration monitorContextConfig, QueueManager queueManager) {
        this(monitorContextConfig, queueManager, null);
    }

    public MetricCreator(MonitorContextConfiguration monitorContextConfig, QueueManager queueManager, @Nullable String firstPathComponent) {
        this.monitorContextConfig = monitorContextConfig;
        this.queueManager = queueManager;
        this.firstPathComponent = firstPathComponent;
    }

    Metric createMetric(String metricName, int metricValue, WMQMetricOverride wmqOverride, String... pathElements) {
        String queueManagerName = WMQUtil.getQueueManagerNameFromConfig(queueManager);
        String metricPath = getMetricsName(queueManagerName, pathElements);
        if (wmqOverride != null && wmqOverride.getMetricProperties() != null) {
            return new Metric(metricName, String.valueOf(metricValue), metricPath, wmqOverride.getMetricProperties());
        }
        return new Metric(metricName, String.valueOf(metricValue), metricPath);
    }

    private String getMetricsName(String qmNameToBeDisplayed, String... pathElements) {
        StringBuilder pathBuilder = new StringBuilder(monitorContextConfig.getMetricPrefix());
        pathBuilder.append("|")
                .append(qmNameToBeDisplayed)
                .append("|");
        if(firstPathComponent != null){
            pathBuilder.append(firstPathComponent)
                    .append("|");
        }
        for (int i = 0; i < pathElements.length; i++) {
            pathBuilder.append(pathElements[i]);
            if (i != pathElements.length - 1) {
                pathBuilder.append("|");
            }
        }
        return pathBuilder.toString();
    }
}
