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

import static com.ibm.mq.constants.CMQCFC.MQIACH_BUFFERS_RECEIVED;
import static com.ibm.mq.constants.CMQCFC.MQIACH_BUFFERS_SENT;
import static com.ibm.mq.constants.CMQCFC.MQIACH_BYTES_RECEIVED;
import static com.ibm.mq.constants.CMQCFC.MQIACH_BYTES_SENT;
import static com.ibm.mq.constants.CMQCFC.MQIACH_CHANNEL_STATUS;
import static com.ibm.mq.constants.CMQCFC.MQIACH_CURRENT_SHARING_CONVS;
import static com.ibm.mq.constants.CMQCFC.MQIACH_MAX_INSTANCES;
import static com.ibm.mq.constants.CMQCFC.MQIACH_MAX_INSTS_PER_CLIENT;
import static com.ibm.mq.constants.CMQCFC.MQIACH_MAX_SHARING_CONVS;
import static com.ibm.mq.constants.CMQCFC.MQIACH_MR_COUNT;
import static com.ibm.mq.constants.CMQCFC.MQIACH_MSGS;
import static com.ibm.mq.constants.CMQCFC.MQIACH_MSGS_RECEIVED;
import static com.ibm.mq.constants.CMQCFC.MQIACH_MSGS_SENT;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

class ChannelMetrics {

  public static final String OTEL_IBM_MQ = "otel/ibm.mq";
  private final Map<Integer, LongGauge> instruments;
  private final LongGauge activeChannels;

  private ChannelMetrics(Map<Integer, LongGauge> instruments, LongGauge activeChannels) {
    this.instruments = instruments;
    this.activeChannels = activeChannels;
  }

  static ChannelMetrics create() {
    OpenTelemetry otel = GlobalOpenTelemetry.get();
    Meter meter = otel.getMeter(OTEL_IBM_MQ);
    Map<Integer, LongGauge> instruments = new HashMap<>();
    instruments.put(MQIACH_MSGS, makeGauge(meter, "ibm.mq.messages"));
    instruments.put(MQIACH_CHANNEL_STATUS, makeGauge(meter, "ibm.mq.status"));
    instruments.put(MQIACH_BYTES_SENT, makeGauge(meter, "ibm.mq.bytes.sent"));
    instruments.put(MQIACH_BYTES_RECEIVED, makeGauge(meter, "ibm.mq.bytes.received"));
    instruments.put(MQIACH_BUFFERS_SENT, makeGauge(meter, "ibm.mq.buffers.sent"));
    instruments.put(MQIACH_BUFFERS_RECEIVED, makeGauge(meter, "ibm.mq.buffers.received"));
    instruments.put(
        MQIACH_CURRENT_SHARING_CONVS, makeGauge(meter, "ibm.mq.current.sharing.conversations"));
    instruments.put(MQIACH_MAX_SHARING_CONVS, makeGauge(meter, "ibm.mq.sharing.conversations.max"));
    instruments.put(MQIACH_MAX_INSTANCES, makeGauge(meter, "ibm.mq.instances.max"));
    instruments.put(
        MQIACH_MAX_INSTS_PER_CLIENT, makeGauge(meter, "ibm.mq.instances.per_client.max"));
    instruments.put(
        MQIACH_MR_COUNT,
        makeGauge(meter, "ibm.mq.message.retry.count")); // TODO: Should be a counter?
    instruments.put(
        MQIACH_MSGS_SENT,
        makeGauge(meter, "ibm.mq.message.sent.count")); // TODO: Should be a counter?
    instruments.put(
        MQIACH_MSGS_RECEIVED,
        makeGauge(meter, "ibm.mq.message.received.count")); // TODO: Should be a counter?
    LongGauge activeChannels = makeGauge(meter, "ibm.mq.active.channels");
    return new ChannelMetrics(instruments, activeChannels);
  }

  private static LongGauge makeGauge(Meter meter, String name) {
    return meter.gaugeBuilder(name).ofLongs().build();
  }

  // TODO: This could be used elsewhere
  void setAll(Function<Integer,Integer> valueGetter) {
    for (Entry<Integer, LongGauge> entry : instruments.entrySet()) {
      Integer constant = entry.getKey();
      LongGauge gauge = entry.getValue();
      Integer value = valueGetter.apply(constant);
      if(value != null){
        gauge.set(value);
      }
    }
  }

  public void setActiveChannels(int count) {
    activeChannels.set(count);
  }
}
