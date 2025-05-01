package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.ExcludeFilters;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

/**
 * A temporary bundle to contain the collaborators of the original MetricsCollector
 * base class until we can finish unwinding things. When done and there are no
 * longer usages of MetricsCollector, we could consider renaming this.
 */
@Immutable
public final class MetricsCollectorContext {

    private final Map<String, WMQMetricOverride> metricsToReport;
    private final IntAttributesBuilder attributesBuilder;
    private final QueueManager queueManager;
    private final PCFMessageAgent agent;
    private final MetricWriteHelper metricWriteHelper;

    public MetricsCollectorContext(@Nullable Map<String, WMQMetricOverride> metricsToReport,
                                   QueueManager queueManager, PCFMessageAgent agent, MetricWriteHelper metricWriteHelper) {
        this(metricsToReport, new IntAttributesBuilder(metricsToReport),
                queueManager, agent, metricWriteHelper);
    }

    public MetricsCollectorContext(@Nullable Map<String, WMQMetricOverride> metricsToReport,
                                   IntAttributesBuilder attributesBuilder, QueueManager queueManager,
                                   PCFMessageAgent agent, MetricWriteHelper metricWriteHelper) {
        this.metricsToReport = metricsToReport == null ? Collections.emptyMap() : new HashMap<>(metricsToReport);
        this.attributesBuilder = attributesBuilder;
        this.queueManager = queueManager;
        this.agent = agent;
        this.metricWriteHelper = metricWriteHelper;
    }

    boolean hasNoMetricsToReport() {
        return metricsToReport.isEmpty();
    }

    int[] buildIntAttributesArray(int... inputAttrs) {
        return attributesBuilder.buildIntAttributesArray(inputAttrs);
    }

    Set<String> getChannelIncludeFilterNames() {
        return queueManager.getChannelFilters().getInclude();
    }

    Set<ExcludeFilters> getChannelExcludeFilters() {
        return queueManager.getChannelFilters().getExclude();
    }

    Set<String> getListenerIncludeFilterNames() {
        return queueManager.getListenerFilters().getInclude();
    }

    Set<ExcludeFilters> getListenerExcludeFilters() {
        return queueManager.getListenerFilters().getExclude();
    }

    Set<String> getTopicIncludeFilterNames() {
        return queueManager.getTopicFilters().getInclude();
    }

    Set<ExcludeFilters> getTopicExcludeFilters() {
        return queueManager.getTopicFilters().getExclude();
    }

    PCFMessage[] send(PCFMessage request) throws IOException, MQDataException {
        return agent.send(request);
    }

    void forEachMetric(MetricConsumerAction action) throws PCFException {
        if (metricsToReport == null) {
            return;
        }
        for (Map.Entry<String, WMQMetricOverride> entry : metricsToReport.entrySet()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    void transformAndPrintMetric(Metric responseMetrics) {
        transformAndPrintMetrics(Collections.singletonList(responseMetrics));
    }

    void transformAndPrintMetrics(List<Metric> responseMetrics) {
        metricWriteHelper.transformAndPrintMetrics(responseMetrics);
    }

    String getQueueManagerName() {
        return WMQUtil.getQueueManagerNameFromConfig(queueManager);
    }

    QueueManager getQueueManager() {
        return queueManager;
    }

    PCFMessageAgent getAgent() {
        return agent;
    }

    public MetricWriteHelper getMetricWriteHelper() {
        return metricWriteHelper;
    }

    public String getAgentQueueManagerName() {
        return agent.getQManagerName();
    }

    interface MetricConsumerAction {
        void accept(String key, WMQMetricOverride wmqMetricOverride) throws PCFException;
    }
}
