package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import javax.annotation.Nullable;

public final class MetricCreator {

    private final String metricPrefix;
    private final String queueManagerName;
    @Nullable
    private final String firstPathComponent;

    public MetricCreator(String metricPrefix, QueueManager queueManager) {
        this(metricPrefix, WMQUtil.getQueueManagerNameFromConfig(queueManager), null);
    }

    public MetricCreator(String metricPrefix, QueueManager queueManager, @Nullable String firstPathComponent) {
        this(metricPrefix, WMQUtil.getQueueManagerNameFromConfig(queueManager), firstPathComponent);
    }

    public MetricCreator(String metricPrefix, String queueManagerName, @Nullable String firstPathComponent) {
        this.metricPrefix = metricPrefix;
        this.queueManagerName = queueManagerName;
        this.firstPathComponent = firstPathComponent;
    }

    Metric createMetric(String metricName, int metricValue, WMQMetricOverride wmqOverride, String... pathElements) {
        String metricPath = getMetricsName(queueManagerName, pathElements);
        if (wmqOverride != null && wmqOverride.getMetricProperties() != null) {
            return new Metric(metricName, String.valueOf(metricValue), metricPath, wmqOverride.getMetricProperties());
        }
        return new Metric(metricName, String.valueOf(metricValue), metricPath);
    }

    private String getMetricsName(String qmNameToBeDisplayed, String... pathElements) {
        StringBuilder pathBuilder = new StringBuilder(metricPrefix);
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
