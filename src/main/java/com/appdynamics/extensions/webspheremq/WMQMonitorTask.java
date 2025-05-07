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
package com.appdynamics.extensions.webspheremq;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.util.StringUtils;
import com.appdynamics.extensions.webspheremq.common.Constants;
import com.appdynamics.extensions.webspheremq.common.WMQUtil;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.appdynamics.extensions.webspheremq.config.WMQMetricOverride;
import com.appdynamics.extensions.webspheremq.metricscollector.ChannelMetricsCollector;
import com.appdynamics.extensions.webspheremq.metricscollector.InquireChannelCmdCollector;
import com.appdynamics.extensions.webspheremq.metricscollector.InquireQueueManagerCmdCollector;
import com.appdynamics.extensions.webspheremq.metricscollector.JobSubmitterContext;
import com.appdynamics.extensions.webspheremq.metricscollector.ListenerMetricsCollector;
import com.appdynamics.extensions.webspheremq.metricscollector.MetricCreator;
import com.appdynamics.extensions.webspheremq.metricscollector.MetricsCollectorContext;
import com.appdynamics.extensions.webspheremq.metricscollector.MetricsPublisher;
import com.appdynamics.extensions.webspheremq.metricscollector.MetricsPublisherJob;
import com.appdynamics.extensions.webspheremq.metricscollector.QueueCollectorSharedState;
import com.appdynamics.extensions.webspheremq.metricscollector.QueueManagerMetricsCollector;
import com.appdynamics.extensions.webspheremq.metricscollector.QueueMetricsCollector;
import com.appdynamics.extensions.webspheremq.metricscollector.ReadConfigurationEventQueueCollector;
import com.appdynamics.extensions.webspheremq.metricscollector.TopicMetricsCollector;
import com.google.common.base.Strings;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import java.math.BigDecimal;
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
public class WMQMonitorTask implements AMonitorTaskRunnable {

  public static final Logger logger = LoggerFactory.getLogger(WMQMonitorTask.class);
  private final QueueManager queueManager;
  private MonitorContextConfiguration monitorContextConfig;
  private Map<String, ?> configMap;
  private MetricWriteHelper metricWriteHelper;

  public WMQMonitorTask(
      TasksExecutionServiceProvider tasksExecutionServiceProvider,
      MonitorContextConfiguration monitorContextConfig,
      QueueManager queueManager) {
    this.monitorContextConfig = monitorContextConfig;
    this.queueManager = queueManager;
    this.configMap = monitorContextConfig.getConfigYml();
    this.metricWriteHelper = tasksExecutionServiceProvider.getMetricWriteHelper();
  }

  public void run() {
    String queueManagerTobeDisplayed = WMQUtil.getQueueManagerNameFromConfig(queueManager);
    logger.debug("WMQMonitor thread for queueManager {} started.", queueManagerTobeDisplayed);
    long startTime = System.currentTimeMillis();
    MQQueueManager ibmQueueManager = null;
    PCFMessageAgent agent = null;
    BigDecimal heartBeatMetricValue = BigDecimal.ZERO;
    // encryptionKey is a global setting, which we need to inject to allow decrypting the queue
    // manager password.
    String encryptionKey = (String) configMap.get("encryptionKey");
    try {
      WMQContext auth = new WMQContext(queueManager, encryptionKey);
      Hashtable env = auth.getMQEnvironment();
      try {
        ibmQueueManager = new MQQueueManager(queueManager.getName(), env);
      } catch (MQException mqe) {
        logger.error(mqe.getMessage(), mqe);
        throw new TaskExecutionException(mqe.getMessage());
      }
      logger.debug(
          "MQQueueManager connection initiated for queueManager {} in thread {}",
          queueManagerTobeDisplayed,
          Thread.currentThread().getName());
      heartBeatMetricValue = BigDecimal.ONE;
      agent = initPCFMesageAgent(ibmQueueManager);
      extractAndReportMetrics(ibmQueueManager, agent);
    } catch (Exception e) {
      logger.error("Error in run of " + Thread.currentThread().getName(), e);
    } finally {
      cleanUp(ibmQueueManager, agent);
      metricWriteHelper.printMetric(
          StringUtils.concatMetricPath(
              monitorContextConfig.getMetricPrefix(), queueManagerTobeDisplayed, "HeartBeat"),
          heartBeatMetricValue,
          "AVG.AVG.IND");
      long endTime = System.currentTimeMillis() - startTime;
      logger.debug(
          "WMQMonitor thread for queueManager {} ended. Time taken = {} ms",
          queueManagerTobeDisplayed,
          endTime);
    }
  }

  private PCFMessageAgent initPCFMesageAgent(MQQueueManager ibmQueueManager) {
    PCFMessageAgent agent = null;
    try {
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
    } catch (MQDataException mqe) {
      logger.error(mqe.getMessage(), mqe);
    }
    return agent;
  }

  private void extractAndReportMetrics(MQQueueManager mqQueueManager, PCFMessageAgent agent) {
    // Step 1: Retrieve metrics from configuration
    Map<String, Map<String, WMQMetricOverride>> metricsMap =
        WMQUtil.getMetricsToReportFromConfigYml((List<Map>) configMap.get("mqMetrics"));
    CountDownLatch countDownLatch = new CountDownLatch(metricsMap.size());

    // Step 2: Inquire each metric type
    inquireQueueMangerMetrics(
        metricsMap.get(Constants.METRIC_TYPE_QUEUE_MANAGER), countDownLatch, mqQueueManager, agent);

    inquireChannelMetrics(
        metricsMap.get(Constants.METRIC_TYPE_CHANNEL), countDownLatch, mqQueueManager, agent);

    inquireQueueMetrics(
        metricsMap.get(Constants.METRIC_TYPE_QUEUE),
        QueueCollectorSharedState.getInstance(),
        countDownLatch,
        agent);

    inquireListenerMetrics(
        metricsMap.get(Constants.METRIC_TYPE_LISTENER),
        ListenerMetricsCollector::new,
        ListenerMetricsCollector.ARTIFACT,
        countDownLatch,
        agent);

    inquireTopicMetrics(
        metricsMap.get(Constants.METRIC_TYPE_TOPIC),
        TopicMetricsCollector::new,
        null,
        countDownLatch,
        agent);

    inquireConfigurationMetrics(
        metricsMap.get(Constants.METRIC_TYPE_CONFIGURATION), countDownLatch, mqQueueManager, agent);

    // Step 3: Await all jobs to complete
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      logger.error("Error while the thread {} is waiting ", Thread.currentThread().getName(), e);
    }
  }

  private void inquireQueueMangerMetrics(
      Map<String, WMQMetricOverride> metricsToReport,
      CountDownLatch countDownLatch,
      MQQueueManager mqQueueManager,
      PCFMessageAgent agent) {
    if (metricsToReport == null) {
      logger.warn("No metrics to report for type Queue Manager.");
      return;
    }

    Map<String, Map<String, WMQMetricOverride>> metricsByCommand =
        groupMetricsByCommand(metricsToReport);

    processMetricType(
        metricsByCommand,
        "MQCMD_INQUIRE_Q_MGR_STATUS",
        QueueManagerMetricsCollector::new,
        countDownLatch,
        agent);
    processMetricType(
        metricsByCommand,
        "MQCMD_INQUIRE_Q_MGR",
        InquireQueueManagerCmdCollector::new,
        countDownLatch,
        agent);
  }

  private void inquireChannelMetrics(
      Map<String, WMQMetricOverride> metricsToReport,
      CountDownLatch countDownLatch,
      MQQueueManager mqQueueManager,
      PCFMessageAgent agent) {
    if (metricsToReport == null) {
      logger.warn("No metrics to report for type Channel.");
      return;
    }

    Map<String, Map<String, WMQMetricOverride>> metricsByCommand =
        groupMetricsByCommand(metricsToReport);

    processMetricType(
        metricsByCommand,
        "MQCMD_INQUIRE_CHANNEL_STATUS",
        ChannelMetricsCollector::new,
        countDownLatch,
        agent);
    processMetricType(
        metricsByCommand,
        "MQCMD_INQUIRE_CHANNEL",
        InquireChannelCmdCollector::new,
        countDownLatch,
        agent);
  }

  // Helper to process general metric types
  private void processMetricType(
      Map<String, Map<String, WMQMetricOverride>> metricsByCommand,
      String primaryCommand,
      BiFunction<MetricsCollectorContext, MetricCreator, MetricsPublisher>
          primaryCollectorConstructor,
      CountDownLatch countDownLatch,
      PCFMessageAgent agent) {

    if (metricsByCommand.containsKey(primaryCommand)) {
      submitJob(
          metricsByCommand.get(primaryCommand),
          primaryCollectorConstructor,
          primaryCommand,
          countDownLatch,
          agent);
    }
  }

  // Helper to submit metrics collector jobs
  private void submitJob(
      Map<String, WMQMetricOverride> metrics,
      BiFunction<MetricsCollectorContext, MetricCreator, MetricsPublisher> collectorConstructor,
      String commandType,
      CountDownLatch countDownLatch,
      PCFMessageAgent agent) {

    MetricCreator metricCreator =
        new MetricCreator(monitorContextConfig.getMetricPrefix(), queueManager, commandType);
    MetricsCollectorContext context =
        new MetricsCollectorContext(metrics, queueManager, agent, metricWriteHelper);
    MetricsPublisher collector = collectorConstructor.apply(context, metricCreator);
    Runnable job = new MetricsPublisherJob(collector, countDownLatch);
    monitorContextConfig.getContext().getExecutorService().execute(commandType, job);
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
      CountDownLatch countDownLatch,
      PCFMessageAgent agent) {

    if (queueMetrics == null) {
      logger.warn("No queue metrics to report");
      return;
    }

    MetricsCollectorContext collectorContext =
        new MetricsCollectorContext(queueMetrics, queueManager, agent, metricWriteHelper);
    JobSubmitterContext jobSubmitterContext =
        new JobSubmitterContext(monitorContextConfig, countDownLatch, collectorContext);
    MetricsPublisher queueMetricsCollector =
        new QueueMetricsCollector(queueMetrics, sharedState, jobSubmitterContext);
    Runnable job = new MetricsPublisherJob(queueMetricsCollector, countDownLatch);
    monitorContextConfig.getContext().getExecutorService().execute("QueueMetricsCollector", job);
  }

  // Inquire for listener metrics
  private void inquireListenerMetrics(
      Map<String, WMQMetricOverride> metricsToReport,
      BiFunction<MetricsCollectorContext, MetricCreator, MetricsPublisher> collectorConstructor,
      String artifact,
      CountDownLatch countDownLatch,
      PCFMessageAgent agent) {

    if (metricsToReport == null) {
      logger.warn("No metrics to report for Listener.");
      return;
    }

    MetricsCollectorContext context =
        new MetricsCollectorContext(metricsToReport, queueManager, agent, metricWriteHelper);
    MetricCreator metricCreator =
        new MetricCreator(monitorContextConfig.getMetricPrefix(), queueManager, artifact);
    MetricsPublisher metricsCollector = collectorConstructor.apply(context, metricCreator);
    Runnable job = new MetricsPublisherJob(metricsCollector, countDownLatch);
    monitorContextConfig.getContext().getExecutorService().execute(artifact, job);
  }

  // Inquire for topic metrics
  private void inquireTopicMetrics(
      Map<String, WMQMetricOverride> metricsToReport,
      Function<JobSubmitterContext, MetricsPublisher> collectorConstructor,
      String artifact,
      CountDownLatch countDownLatch,
      PCFMessageAgent agent) {

    if (metricsToReport == null) {
      logger.warn("No metrics to report for Topic.");
      return;
    }

    MetricsCollectorContext context =
        new MetricsCollectorContext(metricsToReport, queueManager, agent, metricWriteHelper);
    JobSubmitterContext jobSubmitterContext =
        new JobSubmitterContext(monitorContextConfig, countDownLatch, context);
    MetricsPublisher metricsCollector = collectorConstructor.apply(jobSubmitterContext);
    Runnable job = new MetricsPublisherJob(metricsCollector, countDownLatch);
    monitorContextConfig.getContext().getExecutorService().execute(artifact, job);
  }

  // Inquire configuration-specific metrics
  private void inquireConfigurationMetrics(
      Map<String, WMQMetricOverride> configurationMetricsToReport,
      CountDownLatch countDownLatch,
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
    Runnable job = new MetricsPublisherJob(collector, countDownLatch);
    monitorContextConfig
        .getContext()
        .getExecutorService()
        .execute("ConfigurationMetricsCollector", job);
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

  public void onTaskComplete() {
    logger.info(
        "WebSphereMQ monitor thread completed for queueManager: {}",
        WMQUtil.getQueueManagerNameFromConfig(queueManager));
  }
}
