package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.metrics.MetricProperties;
import org.assertj.core.api.Assertions;

import java.util.function.Function;

import static com.appdynamics.extensions.webspheremq.metricscollector.MetricPropertiesAssert.metricPropertiesMatching;

public class MetricAssert {

    private final Metric metric;

    public MetricAssert(Metric metric) {
        this.metric = metric;
    }

    static MetricAssert assertThatMetric(Metric metric){
        return new MetricAssert(metric);
    }

    MetricAssert hasName(String name){
        Assertions.assertThat(metric.getMetricName()).isEqualTo(name);
        return this;
    }

    MetricAssert hasValue(String value){
        Assertions.assertThat(metric.getMetricValue()).isEqualTo(value);
        return this;
    }

    MetricPropertiesAssert withPropertiesMatching(){
        return metricPropertiesMatching(metric.getMetricProperties());
    }

    MetricPropertiesAssert withPropertiesMatching(Function<MetricProperties,MetricPropertiesAssert> fn){
        return fn.apply(metric.getMetricProperties());
    }

    MetricAssert hasPath(String path){
        Assertions.assertThat(metric.getMetricPath()).isEqualTo(path);
        return this;
    }


}
