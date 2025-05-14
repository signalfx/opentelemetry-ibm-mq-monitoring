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
package com.splunk.ibm.mq;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.util.StringUtils;
import com.google.common.base.Strings;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.common.Constants;
import com.splunk.ibm.mq.common.WMQUtil;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.config.WMQMetricOverride;
import com.splunk.ibm.mq.metricscollector.ChannelMetricsCollector;
import com.splunk.ibm.mq.metricscollector.InquireChannelCmdCollector;
import com.splunk.ibm.mq.metricscollector.InquireQueueManagerCmdCollector;
import com.splunk.ibm.mq.metricscollector.JobSubmitterContext;
import com.splunk.ibm.mq.metricscollector.ListenerMetricsCollector;
import com.splunk.ibm.mq.metricscollector.MetricCreator;
import com.splunk.ibm.mq.metricscollector.MetricsCollectorContext;
import com.splunk.ibm.mq.metricscollector.MetricsPublisher;
import com.splunk.ibm.mq.metricscollector.MetricsPublisherJob;
import com.splunk.ibm.mq.metricscollector.PerformanceEventQueueCollector;
import com.splunk.ibm.mq.metricscollector.QueueCollectorSharedState;
import com.splunk.ibm.mq.metricscollector.QueueManagerEventCollector;
import com.splunk.ibm.mq.metricscollector.QueueManagerMetricsCollector;
import com.splunk.ibm.mq.metricscollector.QueueMetricsCollector;
import com.splunk.ibm.mq.metricscollector.ReadConfigurationEventQueueCollector;
import com.splunk.ibm.mq.metricscollector.TopicMetricsCollector;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encapsulates all metrics collection for all artifacts related to a queue manager. */
public class WMQMonitorTask implements Runnable {

  public static final Logger logger = LoggerFactory.getLogger(WMQMonitorTask.class);
  private final QueueManager queueManager;
  private final MonitorContextConfiguration monitorContextConfig;
  private final Map<String, ?> configMap;
  private final MetricWriteHelper metricWriteHelper;
  private List<MetricsPublisher> pendingJobs = new ArrayList<>();

  public WMQMonitorTask(
      MetricWriteHelper metricWriteHelper,
      MonitorContextConfiguration monitorContextConfig,
      QueueManager queueManager) {
    this.monitorContextConfig = monitorContextConfig;
    this.queueManager = queueManager;
    this.configMap = monitorContextConfig.getConfigYml();
    this.metricWriteHelper = metricWriteHelper;
  }

  @Override
  public void run() {
    String queueManagerName = WMQUtil.getQueueManagerNameFromConfig(queueManager);
    logger.debug("WMQMonitor thread for queueManager {} started.", queueManagerName);
    long startTime = System.currentTimeMillis();
    MQQueueManager ibmQueueManager = null;
    PCFMessageAgent agent = null;
    BigDecimal heartBeatMetricValue = BigDecimal.ZERO;
    // encryptionKey is a global setting; it is needed to allow decrypting the queue manager
    // password.
    String encryptionKey = (String) configMap.get("encryptionKey");
    try {
      ibmQueueManager = connectToQueueManager(queueManager, encryptionKey);
      heartBeatMetricValue = BigDecimal.ONE;
      agent = initPCFMesageAgent(queueManager, ibmQueueManager);
      extractAndReportMetrics(ibmQueueManager, agent);

    } catch (Exception e) {
      logger.error(
          "Error connecting to QueueManager {} by thread {}: {}",
          queueManagerName,
          Thread.currentThread().getName(),
          e.getMessage(),
          e);
    } finally {
      cleanUp(ibmQueueManager, agent);
      metricWriteHelper.printMetric(
          StringUtils.concatMetricPath(
              monitorContextConfig.getMetricPrefix(), queueManagerName, "HeartBeat"),
          heartBeatMetricValue,
          "AVG.AVG.IND");
      long endTime = System.currentTimeMillis() - startTime;
      logger.debug(
          "WMQMonitor thread for queueManager {} ended. Time taken = {} ms",
          queueManagerName,
          endTime);
    }
  }

  public static MQQueueManager connectToQueueManager(
      QueueManager queueManager, String encryptionKey) {
    MQQueueManager ibmQueueManager = null;
    WMQContext auth = new WMQContext(queueManager, encryptionKey);
    Hashtable env = auth.getMQEnvironment();

    try {
      ibmQueueManager = new MQQueueManager(queueManager.getName(), env);
    } catch (MQException mqe) {
      logger.error(mqe.getMessage(), mqe);
      throw new RuntimeException(mqe.getMessage());
    }
    logger.debug(
        "MQQueueManager connection initiated for queueManager {} in thread {}",
        WMQUtil.getQueueManagerNameFromConfig(queueManager),
        Thread.currentThread().getName());
    return ibmQueueManager;
  }

  public static PCFMessageAgent initPCFMesageAgent(
      QueueManager queueManager, MQQueueManager ibmQueueManager) {
    try {
      PCFMessageAgent agent;
      if (!Strings.isNullOrEmpty(queueManager.getModelQueueName())
          && !Strings.isNullOrEmpty(queueManager.getReplyQueuePrefix())) {
        logger.debug("Initializing the PCF agent for model queue and reply queue prefix.");
        agent = new PCFMessageAgent();
        agent.setModelQueueName(queueManager.getModelQueueName());
        agent.setReplyQueuePrefix(queueManager.getReplyQueuePrefix());
        logger.debug("Connecting to queueManager to set the modelQueueName and replyQueuePrefix.");
        agent.connect(ibmQueueManager);
      } else {
        agent = new PCFMessageAgent(ibmQueueManager);
      }
      if (queueManager.getCcsid() != Integer.MIN_VALUE) {
        agent.setCharacterSet(queueManager.getCcsid());
      }

      if (queueManager.getEncoding() != Integer.MIN_VALUE) {
        agent.setEncoding(queueManager.getEncoding());
      }
      logger.debug(
          "Initialized PCFMessageAgent for queueManager {} in thread {}",
          agent.getQManagerName(),
          Thread.currentThread().getName());
      return agent;
    } catch (MQDataException mqe) {
      logger.error(mqe.getMessage(), mqe);
      throw new RuntimeException(mqe);
    }
  }

  private void extractAndReportMetrics(MQQueueManager mqQueueManager, PCFMessageAgent agent) {
    // Step 1: Retrieve metrics from configuration
    Map<String, Map<String, WMQMetricOverride>> metricsMap =
        WMQUtil.getMetricsToReportFromConfigYml((List<Map>) configMap.get("mqMetrics"));
    pendingJobs.clear();

    // Step 2: Inquire each metric type
    inquireQueueMangerMetrics(metricsMap.get(Constants.METRIC_TYPE_QUEUE_MANAGER), agent);

    inquireChannelMetrics(metricsMap.get(Constants.METRIC_TYPE_CHANNEL), agent);

    inquireQueueMetrics(
        metricsMap.get(Constants.METRIC_TYPE_QUEUE),
        QueueCollectorSharedState.getInstance(),
        agent);

    inquireListenerMetrics(
        metricsMap.get(Constants.METRIC_TYPE_LISTENER), ListenerMetricsCollector::new, agent);

    inquireTopicMetrics(
        metricsMap.get(Constants.METRIC_TYPE_TOPIC), TopicMetricsCollector::new, agent);

    inquireConfigurationMetrics(
        metricsMap.get(Constants.METRIC_TYPE_CONFIGURATION), mqQueueManager, agent);

    inquirePerformanceMetrics(mqQueueManager);

    inquireQueueManagerEventsMetrics(mqQueueManager);

    // Step 3: enqueue all jobs
    CountDownLatch countDownLatch = new CountDownLatch(pendingJobs.size());
    for (MetricsPublisher collector : pendingJobs) {
      Runnable job = new MetricsPublisherJob(collector, countDownLatch);
      monitorContextConfig.getContext().getExecutorService().execute(collector.getName(), job);
    }
    pendingJobs.clear();

    // Step 4: Await all jobs to complete
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      logger.error("Error while the thread {} is waiting ", Thread.currentThread().getName(), e);
    }
  }

  private void inquireQueueMangerMetrics(
      Map<String, WMQMetricOverride> metricsToReport, PCFMessageAgent agent) {
    if (metricsToReport == null) {
      logger.warn("No metrics to report for type Queue Manager.");
      return;
    }

    Map<String, Map<String, WMQMetricOverride>> metricsByCommand =
        groupMetricsByCommand(metricsToReport);

    processMetricType(
        metricsByCommand, "MQCMD_INQUIRE_Q_MGR_STATUS", QueueManagerMetricsCollector::new, agent);
    processMetricType(
        metricsByCommand, "MQCMD_INQUIRE_Q_MGR", InquireQueueManagerCmdCollector::new, agent);
  }

  private void inquireChannelMetrics(
      Map<String, WMQMetricOverride> metricsToReport, PCFMessageAgent agent) {
    if (metricsToReport == null) {
      logger.warn("No metrics to report for type Channel.");
      return;
    }

    Map<String, Map<String, WMQMetricOverride>> metricsByCommand =
        groupMetricsByCommand(metricsToReport);

    processMetricType(
        metricsByCommand, "MQCMD_INQUIRE_CHANNEL_STATUS", ChannelMetricsCollector::new, agent);
    processMetricType(
        metricsByCommand, "MQCMD_INQUIRE_CHANNEL", InquireChannelCmdCollector::new, agent);
  }

  // Helper to process general metric types
  private void processMetricType(
      Map<String, Map<String, WMQMetricOverride>> metricsByCommand,
      String primaryCommand,
      BiFunction<MetricsCollectorContext, MetricCreator, MetricsPublisher>
          primaryCollectorConstructor,
      PCFMessageAgent agent) {

    if (metricsByCommand.containsKey(primaryCommand)) {
      submitJob(
          metricsByCommand.get(primaryCommand), primaryCollectorConstructor, primaryCommand, agent);
    }
  }

  // Helper to submit metrics collector jobs
  private void submitJob(
      Map<String, WMQMetricOverride> metrics,
      BiFunction<MetricsCollectorContext, MetricCreator, MetricsPublisher> collectorConstructor,
      String commandType,
      PCFMessageAgent agent) {

    MetricCreator metricCreator =
        new MetricCreator(monitorContextConfig.getMetricPrefix(), queueManager, commandType);
    MetricsCollectorContext context =
        new MetricsCollectorContext(metrics, queueManager, agent, metricWriteHelper);
    MetricsPublisher collector = collectorConstructor.apply(context, metricCreator);
    pendingJobs.add(collector);
  }

  // Helper to group metrics by IBM command
  private Map<String, Map<String, WMQMetricOverride>> groupMetricsByCommand(
      Map<String, WMQMetricOverride> metricsToReport) {

    Map<String, Map<String, WMQMetricOverride>> metricsByCommand = new HashMap<>();
    for (Map.Entry<String, WMQMetricOverride> entry : metricsToReport.entrySet()) {
      WMQMetricOverride wmqOverride = entry.getValue();
      String command =
          wmqOverride.getIbmCommand() != null ? wmqOverride.getIbmCommand() : "UNKNOWN_COMMAND";
      metricsByCommand.putIfAbsent(command, new HashMap<>());
      metricsByCommand.get(command).put(entry.getKey(), wmqOverride);
    }
    return metricsByCommand;
  }

  // Inquire for queue metrics
  private void inquireQueueMetrics(
      Map<String, WMQMetricOverride> queueMetrics,
      QueueCollectorSharedState sharedState,
      PCFMessageAgent agent) {

    if (queueMetrics == null) {
      logger.warn("No queue metrics to report");
      return;
    }

    MetricsCollectorContext collectorContext =
        new MetricsCollectorContext(queueMetrics, queueManager, agent, metricWriteHelper);
    JobSubmitterContext jobSubmitterContext =
        new JobSubmitterContext(monitorContextConfig, collectorContext);
    MetricsPublisher queueMetricsCollector =
        new QueueMetricsCollector(queueMetrics, sharedState, jobSubmitterContext);
    pendingJobs.add(queueMetricsCollector);
  }

  // Inquire for listener metrics
  private void inquireListenerMetrics(
      Map<String, WMQMetricOverride> metricsToReport,
      BiFunction<MetricsCollectorContext, MetricCreator, MetricsPublisher> collectorConstructor,
      PCFMessageAgent agent) {

    if (metricsToReport == null) {
      logger.warn("No metrics to report for Listener.");
      return;
    }

    MetricsCollectorContext context =
        new MetricsCollectorContext(metricsToReport, queueManager, agent, metricWriteHelper);
    MetricCreator metricCreator =
        new MetricCreator(
            monitorContextConfig.getMetricPrefix(),
            queueManager,
            ListenerMetricsCollector.ARTIFACT);
    MetricsPublisher metricsCollector = collectorConstructor.apply(context, metricCreator);
    pendingJobs.add(metricsCollector);
  }

  // Inquire for topic metrics
  private void inquireTopicMetrics(
      Map<String, WMQMetricOverride> metricsToReport,
      Function<JobSubmitterContext, MetricsPublisher> collectorConstructor,
      PCFMessageAgent agent) {

    if (metricsToReport == null) {
      logger.warn("No metrics to report for Topic.");
      return;
    }

    MetricsCollectorContext context =
        new MetricsCollectorContext(metricsToReport, queueManager, agent, metricWriteHelper);
    JobSubmitterContext jobSubmitterContext =
        new JobSubmitterContext(monitorContextConfig, context);
    MetricsPublisher metricsCollector = collectorConstructor.apply(jobSubmitterContext);
    pendingJobs.add(metricsCollector);
  }

  // Inquire configuration-specific metrics
  private void inquireConfigurationMetrics(
      Map<String, WMQMetricOverride> configurationMetricsToReport,
      MQQueueManager mqQueueManager,
      PCFMessageAgent agent) {

    if (configurationMetricsToReport == null) {
      logger.warn("No configuration metrics to report");
      return;
    }

    MetricCreator metricCreator =
        new MetricCreator(monitorContextConfig.getMetricPrefix(), queueManager);
    ReadConfigurationEventQueueCollector collector =
        new ReadConfigurationEventQueueCollector(
            configurationMetricsToReport,
            monitorContextConfig,
            agent,
            mqQueueManager,
            queueManager,
            metricWriteHelper,
            metricCreator);
    pendingJobs.add(collector);
  }

  // Inquire performance-specific metrics
  private void inquirePerformanceMetrics(MQQueueManager mqQueueManager) {

    PerformanceEventQueueCollector collector =
        new PerformanceEventQueueCollector(mqQueueManager, queueManager, metricWriteHelper);
    pendingJobs.add(collector);
  }

  // Inquire queue manager event specific metrics
  private void inquireQueueManagerEventsMetrics(MQQueueManager mqQueueManager) {

    QueueManagerEventCollector collector =
        new QueueManagerEventCollector(mqQueueManager, queueManager, metricWriteHelper);
    pendingJobs.add(collector);
  }

  /** Destroy the agent and disconnect from queue manager */
  private void cleanUp(MQQueueManager ibmQueueManager, PCFMessageAgent agent) {
    // Disconnect the agent.

    if (agent != null) {
      try {
        String qMgrName = agent.getQManagerName();
        agent.disconnect();
        logger.debug(
            "PCFMessageAgent disconnected for queueManager {} in thread {}",
            qMgrName,
            Thread.currentThread().getName());
      } catch (Exception e) {
        logger.error(
            "Error occurred  while disconnecting PCFMessageAgent for queueManager {} in thread {}",
            queueManager.getName(),
            Thread.currentThread().getName(),
            e);
      }
    }

    // Disconnect queue manager
    if (ibmQueueManager != null) {
      try {
        ibmQueueManager.disconnect();
        // logger.debug("Connection disconnected for queue manager {} in thread {}",
        // ibmQueueManager.getName(), Thread.currentThread().getName());
      } catch (Exception e) {
        logger.error(
            "Error occurred while disconnecting queueManager {} in thread {}",
            queueManager.getName(),
            Thread.currentThread().getName(),
            e);
      }
    }
  }
}
