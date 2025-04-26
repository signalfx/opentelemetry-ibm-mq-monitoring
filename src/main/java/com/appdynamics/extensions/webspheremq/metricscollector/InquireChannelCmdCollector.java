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
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.pcf.MQCFIL;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is responsible for channel inquiry metric collection.
 */
public class InquireChannelCmdCollector extends MetricsCollector {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(InquireChannelCmdCollector.class);
	private static final String ARTIFACT = "Channels";

	public InquireChannelCmdCollector(Map<String, WMQMetricOverride> metricsToReport, MonitorContextConfiguration monitorContextConfig, PCFMessageAgent agent, QueueManager queueManager, MetricWriteHelper metricWriteHelper) {
		super(metricsToReport, monitorContextConfig, agent, metricWriteHelper, queueManager, null, ARTIFACT);
	}

	public void run() {
		try {
			logger.info("Collecting metrics for command MQCMD_INQUIRE_CHANNEL");
			this.process();
		} catch (TaskExecutionException e) {
			logger.error("Error in ChannelMetricsCollector ", e);
		} finally {
			countDownLatch.countDown();
		}
	}

	@Override
	public void publishMetrics() throws TaskExecutionException {
		long entryTime = System.currentTimeMillis();

		if (getMetricsToReport() == null || getMetricsToReport().isEmpty()) {
			logger.debug("Channel metrics to report from the config is null or empty, nothing to publish");
			return;
		}

		Set<String> channelGenericNames = this.queueManager.getChannelFilters().getInclude();

		for(String channelGenericName : channelGenericNames){
			PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_CHANNEL);
			request.addParameter(CMQCFC.MQCACH_CHANNEL_NAME, channelGenericName);
			request.addParameter(new MQCFIL(MQConstants.MQIACF_CHANNEL_ATTRS, new int[] {MQConstants.MQIACF_ALL}));
			try {
				logger.debug("sending PCF agent request to query metrics for generic channel {}", channelGenericName);
				long startTime = System.currentTimeMillis();
				PCFMessage[] response = agent.send(request);
				long endTime = System.currentTimeMillis() - startTime;
				logger.debug("PCF agent queue metrics query response for generic queue {} received in {} milliseconds", channelGenericName, endTime);
				if (response == null || response.length <= 0) {
					logger.warn("Unexpected Error while PCFMessage.send(), response is either null or empty");
					return;
				}
                for (PCFMessage pcfMessage : response) {
                    String channelName = pcfMessage.getStringParameterValue(CMQCFC.MQCACH_CHANNEL_NAME).trim();
                    Set<ExcludeFilters> excludeFilters = this.queueManager.getChannelFilters().getExclude();
                    if (!ExcludeFilters.isExcluded(channelName, excludeFilters)) { //check for exclude filters
                        logger.debug("Pulling out metrics for channel name {}", channelName);
                        Iterator<String> itr = getMetricsToReport().keySet().iterator();
                        List<Metric> metrics = Lists.newArrayList();
                        while (itr.hasNext()) {
                            String metrickey = itr.next();
                            WMQMetricOverride wmqOverride = getMetricsToReport().get(metrickey);
							if (pcfMessage.getParameter(wmqOverride.getConstantValue()) == null) {
								logger.debug("Missing property {} on {}", metrickey, channelName);
								continue;
							}
                            int metricVal = pcfMessage.getIntParameterValue(wmqOverride.getConstantValue());
                            Metric metric = createMetric(queueManager, metrickey, metricVal, wmqOverride, getArtifact(), channelName, metrickey);
                            metrics.add(metric);
                        }
						metricWriteHelper.transformAndPrintMetrics(metrics);
                    } else {
                        logger.debug("Channel name {} is excluded.", channelName);
                    }
                }
			}
			catch (PCFException pcfe) {
				if (pcfe.getReason() == MQConstants.MQRCCF_CHL_STATUS_NOT_FOUND) {
					String errorMsg = "Channel- " + channelGenericName + " :";
					errorMsg += "Could not collect channel information as channel is stopped or inactive: Reason '3065'\n";
					errorMsg += "If the channel type is MQCHT_RECEIVER, MQCHT_SVRCONN or MQCHT_CLUSRCVR, then the only action is to enable the channel, not start it.";
					logger.error(errorMsg,pcfe);
				} else if (pcfe.getReason() == MQConstants.MQRC_SELECTOR_ERROR) {
					logger.error("Invalid metrics passed while collecting channel metrics, check config.yaml: Reason '2067'",pcfe);
				}
				logger.error(pcfe.getMessage(), pcfe);
			} catch (Exception e) {
				logger.error("Unexpected Error occoured while collecting metrics for channel " + channelGenericName, e);
			}
		}

		long exitTime = System.currentTimeMillis() - entryTime;
		logger.debug("Time taken to publish metrics for all channels is {} milliseconds", exitTime);

	}
}