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
package com.splunk.ibm.mq.metricscollector;

import static java.util.Collections.emptyList;

import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.splunk.ibm.mq.config.ExcludeFilters;
import com.splunk.ibm.mq.config.QueueManager;
import com.splunk.ibm.mq.opentelemetry.OpenTelemetryMetricWriteHelper;
import java.io.IOException;
import java.util.*;
import javax.annotation.concurrent.Immutable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A temporary bundle to contain the collaborators of the original MetricsCollector base class until
 * we can finish unwinding things. When done and there are no longer usages of MetricsCollector, we
 * could consider renaming this.
 */
@Immutable
public final class MetricsCollectorContext {

  private static final Logger logger = LoggerFactory.getLogger(MetricsCollectorContext.class);

  private final QueueManager queueManager;
  private final PCFMessageAgent agent;
  private final OpenTelemetryMetricWriteHelper metricWriteHelper;

  public MetricsCollectorContext(
      QueueManager queueManager,
      PCFMessageAgent agent,
      OpenTelemetryMetricWriteHelper metricWriteHelper) {
    this.queueManager = queueManager;
    this.agent = agent;
    this.metricWriteHelper = metricWriteHelper;
  }

  Set<String> getChannelIncludeFilterNames() {
    return queueManager.getChannelFilters().getInclude();
  }

  Set<ExcludeFilters> getChannelExcludeFilters() {
    return queueManager.getChannelFilters().getExclude();
  }

  Set<String> getListenerIncludeFilterNames() {
    return queueManager.getListenerFilters().getInclude();
  }

  Set<ExcludeFilters> getListenerExcludeFilters() {
    return queueManager.getListenerFilters().getExclude();
  }

  Set<String> getTopicIncludeFilterNames() {
    return queueManager.getTopicFilters().getInclude();
  }

  Set<ExcludeFilters> getTopicExcludeFilters() {
    return queueManager.getTopicFilters().getExclude();
  }

  Set<String> getQueueIncludeFilterNames() {
    return queueManager.getQueueFilters().getInclude();
  }

  Set<ExcludeFilters> getQueueExcludeFilters() {
    return queueManager.getQueueFilters().getExclude();
  }

  @NotNull
  List<PCFMessage> send(PCFMessage request) throws IOException, MQDataException {
    PCFMessage[] result = agent.send(request);
    return result == null ? emptyList() : Arrays.asList(result);
  }

  String getQueueManagerName() {
    return queueManager.getName();
  }

  QueueManager getQueueManager() {
    return queueManager;
  }

  PCFMessageAgent getAgent() {
    return agent;
  }

  OpenTelemetryMetricWriteHelper getMetricWriteHelper() {
    return metricWriteHelper;
  }
}
