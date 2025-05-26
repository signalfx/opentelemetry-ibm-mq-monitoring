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
package com.splunk.ibm.mq.opentelemetry;

import com.splunk.ibm.mq.metricscollector.Metric;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MQOtelTranslator {

  public static final Logger logger = LoggerFactory.getLogger(MQOtelTranslator.class);

  private static final long SCRAPE_PERIOD_NS = 60L * 1000 * 1000 * 1000;

  public MQOtelTranslator() {}

  public Collection<MetricData> translate(List<Metric> metricList) {
    if (metricList == null || metricList.isEmpty()) {
      throw new IllegalArgumentException("Metrics List cannot be null");
    }
    Resource res = Resource.empty();
    InstrumentationScopeInfo scopeInfo = InstrumentationScopeInfo.create("websphere/mq");
    List<MetricData> metrics = new ArrayList<>();
    for (Metric metric : metricList) {
      Instant now = Instant.now();
      long endTime = now.getEpochSecond() * 1_000_000_000 + now.getNano();
      long startingTime = endTime - SCRAPE_PERIOD_NS;

      LongPointData pointData =
          ImmutableLongPointData.create(
              startingTime,
              endTime,
              metric.getAttributes(),
              Long.parseLong(metric.getMetricValue()));
      MetricData otelMetricData =
          ImmutableMetricData.createLongGauge(
              res,
              scopeInfo,
              metric.getMetricName(),
              "",
              "1",
              ImmutableGaugeData.create(Collections.singleton(pointData)));

      metrics.add(otelMetricData);
    }
    return metrics;
  }
}
