package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;

import java.util.Iterator;
import java.util.Map;

final class IntAttributesBuilder {
    private final Map<String, WMQMetricOverride> metrics;

    IntAttributesBuilder(Map<String, WMQMetricOverride> metrics) {
        this.metrics = metrics;
    }

    int[] buildIntAttributesArray(int... inputAttrs) {
        int[] attrs = new int[inputAttrs.length + metrics.size()];
        // copy input attrs
        System.arraycopy(inputAttrs, 0, attrs, 0, inputAttrs.length);

        // fill attrs from metrics.
        int i = inputAttrs.length;
        for (WMQMetricOverride metric : metrics.values()) {
            attrs[i++] = metric.getConstantValue();
        }

        return attrs;
    }
}
