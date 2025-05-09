/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.splunk.ibm.mq.metricscollector;

import static org.assertj.core.api.Assertions.assertThat;

import com.appdynamics.extensions.metrics.MetricProperties;
import java.math.BigDecimal;
import java.util.function.Function;

public class MetricPropertiesAssert {

  private final MetricProperties metricProperties;

  public MetricPropertiesAssert(MetricProperties metricProperties) {
    this.metricProperties = metricProperties;
  }

  static MetricPropertiesAssert metricPropertiesMatching(MetricProperties props) {
    return new MetricPropertiesAssert(props);
  }

  MetricPropertiesAssert alias(String alias) {
    assertThat(metricProperties.getAlias()).isEqualTo(alias);
    return this;
  }

  MetricPropertiesAssert multiplier(BigDecimal multiplier) {
    assertThat(metricProperties.getMultiplier()).isEqualTo(multiplier);
    return this;
  }

  MetricPropertiesAssert aggregationType(String aggregationType) {
    assertThat(metricProperties.getAggregationType()).isEqualTo(aggregationType);
    return this;
  }

  MetricPropertiesAssert timeRollup(String rollup) {
    assertThat(metricProperties.getTimeRollUpType()).isEqualTo(rollup);
    return this;
  }

  MetricPropertiesAssert clusterRollUp(String clusterRollUp) {
    assertThat(metricProperties.getClusterRollUpType()).isEqualTo(clusterRollUp);
    return this;
  }

  MetricPropertiesAssert delta(boolean delta) {
    assertThat(metricProperties.getDelta()).isEqualTo(delta);
    return this;
  }

  public static Function<MetricProperties, MetricPropertiesAssert> standardPropsForAlias(
      String alias) {
    return mp ->
        metricPropertiesMatching(mp)
            .alias(alias)
            .multiplier(BigDecimal.ONE)
            .aggregationType("AVERAGE")
            .timeRollup("AVERAGE")
            .clusterRollUp("INDIVIDUAL")
            .delta(false);
  }
}
