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

import static com.splunk.ibm.mq.metricscollector.MetricPropertiesAssert.metricPropertiesMatching;

import com.appdynamics.extensions.metrics.MetricProperties;
import java.util.function.Function;
import org.assertj.core.api.Assertions;

public class MetricAssert {

  private final Metric metric;

  public MetricAssert(Metric metric) {
    this.metric = metric;
  }

  static MetricAssert assertThatMetric(Metric metric) {
    return new MetricAssert(metric);
  }

  MetricAssert hasName(String name) {
    Assertions.assertThat(metric.getMetricName()).isEqualTo(name);
    return this;
  }

  MetricAssert hasValue(String value) {
    Assertions.assertThat(metric.getMetricValue()).isEqualTo(value);
    return this;
  }

  MetricPropertiesAssert withPropertiesMatching() {
    return metricPropertiesMatching(metric.getMetricProperties());
  }

  MetricPropertiesAssert withPropertiesMatching(
      Function<MetricProperties, MetricPropertiesAssert> fn) {
    return fn.apply(metric.getMetricProperties());
  }

  MetricAssert hasPath(String path) {
    Assertions.assertThat(metric.getMetricPath()).isEqualTo(path);
    return this;
  }
}
