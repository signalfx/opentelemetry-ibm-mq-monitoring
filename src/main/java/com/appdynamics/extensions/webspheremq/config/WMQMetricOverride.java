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
package com.appdynamics.extensions.webspheremq.config;

import static java.util.Collections.unmodifiableMap;

import com.sun.istack.NotNull;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an override for WebSphere MQ metrics with custom properties. This class provides
 * functionality to specify and manage IBM constants, commands, and associated metric properties for
 * WebSphere MQ configuration.
 */
@Immutable
public final class WMQMetricOverride {

  public static final Logger logger = LoggerFactory.getLogger(WMQMetricOverride.class);
  private static final int INVALID_CONSTANT = -1;

  private final String ibmConstant;
  private final String ibmCommand;
  private final int constantValue;
  private final Map<String, ?> metricProperties;

  private WMQMetricOverride(Builder builder, int constantValue) {
    this.ibmCommand = builder.ibmCommand;
    this.ibmConstant = builder.ibmConstant;
    this.constantValue = constantValue;
    this.metricProperties = new HashMap<>(builder.metricProperties);
  }

  public String getIbmConstant() {
    return ibmConstant;
  }

  public String getIbmCommand() {
    return ibmCommand;
  }

  public int getConstantValue() {
    return constantValue;
  }

  public boolean hasInvalidConstant() {
    return constantValue == INVALID_CONSTANT;
  }

  @NotNull
  public Map<String, ?> getMetricProperties() {
    return unmodifiableMap(metricProperties);
  }

  private static int computeConstantValue(String ibmConstant) {
    int lastPacSeparatorDotIdx = ibmConstant.lastIndexOf('.');
    if (lastPacSeparatorDotIdx == -1) {
      return INVALID_CONSTANT;
    }
    String declaredField = ibmConstant.substring(lastPacSeparatorDotIdx + 1);
    String classStr = ibmConstant.substring(0, lastPacSeparatorDotIdx);
    try {
      Class<?> clazz = Class.forName(classStr);
      return (Integer) clazz.getDeclaredField(declaredField).get(Integer.class);
    } catch (Exception e) {
      logger.warn(e.getMessage());
      logger.warn(
          "ibmConstant {} is not a valid constant defaulting constant value to -1", ibmConstant);
    }
    return INVALID_CONSTANT;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String ibmConstant;
    private String ibmCommand;
    private Map<String, ?> metricProperties = new HashMap<>();

    public Builder ibmConstant(String ibmConstant) {
      this.ibmConstant = ibmConstant;
      return this;
    }

    public Builder ibmCommand(String ibmCommand) {
      this.ibmCommand = ibmCommand;
      return this;
    }

    public Builder metricProperties(Map<String, ?> metricProperties) {
      this.metricProperties = new HashMap<>(metricProperties);
      return this;
    }

    public WMQMetricOverride build() {
      int constantValue = computeConstantValue(ibmConstant);
      return new WMQMetricOverride(this, constantValue);
    }
  }

  @Override
  public String toString() {
    return "["
        + "IbmConstant="
        + getIbmConstant()
        + ","
        + "IbmCommand="
        + getIbmCommand()
        + ","
        + "ConstantVal="
        + getConstantValue()
        + ","
        + "]";
  }
}
