package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

/**
 * JobSubmitterContext is a bundle class used by MetricsPublisher instances in order
 * to bundle together collaborators and to encapsulate some common repeated
 * functionality.
 */
final public class JobSubmitterContext {

    private final MonitorContextConfiguration monitorContextConfig;
    private final CountDownLatch countDownLatch;
    private final MetricsCollectorContext collectorContext;

    public JobSubmitterContext(MonitorContextConfiguration monitorContextConfig, CountDownLatch countDownLatch, MetricsCollectorContext collectorContext) {
        this.monitorContextConfig = monitorContextConfig;
        this.countDownLatch = countDownLatch;
        this.collectorContext = collectorContext;
    }

    String getMetricPrefix() {
        return monitorContextConfig.getMetricPrefix();
    }

    Future<?> submitPublishJob(String name, MetricsPublisher publisher) {
        MetricsPublisherJob job = new MetricsPublisherJob(publisher, countDownLatch);
        return monitorContextConfig.getContext()
                .getExecutorService()
                .submit(name, job);
    }

    int getConfigInt(String key, int defaultValue) {
        Object result = monitorContextConfig.getConfigYml().get(key);
        if(result == null){
            return defaultValue;
        }
        return (Integer) result;
    }

    MetricCreator newMetricCreator(String firstPathComponent) {
        return new MetricCreator(getMetricPrefix(), collectorContext.getQueueManagerName(), firstPathComponent);
    }

    MetricsCollectorContext newCollectorContext(Map<String, WMQMetricOverride> newMetrics) {
        IntAttributesBuilder attributesBuilder = new IntAttributesBuilder(newMetrics);
        return new MetricsCollectorContext(newMetrics, attributesBuilder,
                collectorContext.getQueueManager(),
                collectorContext.getAgent(),
                collectorContext.getMetricWriteHelper());
    }

    Map<String, WMQMetricOverride> getMetricsForCommand(String command){
        return collectorContext.getMetricsForCommand(command);
    }
}
