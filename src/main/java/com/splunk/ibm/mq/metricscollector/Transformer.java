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

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.DeltaMetricsCalculator;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.extensions.util.NumberUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Transformer {
  private static Logger logger = LoggerFactory.getLogger(Transformer.class);
  private List<Metric> metricList;
  private DeltaTranform deltaTranform = new DeltaTranform();
  private MultiplierTransform multiplierTransform = new MultiplierTransform();
  private ConvertTransform convertTransform = new ConvertTransform();
  private AliasTransform aliasTransform = new AliasTransform();

  public Transformer(List<Metric> metricList) {
    AssertUtils.assertNotNull(metricList, "Metrics List cannot be null");
    this.metricList = metricList;
  }

  public void transform() {
    for (Metric metric : metricList) {
      applyTransforms(metric);
    }
  }

  private void applyTransforms(Metric metric) {
    aliasTransform.applyAlias(metric);
    if (metric.getMetricValue() != null) {
      convertTransform.convert(metric);
    }
    if (metric.getMetricValue() != null) {
      deltaTranform.applyDelta(metric);
    }
    if (metric.getMetricValue() != null) {
      multiplierTransform.multiply(metric);
    }
    if (metric.getMetricValue() != null) {
      try {
        BigDecimal value = new BigDecimal(metric.getMetricValue());
        metric.setMetricValue(value.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString());
      } catch (NumberFormatException e) {
        logger.debug(e.toString());
      }
    }
  }
}

class AliasTransform {
  Splitter PIPE_SPLITTER = Splitter.on('|').trimResults();

  void applyAlias(Metric metric) {
    String metricName = metric.getMetricName();
    String alias = metric.getMetricProperties().getAlias();
    String metricPath = metric.getMetricPath();
    List<String> splitList = new ArrayList<>(PIPE_SPLITTER.splitToList(metricPath));
    if (splitList.size() > 0) {
      String metricNameFromSplit = splitList.get(splitList.size() - 1);
      if (metricNameFromSplit.equals(metricName)) {
        splitList.remove(splitList.size() - 1);
        splitList.add(alias);
        metric.setMetricPath(Joiner.on("|").join(splitList));
      }
    }
  }
}

class ConvertTransform {
  private static final org.slf4j.Logger logger =
      ExtensionsLoggerFactory.getLogger(ConvertTransform.class);

  void convert(Metric metric) {
    Map<Object, Object> convertMap = metric.getMetricProperties().getConversionValues();
    String metricValue = metric.getMetricValue();
    if (convertMap != null && !convertMap.isEmpty() && convertMap.containsKey(metricValue)) {
      metric.setMetricValue(convertMap.get(metricValue).toString());
      logger.debug(
          "Applied conversion on {} and replaced value {} with {}",
          metric.getMetricPath(),
          metricValue,
          metric.getMetricValue());
    }
  }
}

class DeltaTranform {
  private static final org.slf4j.Logger logger =
      ExtensionsLoggerFactory.getLogger(DeltaTranform.class);
  private static DeltaMetricsCalculator deltaCalculator = new DeltaMetricsCalculator(10);

  void applyDelta(Metric metric) {
    String metricValue = metric.getMetricValue();
    if (NumberUtils.isNumber(metricValue) && metric.getMetricProperties().getDelta() == true) {
      BigDecimal deltaValue =
          deltaCalculator.calculateDelta(metric.getMetricPath(), new BigDecimal(metricValue));
      if (deltaValue != null) {
        metric.setMetricValue(deltaValue.toString());
      } else {
        metric.setMetricValue(null);
      }
    }
  }
}

class MultiplierTransform {

  void multiply(Metric metric) {
    String metricValue = metric.getMetricValue();
    if (NumberUtils.isNumber(metricValue)) {
      BigDecimal metricValueBigD = new BigDecimal(metricValue);
      metric.setMetricValue(
          (metricValueBigD.multiply(metric.getMetricProperties().getMultiplier())).toString());
    }
  }
}
