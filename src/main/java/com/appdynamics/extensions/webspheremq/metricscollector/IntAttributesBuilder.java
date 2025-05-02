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
package com.appdynamics.extensions.webspheremq.metricscollector;

import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is responsible for building a list of integer-valued attributes from a predefined
 * collection of MWQMetricOverride metrics. It does this by allocating a new int[] and appending the
 * predefined values to an array of input values.
 */
public final class IntAttributesBuilder {
  private final Collection<WMQMetricOverride> metrics;

  public IntAttributesBuilder(@Nullable Map<String, WMQMetricOverride> metrics) {
    this(metrics == null ? null : metrics.values());
  }

  public IntAttributesBuilder(@Nullable Collection<WMQMetricOverride> metrics) {
    this.metrics = metrics == null ? Collections.emptySet() : metrics;
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
