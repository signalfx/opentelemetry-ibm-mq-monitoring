package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.metrics.MetricProperties;

import java.math.BigDecimal;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricPropertiesAssert {

    private final MetricProperties metricProperties;

    public MetricPropertiesAssert(MetricProperties metricProperties) {
        this.metricProperties = metricProperties;
    }

    static MetricPropertiesAssert metricPropertiesMatching(MetricProperties props){
        return new MetricPropertiesAssert(props);
    }

    MetricPropertiesAssert alias(String alias){
        assertThat(metricProperties.getAlias()).isEqualTo(alias);
        return this;
    }
    MetricPropertiesAssert multiplier(BigDecimal multiplier){
        assertThat(metricProperties.getMultiplier()).isEqualTo(multiplier);
        return this;
    }
    MetricPropertiesAssert aggregationType(String aggregationType){
        assertThat(metricProperties.getAggregationType()).isEqualTo(aggregationType);
        return this;
    }
    MetricPropertiesAssert timeRollup(String rollup){
        assertThat(metricProperties.getTimeRollUpType()).isEqualTo(rollup);
        return this;
    }
    MetricPropertiesAssert clusterRollUp(String clusterRollUp){
        assertThat(metricProperties.getClusterRollUpType()).isEqualTo(clusterRollUp);
        return this;
    }
    MetricPropertiesAssert delta(boolean delta){
        assertThat(metricProperties.getDelta()).isEqualTo(delta);
        return this;
    }

    public static Function<MetricProperties, MetricPropertiesAssert> standardPropsForAlias(String alias) {
        return mp -> metricPropertiesMatching(mp)
                .alias(alias)
                .multiplier(BigDecimal.ONE)
                .aggregationType("AVERAGE").timeRollup("AVERAGE").clusterRollUp("INDIVIDUAL")
                .delta(false);
    }

}
