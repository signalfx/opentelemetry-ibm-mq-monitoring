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
import com.appdynamics.extensions.webspheremq.config.ExcludeFilters;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.*;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static com.ibm.mq.constants.CMQC.MQQT_ALIAS;
import static com.ibm.mq.constants.CMQC.MQQT_CLUSTER;
import static com.ibm.mq.constants.CMQC.MQQT_LOCAL;
import static com.ibm.mq.constants.CMQC.MQQT_MODEL;
import static com.ibm.mq.constants.CMQC.MQQT_REMOTE;

final public class QueueMetricsCollector implements MetricsPublisher {

	private static final Logger logger = LoggerFactory.getLogger(QueueMetricsCollector.class);
	public final static String ARTIFACT = "Queues";

	// hack to share state of queue type between collectors.
	// The queue information is only available as response of some commands.
	private final QueueCollectorSharedState sharedState;
	private final MetricCreator metricCreator;
	private final Map<String, WMQMetricOverride> metrics;
    private final JobSubmitterContext context;

    public QueueMetricsCollector(Map<String, WMQMetricOverride> metricsToReport,
                                 QueueCollectorSharedState sharedState,
								 JobSubmitterContext context) {
		this.sharedState = sharedState;
		this.metrics = metricsToReport;
        this.context = context;
		this.metricCreator = context.newMetricCreator(ARTIFACT);
    }

	@Override
	public void publishMetrics() throws TaskExecutionException {
		logger.info("Collecting queue metrics...");
		List<Future> futures = Lists.newArrayList();
		Map<String, WMQMetricOverride>  metricsForInquireQCmd = context.getMetricsForCommand(InquireQCmdCollector.COMMAND);
		if(!metricsForInquireQCmd.isEmpty()){
			MetricsCollectorContext collectorContext = context.newCollectorContext(metricsForInquireQCmd);
			QueueCollectionBuddy queueBuddy = new QueueCollectionBuddy(collectorContext, sharedState, metricCreator, InquireQCmdCollector.COMMAND);
			MetricsPublisher publisher = new InquireQCmdCollector(collectorContext, queueBuddy);
			futures.add(context.submitPublishJob("InquireQCmdCollector", publisher));
		}
		Map<String, WMQMetricOverride>  metricsForInquireQStatusCmd = context.getMetricsForCommand(InquireQStatusCmdCollector.COMMAND);
		if(!metricsForInquireQStatusCmd.isEmpty()){
			MetricsCollectorContext collectorContext = context.newCollectorContext(metricsForInquireQStatusCmd);
			QueueCollectionBuddy queueBuddy = new QueueCollectionBuddy(collectorContext, sharedState, metricCreator, InquireQStatusCmdCollector.COMMAND);
			MetricsPublisher publisher = new InquireQStatusCmdCollector(collectorContext, queueBuddy);
			futures.add(context.submitPublishJob("InquireQStatusCmdCollector", publisher));
		}
		Map<String, WMQMetricOverride>  metricsForResetQStatsCmd = context.getMetricsForCommand(ResetQStatsCmdCollector.COMMAND);
		if(!metricsForResetQStatsCmd.isEmpty()){
			MetricsCollectorContext collectorContext = context.newCollectorContext(metricsForResetQStatsCmd);
			QueueCollectionBuddy queueBuddy = new QueueCollectionBuddy(collectorContext, sharedState, metricCreator, ResetQStatsCmdCollector.COMMAND);
			MetricsPublisher collector = new ResetQStatsCmdCollector(collectorContext, queueBuddy);
			futures.add(context.submitPublishJob("ResetQStatsCmdCollector", collector));
		}
		for(Future<?> future: futures){
			try {
				int timeout = context.getConfigInt("queueMetricsCollectionTimeoutInSeconds", 20);
				future.get(timeout, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.error("The thread was interrupted ",e);
			} catch (ExecutionException e) {
				logger.error("Something unforeseen has happened ",e);
			} catch (TimeoutException e) {
				logger.error("Thread timed out ",e);
			}
		}
	}

	protected void processPCFRequestAndPublishQMetrics(MetricsCollectorContext context, String queueGenericName, PCFMessage request, String command) throws IOException, MQDataException {
		logger.debug("sending PCF agent request to query metrics for generic queue {} for command {}",queueGenericName,command);
		long startTime = System.currentTimeMillis();
		PCFMessage[] response = context.send(request);
		long endTime = System.currentTimeMillis() - startTime;
		logger.debug("PCF agent queue metrics query response for generic queue {} for command {} received in {} milliseconds", queueGenericName, command,endTime);
		if (response == null || response.length <= 0) {
			logger.debug("Unexpected Error while PCFMessage.send() for command {}, response is either null or empty",command);
			return;
		}
		for (int i = 0; i < response.length; i++) {
			String queueName = response[i].getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
			String queueType;
			if (response[i].getParameterValue(CMQC.MQIA_Q_TYPE) == null) {
				queueType = sharedState.getType(queueName);
				if (queueType == null) {
					continue;
				}
			} else {
				switch(response[i].getIntParameterValue(CMQC.MQIA_Q_TYPE)) {
					case MQQT_LOCAL:
						queueType = "local";
						break;
					case MQQT_ALIAS:
						queueType = "alias";
						break;
					case MQQT_REMOTE:
						queueType = "remote";
						break;
					case MQQT_CLUSTER:
						queueType = "cluster";
						break;
					case MQQT_MODEL:
						queueType = "model";
						break;
					default:
						logger.warn("Unknown type of queue {}", response[i].getIntParameterValue(CMQC.MQIA_Q_TYPE));
						queueType = "unknown";
						break;
				}
				if (response[i].getParameter(CMQC.MQIA_USAGE) != null) {
					switch(response[i].getIntParameterValue(CMQC.MQIA_USAGE)) {
						case CMQC.MQUS_NORMAL:
							queueType += "-normal";
							break;
						case CMQC.MQUS_TRANSMISSION:
							queueType += "-transmission";
							break;
					}
				}
				sharedState.putQueueType(queueName, queueType);
			}

			Set<ExcludeFilters> excludeFilters = context.getQueueExcludeFilters();
			if(!ExcludeFilters.isExcluded(queueName,excludeFilters)) { //check for exclude filters
				logger.debug("Pulling out metrics for queue name {} for command {}",queueName,command);
				Iterator<String> itr = metrics.keySet().iterator();
				List<Metric> responseMetrics = Lists.newArrayList();
				while (itr.hasNext()) {
					String metrickey = itr.next();
					WMQMetricOverride wmqOverride = metrics.get(metrickey);
					try{
						PCFParameter pcfParam = response[i].getParameter(wmqOverride.getConstantValue());
						if (pcfParam != null) {
							if(pcfParam instanceof MQCFIN){
								int metricVal = response[i].getIntParameterValue(wmqOverride.getConstantValue());
								Metric metric = metricCreator.createMetric(metrickey, metricVal, wmqOverride, queueName, queueType, metrickey);
								responseMetrics.add(metric);
							}
							else if(pcfParam instanceof MQCFIL){
								int[] metricVals = response[i].getIntListParameterValue(wmqOverride.getConstantValue());
								if(metricVals != null){
									int count=0;
									for(int val : metricVals){
										count++;
										Metric metric = metricCreator.createMetric( metrickey + "_" + count,
												val, wmqOverride, queueName,
												metrickey + "_" + count);
										responseMetrics.add(metric);
									}
								}
							}
						} else {
							logger.warn("PCF parameter is null in response for Queue: {} for metric: {} in command {}", queueName, wmqOverride.getIbmCommand(),command);
						}
					}
					catch (PCFException pcfe) {
						logger.error("PCFException caught while collecting metric for Queue: {} for metric: {} in command {}",queueName, wmqOverride.getIbmCommand(),command, pcfe);
					}

				}
				context.transformAndPrintMetrics(responseMetrics);
			}
			else{
				logger.debug("Queue name {} is excluded.",queueName);
			}
		}
	}
}
