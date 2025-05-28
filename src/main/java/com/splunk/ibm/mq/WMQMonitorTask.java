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

import com.google.common.base.Strings;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.metricscollector.ChannelMetricsCollector;
import com.splunk.ibm.mq.metricscollector.InquireChannelCmdCollector;
import com.splunk.ibm.mq.metricscollector.InquireQueueManagerCmdCollector;
import com.splunk.ibm.mq.metricscollector.ListenerMetricsCollector;
import com.splunk.ibm.mq.metricscollector.MetricsCollectorContext;
import com.splunk.ibm.mq.metricscollector.MetricsPublisherJob;
import com.splunk.ibm.mq.metricscollector.PerformanceEventQueueCollector;
import com.splunk.ibm.mq.metricscollector.QueueManagerEventCollector;
import com.splunk.ibm.mq.metricscollector.QueueManagerMetricsCollector;
import com.splunk.ibm.mq.metricscollector.QueueMetricsCollector;
import com.splunk.ibm.mq.metricscollector.ReadConfigurationEventQueueCollector;
import com.splunk.ibm.mq.metricscollector.TopicMetricsCollector;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encapsulates all metrics collection for all artifacts related to a queue manager. */
public class WMQMonitorTask implements Runnable {

  public static final Logger logger = LoggerFactory.getLogger(WMQMonitorTask.class);
  private final QueueManager queueManager;
  private final List<Consumer<MetricsCollectorContext>> jobs = new ArrayList<>();
  private final ExecutorService threadPool;
  private final LongGauge heartbeatGauge;

  public WMQMonitorTask(
      ConfigWrapper config,
      Meter meter,
      QueueManager queueManager,
      ExecutorService threadPool,
      LongGauge heartbeatGauge) {
    this.queueManager = queueManager;
    this.threadPool = threadPool;
    this.heartbeatGauge = heartbeatGauge;

    jobs.add(new QueueManagerMetricsCollector(meter));
    jobs.add(new InquireQueueManagerCmdCollector(meter));
    jobs.add(new ChannelMetricsCollector(meter));
    jobs.add(new InquireChannelCmdCollector(meter));
    jobs.add(new QueueMetricsCollector(meter, threadPool, config));
    jobs.add(new ListenerMetricsCollector(meter));
    jobs.add(new TopicMetricsCollector(meter));
    jobs.add(new ReadConfigurationEventQueueCollector(meter));
    jobs.add(new PerformanceEventQueueCollector(meter));
    jobs.add(new QueueManagerEventCollector(meter));
  }

  @Override
  public void run() {
    String queueManagerName = queueManager.getName();
    logger.debug("WMQMonitor thread for queueManager {} started.", queueManagerName);
    long startTime = System.currentTimeMillis();
    MQQueueManager ibmQueueManager = null;
    PCFMessageAgent agent = null;
    int heartBeatMetricValue = 0;
    try {
      ibmQueueManager = connectToQueueManager(queueManager);
      heartBeatMetricValue = 1;
      agent = initPCFMessageAgent(queueManager, ibmQueueManager);
      extractAndReportMetrics(ibmQueueManager, agent);
    } catch (Exception e) {
      logger.error(
          "Error connecting to QueueManager {} by thread {}: {}",
          queueManagerName,
          Thread.currentThread().getName(),
          e.getMessage(),
          e);
    } finally {
      heartbeatGauge.set(
          heartBeatMetricValue,
          Attributes.of(AttributeKey.stringKey("queue.manager"), queueManagerName));
      cleanUp(ibmQueueManager, agent);
      long endTime = System.currentTimeMillis() - startTime;
      logger.debug(
          "WMQMonitor thread for queueManager {} ended. Time taken = {} ms",
          queueManagerName,
          endTime);
    }
  }

  public static MQQueueManager connectToQueueManager(QueueManager queueManager) {
    MQQueueManager ibmQueueManager = null;
    WMQContext auth = new WMQContext(queueManager);
    Hashtable env = auth.getMQEnvironment();

    try {
      ibmQueueManager = new MQQueueManager(queueManager.getName(), env);
    } catch (MQException mqe) {
      logger.error(mqe.getMessage(), mqe);
      throw new RuntimeException(mqe.getMessage());
    }
    logger.debug(
        "MQQueueManager connection initiated for queueManager {} in thread {}",
        queueManager.getName(),
        Thread.currentThread().getName());
    return ibmQueueManager;
  }

  public static PCFMessageAgent initPCFMessageAgent(
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

    CountDownLatch countDownLatch = new CountDownLatch(jobs.size());
    logger.debug("Queueing {} jobs", jobs.size());
    MetricsCollectorContext context =
        new MetricsCollectorContext(queueManager, agent, mqQueueManager);
    for (Consumer<MetricsCollectorContext> collector : jobs) {
      Runnable job = new MetricsPublisherJob(() -> collector.accept(context), countDownLatch);
      threadPool.submit(new TaskJob(collector.getClass().getSimpleName(), job));
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
}
