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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;

/**
 * A temporary bundle to contain the collaborators of the original MetricsCollector
 * base class until we can finish unwinding things. When done and there are no
 * longer usages of MetricsCollector, we could consider renaming this.
 */
@Immutable
public final class MetricsCollectorContext {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectorContext.class);

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

    Set<String> getQueueIncludeFilterNames() {
        return queueManager.getQueueFilters().getInclude();
    }

    Set<ExcludeFilters> getQueueExcludeFilters() {
        return queueManager.getQueueFilters().getExclude();
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

    MetricWriteHelper getMetricWriteHelper() {
        return metricWriteHelper;
    }

    String getAgentQueueManagerName() {
        return agent.getQManagerName();
    }

    Map<String, WMQMetricOverride> getMetricsForCommand(String command) {
        if (metricsToReport == null || metricsToReport.isEmpty()) {
            logger.debug("There are no metrics configured for {}", command);
            return emptyMap();
        }
        return metricsToReport.entrySet().stream()
                .filter(entry ->
                        entry.getValue().getIbmCommand().equalsIgnoreCase(command))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (x, y) -> y, HashMap::new));
    }

    interface MetricConsumerAction {
        void accept(String key, WMQMetricOverride wmqMetricOverride) throws PCFException;

    }
}
