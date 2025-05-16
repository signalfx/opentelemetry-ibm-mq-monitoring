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

import com.appdynamics.extensions.metrics.Metric;
import com.splunk.ibm.mq.common.WMQUtil;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import javax.annotation.Nullable;

public final class MetricCreator {

  private final String metricPrefix;
  private final String queueManagerName;
  @Nullable private final String firstPathComponent;

  public MetricCreator(String metricPrefix, QueueManager queueManager) {
    this(metricPrefix, queueManager.getName(), null);
  }

  public MetricCreator(
      String metricPrefix, QueueManager queueManager, @Nullable String firstPathComponent) {
    this(metricPrefix, queueManager.getName(), firstPathComponent);
  }

  public MetricCreator(
      String metricPrefix, String queueManagerName, @Nullable String firstPathComponent) {
    this.metricPrefix = metricPrefix;
    this.queueManagerName = queueManagerName;
    this.firstPathComponent = firstPathComponent;
  }

  Metric createMetric(
      String metricName, int metricValue, WMQMetricOverride wmqOverride, String... pathElements) {
    String metricPath = getMetricsName(queueManagerName, pathElements);
    if (wmqOverride != null) {
      return new Metric(
          metricName, String.valueOf(metricValue), metricPath, wmqOverride.getMetricProperties());
    }
    return new Metric(metricName, String.valueOf(metricValue), metricPath);
  }

  private String getMetricsName(String qmNameToBeDisplayed, String... pathElements) {
    StringBuilder pathBuilder = new StringBuilder(metricPrefix);
    pathBuilder.append("|").append(qmNameToBeDisplayed).append("|");
    if (firstPathComponent != null) {
      pathBuilder.append(firstPathComponent).append("|");
    }
    for (int i = 0; i < pathElements.length; i++) {
      pathBuilder.append(pathElements[i]);
      if (i != pathElements.length - 1) {
        pathBuilder.append("|");
      }
    }
    return pathBuilder.toString();
  }
}
