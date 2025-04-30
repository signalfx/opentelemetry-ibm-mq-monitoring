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

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.ibm.mq.headers.pcf.PCFMessageAgent;

import java.util.concurrent.CountDownLatch;

/**
 * MetricsCollector class is abstract and serves as superclass for all types of metric collection class.<br>
 * It contains common methods to extract or transform metric value and names.
 */
public abstract class MetricsCollector implements MetricsPublisher {

	protected final MonitorContextConfiguration monitorContextConfig;
	protected final PCFMessageAgent agent;
	protected final MetricWriteHelper metricWriteHelper;
	protected final QueueManager queueManager;
	protected final CountDownLatch countDownLatch;

	public MetricsCollector(MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent,
							MetricWriteHelper metricWriteHelper, QueueManager queueManager,
							CountDownLatch countDownLatch) {
		this.monitorContextConfig = monitorContextConfig;
		this.agent = agent;
		this.metricWriteHelper = metricWriteHelper;
		this.queueManager = queueManager;
		this.countDownLatch = countDownLatch;
    }

	public enum FilterType {
		STARTSWITH, EQUALS, ENDSWITH, CONTAINS, NONE
    }
}
