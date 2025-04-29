package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * This class is responsible for building a list of integer-valued attributes
 * from a predefined collection of MWQMetricOverride metrics. It does this
 * by allocating a new int[] and appending the predefined values to
 * an array of input values.
 */
final class IntAttributesBuilder {
    private final Collection<WMQMetricOverride> metrics;

    IntAttributesBuilder(Map<String, WMQMetricOverride> metrics) {
        this(metrics.values());
    }

    IntAttributesBuilder(Collection<WMQMetricOverride> metrics) {
        this.metrics = metrics;
    }

    int[] buildIntAttributesArray(int... inputAttrs) {
        int[] attrs = new int[inputAttrs.length + metrics.size()];
        // copy input attrs
        System.arraycopy(inputAttrs, 0, attrs, 0, inputAttrs.length);

        // fill attrs from metrics.
        int i = inputAttrs.length;
        for (WMQMetricOverride metric : metrics) {
            attrs[i++] = metric.getConstantValue();
        }

        return attrs;
    }
}
