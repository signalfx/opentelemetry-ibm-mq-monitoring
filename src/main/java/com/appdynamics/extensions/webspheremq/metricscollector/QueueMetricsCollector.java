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

public class QueueMetricsCollector extends MetricsCollector implements Runnable {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(QueueMetricsCollector.class);
	private final String artifact = "Queues";

	// hack to share state of queue type between collectors.
	// The queue information is only available as response of some commands.
	protected static ConcurrentHashMap<String, String> queueTypes = new ConcurrentHashMap<>();

	public QueueMetricsCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper, CountDownLatch countDownLatch) {
		this.metricsToReport = metricsToReport;
		this.monitorContextConfig = monitorContextConfig;
		this.agent = agent;
		this.metricWriteHelper = metricWriteHelper;
		this.queueManager = queueManager;
		this.countDownLatch = countDownLatch;
	}

	public void run() {
		try {
			this.process();
		} catch (TaskExecutionException e) {
			logger.error("Error in QueueMetricsCollector ", e);
		} finally {
			countDownLatch.countDown();
		}
	}

	@Override
	protected void publishMetrics() throws TaskExecutionException {
		logger.info("Collecting queue metrics...");
		List<Future> futures = Lists.newArrayList();
		Map<String, WMQMetricOverride>  metricsForInquireQCmd = getMetricsToReport(InquireQCmdCollector.COMMAND);
		if(!metricsForInquireQCmd.isEmpty()){
			futures.add(monitorContextConfig.getContext().getExecutorService().submit("InquireQCmdCollector", new InquireQCmdCollector(this,metricsForInquireQCmd)));
		}
		Map<String, WMQMetricOverride>  metricsForInquireQStatusCmd = getMetricsToReport(InquireQStatusCmdCollector.COMMAND);
		if(!metricsForInquireQStatusCmd.isEmpty()){
			futures.add(monitorContextConfig.getContext().getExecutorService().submit("InquireQStatusCmdCollector", new InquireQStatusCmdCollector(this,metricsForInquireQStatusCmd)));
		}
		Map<String, WMQMetricOverride>  metricsForResetQStatsCmd = getMetricsToReport(ResetQStatsCmdCollector.COMMAND);
		if(!metricsForResetQStatsCmd.isEmpty()){
			futures.add(monitorContextConfig.getContext().getExecutorService().submit("ResetQStatsCmdCollector", new ResetQStatsCmdCollector(this,metricsForResetQStatsCmd)));
		}
		for(Future f: futures){
			try {
				long timeout = 20;
				if(monitorContextConfig.getConfigYml().get("queueMetricsCollectionTimeoutInSeconds") != null){
					timeout = (Integer)monitorContextConfig.getConfigYml().get("queueMetricsCollectionTimeoutInSeconds");
				}
				f.get(timeout, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.error("The thread was interrupted ",e);
			} catch (ExecutionException e) {
				logger.error("Something unforeseen has happened ",e);
			} catch (TimeoutException e) {
				logger.error("Thread timed out ",e);
			}
		}
	}

	private Map<String, WMQMetricOverride> getMetricsToReport(String command) {
		Map<String, WMQMetricOverride> commandMetrics = Maps.newHashMap();
		if (getMetricsToReport() == null || getMetricsToReport().isEmpty()) {
			logger.debug("There are no metrics configured for {}",command);
			return commandMetrics;
		}
		Iterator<String> itr = getMetricsToReport().keySet().iterator();
		while (itr.hasNext()) {
			String metrickey = itr.next();
			WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
			if(wmqOverride.getIbmCommand().equalsIgnoreCase(command)){
				commandMetrics.put(metrickey,wmqOverride);
			}
		}
		return commandMetrics;
	}

	@Override
	public String getArtifact() {
		return artifact;
	}

	@Override
	public Map<String, WMQMetricOverride> getMetricsToReport() {
		return this.metricsToReport;
	}

	protected void processPCFRequestAndPublishQMetrics(String queueGenericName, PCFMessage request, String command) throws IOException, MQDataException {
		logger.debug("sending PCF agent request to query metrics for generic queue {} for command {}",queueGenericName,command);
		long startTime = System.currentTimeMillis();
		PCFMessage[] response = agent.send(request);
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
				queueType = queueTypes.get(queueName);
				if (queueType == null) {
					continue;
				}
			} else {
				switch(response[i].getIntParameterValue(CMQC.MQIA_Q_TYPE)) {
					case MQQT_LOCAL:
						queueType = "local";
						switch(response[i].getIntParameterValue(CMQC.MQIA_USAGE)) {
							case CMQC.MQUS_NORMAL:
								queueType += "-normal";
							case CMQC.MQUS_TRANSMISSION:
								queueType += "-transmission";
						}
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
						switch(response[i].getIntParameterValue(CMQC.MQIA_USAGE)) {
							case CMQC.MQUS_NORMAL:
								queueType += "-normal";
							case CMQC.MQUS_TRANSMISSION:
								queueType += "-transmission";
						}
						break;
					default:
						logger.warn("Unknown type of queue {}", response[i].getIntParameterValue(CMQC.MQIA_Q_TYPE));
						queueType = "unknown";
						break;
				}
				queueTypes.put(queueName,queueType);
			}


			Set<ExcludeFilters> excludeFilters = this.queueManager.getQueueFilters().getExclude();
			if(!isExcluded(queueName,excludeFilters)) { //check for exclude filters
				logger.debug("Pulling out metrics for queue name {} for command {}",queueName,command);
				Iterator<String> itr = getMetricsToReport().keySet().iterator();
				List<Metric> metrics = Lists.newArrayList();
				while (itr.hasNext()) {
					String metrickey = itr.next();
					WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
					try{
						PCFParameter pcfParam = response[i].getParameter(wmqOverride.getConstantValue());
						if (pcfParam != null) {
							if(pcfParam instanceof MQCFIN){
								int metricVal = response[i].getIntParameterValue(wmqOverride.getConstantValue());
								Metric metric = createMetric(queueManager, metrickey, metricVal, wmqOverride, getArtifact(), queueName, queueType, metrickey);
								metrics.add(metric);
							}
							else if(pcfParam instanceof MQCFIL){
								int[] metricVals = response[i].getIntListParameterValue(wmqOverride.getConstantValue());
								if(metricVals != null){
									int count=0;
									for(int val : metricVals){
										count++;
										Metric metric = createMetric(queueManager, metrickey + "_" + count,
												val, wmqOverride, getArtifact(), queueName,
												metrickey + "_" + count);
										metrics.add(metric);
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
				publishMetrics(metrics);
			}
			else{
				logger.debug("Queue name {} is excluded.",queueName);
			}
		}
	}
}
