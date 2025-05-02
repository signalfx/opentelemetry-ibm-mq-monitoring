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
import com.appdynamics.extensions.webspheremq.metricscollector.*;
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
    Map<String, Map<String, WMQMetricOverride>> metricsMap =
        WMQUtil.getMetricsToReportFromConfigYml((List<Map>) configMap.get("mqMetrics"));

    CountDownLatch countDownLatch = new CountDownLatch(metricsMap.size());

    Map<String, WMQMetricOverride> qMgrMetricsToReport =
        metricsMap.get(Constants.METRIC_TYPE_QUEUE_MANAGER);
    if (qMgrMetricsToReport != null) {
      Map<String, Map<String, WMQMetricOverride>> metricsByCommand = new HashMap<>();
      for (String key : qMgrMetricsToReport.keySet()) {
        WMQMetricOverride wmqOverride = qMgrMetricsToReport.get(key);
        String cmd =
            wmqOverride.getIbmCommand() == null
                ? "MQCMD_INQUIRE_Q_MGR_STATUS"
                : wmqOverride.getIbmCommand();
        metricsByCommand.putIfAbsent(cmd, new HashMap<>());
        metricsByCommand.get(cmd).put(key, wmqOverride);
      }
      Map<String, WMQMetricOverride> queueManagerMetricsToReport =
          metricsByCommand.get("MQCMD_INQUIRE_Q_MGR_STATUS");
      if (queueManagerMetricsToReport != null) {
        MetricCreator metricCreator =
            new MetricCreator(monitorContextConfig.getMetricPrefix(), queueManager);
        MetricsCollectorContext context =
            new MetricsCollectorContext(
                queueManagerMetricsToReport, queueManager, agent, metricWriteHelper);
        MetricsPublisher qMgrMetricsCollector =
            new QueueManagerMetricsCollector(context, metricCreator);
        Runnable job = new MetricsPublisherJob(qMgrMetricsCollector, countDownLatch);
        monitorContextConfig
            .getContext()
            .getExecutorService()
            .execute("QueueManagerMetricsCollector", job);
      }
      Map<String, WMQMetricOverride> inquireMetricsToReport =
          metricsByCommand.get("MQCMD_INQUIRE_Q_MGR");
      if (inquireMetricsToReport != null) {
        MetricCreator metricCreator =
            new MetricCreator(
                monitorContextConfig.getMetricPrefix(),
                queueManager,
                InquireQueueManagerCmdCollector.ARTIFACT);
        MetricsCollectorContext context =
            new MetricsCollectorContext(
                inquireMetricsToReport, queueManager, agent, metricWriteHelper);
        MetricsPublisher inquireQueueMgrMetricsCollector =
            new InquireQueueManagerCmdCollector(context, metricCreator);
        Runnable job = new MetricsPublisherJob(inquireQueueMgrMetricsCollector, countDownLatch);
        monitorContextConfig
            .getContext()
            .getExecutorService()
            .execute("InquireQueueManagerCmdCollector", job);
      }
    } else {
      logger.warn("No queue manager metrics to report");
    }

    Map<String, WMQMetricOverride> channelMetricsToReport =
        metricsMap.get(Constants.METRIC_TYPE_CHANNEL);
    if (channelMetricsToReport != null) {
      Map<String, Map<String, WMQMetricOverride>> metricsByCommand = new HashMap<>();
      for (String key : channelMetricsToReport.keySet()) {
        WMQMetricOverride wmqOverride = channelMetricsToReport.get(key);
        String cmd =
            wmqOverride.getIbmCommand() == null
                ? "MQCMD_INQUIRE_CHANNEL_STATUS"
                : wmqOverride.getIbmCommand();
        metricsByCommand.putIfAbsent(cmd, new HashMap<>());
        metricsByCommand.get(cmd).put(key, wmqOverride);
      }
      if (metricsByCommand.get("MQCMD_INQUIRE_CHANNEL_STATUS") != null) {
        Map<String, WMQMetricOverride> metricsToReport =
            metricsByCommand.get("MQCMD_INQUIRE_CHANNEL_STATUS");
        MetricCreator metricCreator =
            new MetricCreator(
                monitorContextConfig.getMetricPrefix(),
                queueManager,
                ChannelMetricsCollector.ARTIFACT);
        MetricsCollectorContext context =
            new MetricsCollectorContext(metricsToReport, queueManager, agent, metricWriteHelper);
        MetricsPublisher channelMetricsCollector =
            new ChannelMetricsCollector(context, metricCreator);
        Runnable job = new MetricsPublisherJob(channelMetricsCollector, countDownLatch);
        monitorContextConfig
            .getContext()
            .getExecutorService()
            .execute("ChannelMetricsCollector", job);
      }
      if (metricsByCommand.get("MQCMD_INQUIRE_CHANNEL") != null) {
        MetricCreator metricCreator =
            new MetricCreator(
                monitorContextConfig.getMetricPrefix(),
                queueManager,
                InquireChannelCmdCollector.ARTIFACT);
        Map<String, WMQMetricOverride> metricsToReport =
            metricsByCommand.get("MQCMD_INQUIRE_CHANNEL");
        MetricsCollectorContext context =
            new MetricsCollectorContext(metricsToReport, queueManager, agent, metricWriteHelper);
        MetricsPublisher inquireChannelMetricsCollector =
            new InquireChannelCmdCollector(context, metricCreator);
        Runnable job = new MetricsPublisherJob(inquireChannelMetricsCollector, countDownLatch);
        monitorContextConfig
            .getContext()
            .getExecutorService()
            .execute("InquireChannelCmdCollector", job);
      }
    } else {
      logger.warn("No channel metrics to report");
    }

    Map<String, WMQMetricOverride> queueMetricsToReport =
        metricsMap.get(Constants.METRIC_TYPE_QUEUE);
    if (queueMetricsToReport != null) {
      QueueCollectorSharedState sharedState = QueueCollectorSharedState.getInstance();
      MetricsCollectorContext collectorContext =
          new MetricsCollectorContext(queueMetricsToReport, queueManager, agent, metricWriteHelper);
      JobSubmitterContext jobSubmitterContext =
          new JobSubmitterContext(monitorContextConfig, countDownLatch, collectorContext);
      MetricsPublisher queueMetricsCollector =
          new QueueMetricsCollector(queueMetricsToReport, sharedState, jobSubmitterContext);
      Runnable job = new MetricsPublisherJob(queueMetricsCollector, countDownLatch);
      monitorContextConfig.getContext().getExecutorService().execute("QueueMetricsCollector", job);
    } else {
      logger.warn("No queue metrics to report");
    }

    Map<String, WMQMetricOverride> listenerMetricsToReport =
        metricsMap.get(Constants.METRIC_TYPE_LISTENER);
    if (listenerMetricsToReport != null) {
      MetricCreator metricCreator =
          new MetricCreator(
              monitorContextConfig.getMetricPrefix(),
              queueManager,
              ListenerMetricsCollector.ARTIFACT);
      MetricsCollectorContext context =
          new MetricsCollectorContext(
              listenerMetricsToReport, queueManager, agent, metricWriteHelper);
      MetricsPublisher listenerMetricsCollector =
          new ListenerMetricsCollector(context, metricCreator);
      Runnable job = new MetricsPublisherJob(listenerMetricsCollector, countDownLatch);
      monitorContextConfig
          .getContext()
          .getExecutorService()
          .execute("ListenerMetricsCollector", job);
    } else {
      logger.warn("No listener metrics to report");
    }

    Map<String, WMQMetricOverride> topicMetricsToReport =
        metricsMap.get(Constants.METRIC_TYPE_TOPIC);
    if (topicMetricsToReport != null) {
      MetricsCollectorContext collectorContext =
          new MetricsCollectorContext(topicMetricsToReport, queueManager, agent, metricWriteHelper);
      JobSubmitterContext jobSubmitterContext =
          new JobSubmitterContext(monitorContextConfig, countDownLatch, collectorContext);
      MetricsPublisher topicsMetricsCollector = new TopicMetricsCollector(jobSubmitterContext);
      Runnable job = new MetricsPublisherJob(topicsMetricsCollector, countDownLatch);
      monitorContextConfig.getContext().getExecutorService().execute("TopicMetricsCollector", job);
    } else {
      logger.warn("No topic metrics to report");
    }

    Map<String, WMQMetricOverride> configurationMetricsToReport =
        metricsMap.get(Constants.METRIC_TYPE_CONFIGURATION);
    if (configurationMetricsToReport != null) {
      MetricCreator metricCreator =
          new MetricCreator(monitorContextConfig.getMetricPrefix(), queueManager);
      ReadConfigurationEventQueueCollector configurationEventQueueCollector =
          new ReadConfigurationEventQueueCollector(
              configurationMetricsToReport,
              this.monitorContextConfig,
              agent,
              mqQueueManager,
              queueManager,
              metricWriteHelper,
              metricCreator);
      Runnable job = new MetricsPublisherJob(configurationEventQueueCollector, countDownLatch);
      monitorContextConfig
          .getContext()
          .getExecutorService()
          .execute("ReadConfigurationEventQueueCollector", job);
    } else {
      logger.warn("No configuration queue metrics to report");
    }

    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      logger.error("Error while the thread {} is waiting ", Thread.currentThread().getName(), e);
    }
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
