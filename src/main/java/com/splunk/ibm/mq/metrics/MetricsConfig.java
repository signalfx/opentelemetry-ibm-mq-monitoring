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
package com.splunk.ibm.mq.metrics;

import com.splunk.ibm.mq.opentelemetry.ConfigWrapper;
import java.util.Map;

// This file is generated using weaver. Do not edit manually.

/**
 * Configuration of metrics as defined in config.yml.
 *
 */
public class MetricsConfig {

  private final Map<String, ?> config;

  public MetricsConfig(ConfigWrapper config) {
      this.config = config.getMetrics();
  }


  public boolean isMqMessageRetryCountEnabled() {
    Object metricInfo = config.get("mq.message.retry.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqStatusEnabled() {
    Object metricInfo = config.get("mq.status");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMaxSharingConversationsEnabled() {
    Object metricInfo = config.get("mq.max.sharing.conversations");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqCurrentSharingConversationsEnabled() {
    Object metricInfo = config.get("mq.current.sharing.conversations");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqByteReceivedEnabled() {
    Object metricInfo = config.get("mq.byte.received");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqByteSentEnabled() {
    Object metricInfo = config.get("mq.byte.sent");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqBuffersReceivedEnabled() {
    Object metricInfo = config.get("mq.buffers.received");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqBuffersSentEnabled() {
    Object metricInfo = config.get("mq.buffers.sent");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMessageCountEnabled() {
    Object metricInfo = config.get("mq.message.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqOpenInputCountEnabled() {
    Object metricInfo = config.get("mq.open.input.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqOpenOutputCountEnabled() {
    Object metricInfo = config.get("mq.open.output.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqHighQueueDepthEnabled() {
    Object metricInfo = config.get("mq.high.queue.depth");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqServiceIntervalEnabled() {
    Object metricInfo = config.get("mq.service.interval");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqQueueDepthFullEventEnabled() {
    Object metricInfo = config.get("mq.queue.depth.full.event");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqQueueDepthHighEventEnabled() {
    Object metricInfo = config.get("mq.queue.depth.high.event");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqQueueDepthLowEventEnabled() {
    Object metricInfo = config.get("mq.queue.depth.low.event");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqUncommittedMessagesEnabled() {
    Object metricInfo = config.get("mq.uncommitted.messages");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqOldestMsgAgeEnabled() {
    Object metricInfo = config.get("mq.oldest.msg.age");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqCurrentMaxQueueFilesizeEnabled() {
    Object metricInfo = config.get("mq.current.max.queue.filesize");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqCurrentQueueFilesizeEnabled() {
    Object metricInfo = config.get("mq.current.queue.filesize");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqInstancesPerClientEnabled() {
    Object metricInfo = config.get("mq.instances.per.client");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMessageDeqCountEnabled() {
    Object metricInfo = config.get("mq.message.deq.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMessageEnqCountEnabled() {
    Object metricInfo = config.get("mq.message.enq.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqQueueDepthEnabled() {
    Object metricInfo = config.get("mq.queue.depth");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqServiceIntervalEventEnabled() {
    Object metricInfo = config.get("mq.service.interval.event");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqReusableLogSizeEnabled() {
    Object metricInfo = config.get("mq.reusable.log.size");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqManagerActiveChannelsEnabled() {
    Object metricInfo = config.get("mq.manager.active.channels");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqRestartLogSizeEnabled() {
    Object metricInfo = config.get("mq.restart.log.size");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMaxQueueDepthEnabled() {
    Object metricInfo = config.get("mq.max.queue.depth");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqOnqtime1Enabled() {
    Object metricInfo = config.get("mq.onqtime.1");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqOnqtime2Enabled() {
    Object metricInfo = config.get("mq.onqtime.2");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMessageReceivedCountEnabled() {
    Object metricInfo = config.get("mq.message.received.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMessageSentCountEnabled() {
    Object metricInfo = config.get("mq.message.sent.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqMaxInstancesEnabled() {
    Object metricInfo = config.get("mq.max.instances");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqConnectionCountEnabled() {
    Object metricInfo = config.get("mq.connection.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqManagerStatusEnabled() {
    Object metricInfo = config.get("mq.manager.status");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqHeartbeatEnabled() {
    Object metricInfo = config.get("mq.heartbeat");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqArchiveLogSizeEnabled() {
    Object metricInfo = config.get("mq.archive.log.size");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqManagerMaxActiveChannelsEnabled() {
    Object metricInfo = config.get("mq.manager.max.active.channels");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqManagerStatisticsIntervalEnabled() {
    Object metricInfo = config.get("mq.manager.statistics.interval");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqPublishCountEnabled() {
    Object metricInfo = config.get("mq.publish.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqSubscriptionCountEnabled() {
    Object metricInfo = config.get("mq.subscription.count");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqListenerStatusEnabled() {
    Object metricInfo = config.get("mq.listener.status");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqUnauthorizedEventEnabled() {
    Object metricInfo = config.get("mq.unauthorized.event");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

  public boolean isMqManagerMaxHandlesEnabled() {
    Object metricInfo = config.get("mq.manager.max.handles");
    if (!(metricInfo instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<?, ?>) metricInfo).get("enabled");
    if (enabled instanceof Boolean) {
      return (Boolean) ((Map<?, ?>) metricInfo).get("enabled");
    }
    return false;
  }

}