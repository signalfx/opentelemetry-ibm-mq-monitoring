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
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.collect.Lists;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.MQCFIL;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This class is responsible for queue metric collection.
 */
final public class InquireQueueManagerCmdCollector extends MetricsCollector {

	private static final Logger logger = ExtensionsLoggerFactory.getLogger(InquireQueueManagerCmdCollector.class);
	public final static String ARTIFACT = "Queue Manager";
	private final MetricCreator metricCreator;
	private final Map<String, WMQMetricOverride> metrics;

	public InquireQueueManagerCmdCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper, CountDownLatch countDownLatch, MetricCreator metricCreator) {
		super(monitorContextConfig, agent, metricWriteHelper, queueManager, countDownLatch);
        this.metricCreator = metricCreator;
		this.metrics = metricsToReport;
    }

	@Override
	public void publishMetrics() throws TaskExecutionException {
		long entryTime = System.currentTimeMillis();
		logger.debug("publishMetrics entry time for queuemanager {} is {} milliseconds", agent.getQManagerName(), entryTime);
		PCFMessage request;
		PCFMessage[] responses;
		// CMQCFC.MQCMD_INQUIRE_Q_MGR is 2
		request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_MGR);
		//request.addParameter(CMQC.MQCA_Q_MGR_NAME, "*");
		// CMQCFC.MQIACF_Q_MGR_STATUS_ATTRS is 1001
		request.addParameter(new MQCFIL(MQConstants.MQIACF_Q_MGR_ATTRS, new int[] {MQConstants.MQIACF_ALL}));
		try {
			// Note that agent.send() method is synchronized
			logger.debug("sending PCF agent request to query queuemanager {}", agent.getQManagerName());
			long startTime = System.currentTimeMillis();
			responses = agent.send(request);
			long endTime = System.currentTimeMillis() - startTime;
			logger.debug("PCF agent queuemanager metrics query response for {} received in {} milliseconds", agent.getQManagerName(), endTime);
			if (responses == null || responses.length <= 0) {
				logger.debug("Unexpected Error while PCFMessage.send(), response is either null or empty");
				return;
			}
			Iterator<String> overrideItr = metrics.keySet().iterator();
			List<Metric> responseMetrics = Lists.newArrayList();
			while (overrideItr.hasNext()) {
				String metrickey = overrideItr.next();
				WMQMetricOverride wmqOverride = metrics.get(metrickey);
				int metricVal = responses[0].getIntParameterValue(wmqOverride.getConstantValue());
				if (logger.isDebugEnabled()) {
					logger.debug("Metric: " + metrickey + "=" + metricVal);
				}
				Metric metric = metricCreator.createMetric(metrickey, metricVal, wmqOverride, metrickey);
				responseMetrics.add(metric);
			}
			metricWriteHelper.transformAndPrintMetrics(responseMetrics);
		} catch (Exception e) {
			logger.error(e.getMessage());
			throw new TaskExecutionException(e);
		} finally {
			long exitTime = System.currentTimeMillis() - entryTime;
			logger.debug("Time taken to publish metrics for queuemanager is {} milliseconds", exitTime);
		}
	}
}
