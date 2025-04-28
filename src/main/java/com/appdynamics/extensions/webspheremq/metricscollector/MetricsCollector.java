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
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * MetricsCollector class is abstract and serves as superclass for all types of metric collection class.<br>
 * It contains common methods to extract or transform metric value and names.
 */
public abstract class MetricsCollector implements MetricsPublisher {

	private final Map<String, WMQMetricOverride> metricsToReport;
	protected final MonitorContextConfiguration monitorContextConfig;
	protected final PCFMessageAgent agent;
	protected final MetricWriteHelper metricWriteHelper;
	protected final QueueManager queueManager;
	protected final CountDownLatch countDownLatch;
	private final String artifact;

	public MetricsCollector(Map<String, WMQMetricOverride> metricsToReport,
                            MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent,
                            MetricWriteHelper metricWriteHelper, QueueManager queueManager,
                            CountDownLatch countDownLatch, String artifact) {
		this.metricsToReport = metricsToReport;
		this.monitorContextConfig = monitorContextConfig;
		this.agent = agent;
		this.metricWriteHelper = metricWriteHelper;
		this.queueManager = queueManager;
		this.countDownLatch = countDownLatch;
        this.artifact = artifact;
    }


	final public String getArtifact(){
		return artifact;
	}

	final public Map<String, WMQMetricOverride> getMetricsToReport() {
		return metricsToReport;
	}

	/**
	 * Applies include and exclude filters to the artifacts (i.e queue manager, q, or channel),<br>
	 * extracts and publishes the metrics to controller
	 *
	 * @throws TaskExecutionException
	 */
	public final void process() throws TaskExecutionException {
		publishMetrics();
	}

	public enum FilterType {
		STARTSWITH, EQUALS, ENDSWITH, CONTAINS, NONE
    }

	protected int[] getIntAttributesArray(int... inputAttrs) {
		int[] attrs = new int[inputAttrs.length+getMetricsToReport().size()];
		// fill input attrs
        System.arraycopy(inputAttrs, 0, attrs, 0, inputAttrs.length);
		//fill attrs from metrics to report.
		Iterator<String> overrideItr = getMetricsToReport().keySet().iterator();
		for (int count = inputAttrs.length; overrideItr.hasNext() && count < attrs.length; count++) {
			String metrickey = overrideItr.next();
			WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
			attrs[count] = wmqOverride.getConstantValue();
		}
		return attrs;

	}
}
