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
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WMQMonitor implements Runnable {

  public static final Logger logger = LoggerFactory.getLogger(WMQMonitor.class);

  private final ExecutorService threadPool;
  private final ConfigWrapper config;
  private final List<TaskJob> jobs;

  public WMQMonitor(ConfigWrapper config, ExecutorService threadPool, Meter meter) {
    this.config = config;
    this.threadPool = threadPool;
    LongGauge heartbeatGauge = meter.gaugeBuilder("mq.heartbeat").setUnit("1").ofLongs().build();
    List<Map<String, ?>> queueManagers = getQueueManagers();
    ObjectMapper mapper = new ObjectMapper();

    this.jobs = new ArrayList<>();

    for (Map<String, ?> queueManager : queueManagers) {
      try {
        QueueManager qManager = mapper.convertValue(queueManager, QueueManager.class);
        WMQMonitorTask task =
            new WMQMonitorTask(config, meter, qManager, threadPool, heartbeatGauge);
        jobs.add(new TaskJob((String) queueManager.get("name"), task));
      } catch (Throwable t) {
        logger.error("Error preparing queue manager {}", queueManager, t);
      }
    }
  }

  @Override
  public void run() {
    for (TaskJob job : jobs) {
      try {
        threadPool.submit(job);
      } catch (Throwable t) {
        logger.error("Error preparing queue manager {}", job, t);
      }
    }
  }

  @NotNull
  private List<Map<String, ?>> getQueueManagers() {
    List<Map<String, ?>> queueManagers = config.getQueueManagers();
    if (queueManagers.isEmpty()) {
      throw new IllegalStateException(
          "The 'queueManagers' section in config.yml is empty or otherwise incorrect.");
    }
    return queueManagers;
  }
}
