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

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Represents an override for WebSphere MQ metrics with custom properties.
 * This class provides functionality to specify and manage IBM constants,
 * commands, and associated metric properties for WebSphere MQ configuration.
 */
public class WMQMetricOverride {

	String ibmConstant;
	String ibmCommand;
	int constantValue = -1;
	Map<String, ?> metricProperties;

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(WMQMetricOverride.class);

	public String getIbmConstant() {
		return ibmConstant;
	}

	public void setIbmConstant(String ibmConstant) {
		this.ibmConstant = ibmConstant;
	}

	public String getIbmCommand() {
		return ibmCommand;
	}

	public Map<String, ?> getMetricProperties() {
		return metricProperties;
	}

	public void setMetricProperties(Map<String, ?> metricProperties) {
		this.metricProperties = metricProperties;
	}

	public void setIbmCommand(String ibmCommand) {
		this.ibmCommand = ibmCommand;
	}

	public int getConstantValue() {
		if (constantValue == -1) {
			int lastPacSeparatorDotIdx = getIbmConstant().lastIndexOf('.');
			if (lastPacSeparatorDotIdx != -1) {
				String declaredField = getIbmConstant().substring(lastPacSeparatorDotIdx + 1);
				String classStr = getIbmConstant().substring(0, lastPacSeparatorDotIdx);
				Class clazz;
				try {
					clazz = Class.forName(classStr);
					constantValue = (Integer) clazz.getDeclaredField(declaredField).get(Integer.class);
				} catch (Exception e) {
					logger.warn(e.getMessage());
					logger.warn("ibmConstant {} is not a valid constant defaulting constant value to -1", getIbmConstant());
				}
			}
		}
		return constantValue;
	}

	public void setConstantValue(int constantValue) {
		this.constantValue = constantValue;
	}

    @Override
    public String toString() {
        return "[" +
                "IbmConstant=" + getIbmConstant() + "," +
                "IbmCommand=" + getIbmCommand() + "," +
                "ConstantVal=" + getConstantValue() + "," +
                "]";
    }

}
