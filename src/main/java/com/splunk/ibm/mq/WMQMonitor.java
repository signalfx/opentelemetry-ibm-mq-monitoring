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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.metrics.MetricsConfig;
import com.splunk.ibm.mq.metricscollector.*;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import com.splunk.ibm.mq.util.WMQUtil;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WMQMonitor {

  private static final Logger logger = LoggerFactory.getLogger(WMQMonitor.class);

  private final List<QueueManager> queueManagers;
  private final List<Consumer<MetricsCollectorContext>> jobs = new ArrayList<>();
  private final LongGauge heartbeatGauge;
  private final ExecutorService threadPool;
  private final MetricsConfig metricsConfig;

  public WMQMonitor(ConfigWrapper config, ExecutorService threadPool, Meter meter) {
    List<Map<String, ?>> queueManagers = getQueueManagers(config);
    ObjectMapper mapper = new ObjectMapper();

    this.queueManagers = new ArrayList<>();

    for (Map<String, ?> queueManager : queueManagers) {
      try {
        QueueManager qManager = mapper.convertValue(queueManager, QueueManager.class);
        this.queueManagers.add(qManager);
      } catch (Throwable t) {
        logger.error("Error preparing queue manager {}", queueManager, t);
      }
    }

    this.metricsConfig = new MetricsConfig(config);

    this.heartbeatGauge = meter.gaugeBuilder("mq.heartbeat").setUnit("1").ofLongs().build();
    this.threadPool = threadPool;

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

  public void run() {
    for (QueueManager qm : this.queueManagers) {
      run(qm);
    }
  }

  @NotNull
  private List<Map<String, ?>> getQueueManagers(ConfigWrapper config) {
    List<Map<String, ?>> queueManagers = config.getQueueManagers();
    if (queueManagers.isEmpty()) {
      throw new IllegalStateException(
          "The 'queueManagers' section in config.yml is empty or otherwise incorrect.");
    }
    return queueManagers;
  }

  public void run(QueueManager queueManager) {
    String queueManagerName = queueManager.getName();
    logger.debug("WMQMonitor thread for queueManager {} started.", queueManagerName);
    long startTime = System.currentTimeMillis();
    MQQueueManager ibmQueueManager = null;
    PCFMessageAgent agent = null;
    int heartBeatMetricValue = 0;
    try {
      ibmQueueManager = WMQUtil.connectToQueueManager(queueManager);
      heartBeatMetricValue = 1;
      agent = WMQUtil.initPCFMessageAgent(queueManager, ibmQueueManager);
      extractAndReportMetrics(ibmQueueManager, queueManager, agent);
    } catch (Exception e) {
      logger.error(
          "Error connecting to QueueManager {} by thread {}: {}",
          queueManagerName,
          Thread.currentThread().getName(),
          e.getMessage(),
          e);
    } finally {
      if (this.metricsConfig.isMqHeartbeatEnabled()) {
        heartbeatGauge.set(
            heartBeatMetricValue,
            Attributes.of(AttributeKey.stringKey("queue.manager"), queueManagerName));
      }
      cleanUp(ibmQueueManager, agent);
      long endTime = System.currentTimeMillis() - startTime;
      logger.debug(
          "WMQMonitor thread for queueManager {} ended. Time taken = {} ms",
          queueManagerName,
          endTime);
    }
  }

  private void extractAndReportMetrics(
      MQQueueManager mqQueueManager, QueueManager queueManager, PCFMessageAgent agent) {
    logger.debug("Queueing {} jobs", jobs.size());
    MetricsCollectorContext context =
        new MetricsCollectorContext(queueManager, agent, mqQueueManager, this.metricsConfig);
    List<Callable<Void>> tasks = new ArrayList<>();
    for (Consumer<MetricsCollectorContext> collector : jobs) {
      tasks.add(
          () -> {
            try {
              long startTime = System.currentTimeMillis();
              collector.accept(context);
              long diffTime = System.currentTimeMillis() - startTime;
              if (diffTime > 60000L) {
                logger.warn(
                    "{} Task took {} ms to complete",
                    collector.getClass().getSimpleName(),
                    diffTime);
              } else {
                logger.debug(
                    "{} Task took {} ms to complete",
                    collector.getClass().getSimpleName(),
                    diffTime);
              }
            } catch (Exception e) {
              logger.error(
                  "Error while running task name = " + collector.getClass().getSimpleName(), e);
            }
            return null;
          });
    }

    try {
      this.threadPool.invokeAll(tasks);
    } catch (InterruptedException e) {
      logger.error("Error while the thread {} is waiting ", Thread.currentThread().getName(), e);
    }
  }

  /** Destroy the agent and disconnect from queue manager */
  private void cleanUp(MQQueueManager ibmQueueManager, PCFMessageAgent agent) {
    // Disconnect the agent.

    if (agent != null) {
      String qMgrName = agent.getQManagerName();
      try {
        agent.disconnect();
        logger.debug(
            "PCFMessageAgent disconnected for queueManager {} in thread {}",
            qMgrName,
            Thread.currentThread().getName());
      } catch (Exception e) {
        logger.error(
            "Error occurred  while disconnecting PCFMessageAgent for queueManager {} in thread {}",
            qMgrName,
            Thread.currentThread().getName(),
            e);
      }
    }

    // Disconnect queue manager
    if (ibmQueueManager != null) {
      String name = "";
      try {
        name = ibmQueueManager.getName();
        ibmQueueManager.disconnect();
        // logger.debug("Connection disconnected for queue manager {} in thread {}",
        // ibmQueueManager.getName(), Thread.currentThread().getName());
      } catch (Exception e) {
        logger.error(
            "Error occurred while disconnecting queueManager {} in thread {}",
            name,
            Thread.currentThread().getName(),
            e);
      }
    }
  }
}
