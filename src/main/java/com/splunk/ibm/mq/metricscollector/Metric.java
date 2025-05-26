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

import com.appdynamics.extensions.util.AssertUtils;
import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class Metric {
  private String metricName;
  private String metricValue;
  private Attributes attributes;

  public Metric(String metricName, String metricValue, Attributes attributes) {
    this.metricName = metricName;
    this.metricValue = metricValue;
    this.attributes = attributes;
  }

  public Attributes getAttributes() {
    return attributes;
  }

  public Metric(@NotNull String metricName, @NotNull String metricValue) {
    this.metricName = metricName;
    this.metricValue = metricValue;
  }

  public Metric(String metricName, String metricValue, Map<String, ?> metricProperties) {
    this(metricName, metricValue);
    AssertUtils.assertNotNull(metricProperties, "Metric Properties cannot be null");
  }

  public String getMetricName() {
    return metricName;
  }

  public String getMetricValue() {
    return metricValue;
  }

  public void setMetricValue(String metricValue) {
    this.metricValue = metricValue;
  }

  public String toString() {
    return String.format("[%s]=[%s]]", getMetricName(), getMetricValue());
  }
}
