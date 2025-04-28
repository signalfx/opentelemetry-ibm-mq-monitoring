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
package com.appdynamics.extensions.opentelemetry;

import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.util.AssertUtils;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MQOtelTranslator {

    public static final Logger logger = LoggerFactory.getLogger(MQOtelTranslator.class);

    private static final long SCRAPE_PERIOD_NS = 60L * 1000 * 1000 * 1000;
    private static final Splitter PIPE_SPLITTER = Splitter.on('|').trimResults();

    private static class Mapping {
        private final Map<String, String> nameMappings;
        private final Function<List<String>, Attributes> attributeMappingFunction;

        Mapping(Map<String, String> nameMappings, Function<List<String>, Attributes> attributeMappingFunction) {
            this.nameMappings = nameMappings;
            this.attributeMappingFunction = attributeMappingFunction;
        }
    }

    private static final Mapping queueMgrMetricNameMappings = new Mapping(new HashMap<String, String>() {{
        put("Status", "mq.manager.status");
        put("ConnectionCount", "mq.manager.connection.count");
    }}, segments -> {
        AttributesBuilder builder = Attributes.builder();
        builder.put("queue.manager", segments.get(segments.size() - 2));
        return builder.build();
    });

    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|UncommittedMsgs
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.1|local|UncommittedMsgs
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.2|local|UncommittedMsgs
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.3|local|UncommittedMsgs
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|Max Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|Current Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|Open Input Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|Open Output Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.1|local|Max Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.1|local|Current Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.1|local|Open Input Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.1|local|Open Output Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.2|local|Max Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.2|local|Current Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.2|local|Open Input Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.2|local|Open Output Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.3|local|Max Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.3|local|Current Queue Depth
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.3|local|Open Input Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.QUEUE.3|local|Open Output Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|OnQTime_1
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|OnQTime_2
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|Current maximum queue file size
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Queues|DEV.DEAD.LETTER.QUEUE|local|Current queue file size
    private static final Mapping queueMetricNameMappings = new Mapping(new HashMap<String, String>() {{
        put("Max Queue Depth", "mq.max.queue.depth");
        put("Current Queue Depth", "mq.queue.depth");
        put("Open Input Count", "mq.open.input.count");
        put("Open Output Count", "mq.open.output.count");
        put("OldestMsgAge", "mq.oldest.msg.age");
        put("OnQTime", "mq.onqtime");
        put("OnQTime_1", "mq.onqtime.1");
        put("OnQTime_2", "mq.onqtime.2");
        put("UncommittedMsgs", "mq.uncommitted.msgs");
        put("HighQDepth", "mq.high.queue.depth");
        put("MsgDeqCount", "mq.message.deq.count");
        put("MsgEnqCount", "mq.message.enq.count");
        put("Current maximum queue file size", "mq.current.max.queue.filesize");
        put("Current queue file size", "mq.current.queue.filesize");
        put("Uncommitted Messages", "mq.uncommitted.messages");
        put("Service Interval Event", "mq.service.interval.event");
        put("Service Interval", "mq.service.interval");
    }},
            segments -> {
                AttributesBuilder builder = Attributes.builder();
                builder.put("queue.manager", segments.get(segments.size() - 5));
                builder.put("queue.name", segments.get(segments.size() - 3));
                builder.put("queue.type", segments.get(segments.size() - 2));
                return builder.build();
            });

    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|CLOUD.ADMIN.SVRCONN|Status
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|CLOUD.ADMIN.SVRCONN|Messages
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|CLOUD.ADMIN.SVRCONN|Buffers Sent
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|CLOUD.ADMIN.SVRCONN|Byte Sent
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|CLOUD.ADMIN.SVRCONN|Buffers Received
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|CLOUD.ADMIN.SVRCONN|Byte Received
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Listeners|CLOUD.LISTENER.TCP|Status
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|CLOUD.ADMIN.SVRCONN|Current Sharing Conversations
    private static final Mapping channelMetricNameMappings = new Mapping(new HashMap<String, String>() {{
        put("Messages", "mq.messages");
        put("Status", "mq.status");
        put("Byte Sent", "mq.byte.sent");
        put("Byte Received", "mq.byte.received");
        put("Buffers Sent", "mq.buffers.sent");
        put("Buffers Received", "mq.buffers.received");
        put("Current Sharing Conversations", "mq.current.sharing.conversations");
        put("Max Sharing Conversations", "mq.max.sharing.conversations");
        put("Max Instances", "mq.max.instances");
        put("Max Instances per Client", "mq.instances.per.client");
        put("Message Retry Count", "mq.message.retry.count");
        put("Message Received Count", "mq.message.received.count");
        put("Message Sent", "mq.message.sent.count");
    }}, segments -> {
        AttributesBuilder builder = Attributes.builder();
        builder.put("queue.manager", segments.get(segments.size() - 4));
        builder.put("channel.name", segments.get(segments.size() - 2));
        return builder.build();
    });

    private static final Mapping listenerMetricNameMappings = new Mapping(new HashMap<String, String>() {{
        put("Status", "mq.status");
    }}, segments -> {
        AttributesBuilder builder = Attributes.builder();
        builder.put("queue.manager", segments.get(segments.size() - 4));
        builder.put("channel.name", segments.get(segments.size() - 2));
        return builder.build();
    });

    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Topics|dev|Publish Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Topics|dev|Subscription Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Topics|dev/|Publish Count
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Topics|dev/|Subscription Count
    private static final Mapping topicMetricNameMappings = new Mapping(new HashMap<String, String>() {{
        put("Publish Count", "mq.publish.count");
        put("Subscription Count", "mq.subscription.count");
    }}, segments -> {
        AttributesBuilder builder = Attributes.builder();
        builder.put("queue.manager", segments.get(segments.size() - 4));
        builder.put("topic.name", segments.get(segments.size() - 2));
        return builder.build();
    });

    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Channels|ActiveChannelsCount
    private static final Mapping channelsNameMapping = new Mapping(new HashMap<String, String>() {{
        put("ActiveChannelsCount", "mq.active.channels");
    }},
            segments -> {
                AttributesBuilder builder = Attributes.builder();
                builder.put("queue.manager", segments.get(segments.size() - 3));
                return builder.build();
            });

    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|Status
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|ConnectionCount
    //Server|Component:atoulme|Custom Metrics|WebsphereMQ|mq1|HeartBeat
    private static final Mapping websphereMQNameMappings = new Mapping(new HashMap<String, String>() {{
        put("Status", "mq.status");
        put("ConnectionCount", "mq.connection.count");
        put("HeartBeat", "mq.heartbeat");
        put("Restart Log Size", "mq.restart.log.size");
        put("Reusable Log Size", "mq.reusable.log.size");
        put("Archive Log Size", "mq.archive.log.size");
        put("Statistics Interval", "mq.manager.statistics.interval");
    }},
            segments -> {
                AttributesBuilder builder = Attributes.builder();
                builder.put("queue.manager", segments.get(segments.size() - 2));
                return builder.build();
            });

    private static final Map<String, Mapping> nameMappings = new HashMap<String, Mapping>() {{
        put("Queue Manager", queueMgrMetricNameMappings);
        put("Queues", queueMetricNameMappings);
        put("Channels", channelMetricNameMappings);
        put("ChannelsGlobal", channelsNameMapping);
        put("Listeners", listenerMetricNameMappings);
        put("Topics", topicMetricNameMappings);
        put("WebsphereMQ", websphereMQNameMappings);
    }};

    public MQOtelTranslator() {
    }

    public Collection<MetricData> translate(List<Metric> metricList) {
        AssertUtils.assertNotNull(metricList, "Metrics List cannot be null");
        Resource res = Resource.empty();
        InstrumentationScopeInfo scopeInfo = InstrumentationScopeInfo.create("websphere/mq");
        List<MetricData> metrics = new ArrayList<>();
        for (Metric metric : metricList) {
            Instant now = Instant.now();
            long endTime = now.getEpochSecond() * 1_000_000_000 + now.getNano();
            long startingTime = endTime - SCRAPE_PERIOD_NS;

            ConversionResult result = convertMetricInfo(metric);
            if (result == null) {
                continue;
            }

            LongPointData pointData = ImmutableLongPointData.create(startingTime, endTime, result.attributes, Long.parseLong(metric.getMetricValue()));
            MetricData otelMetricData = ImmutableMetricData.createLongGauge(
                    res,
                    scopeInfo,
                    result.metricName,
                    metric.getMetricPath(),
                    "1",
                    ImmutableGaugeData.create(Collections.singleton(pointData)));

            metrics.add(otelMetricData);
            logger.debug("\nTranslating Metric: {} \nto Otel: {}\n", metric, otelMetricData);
        }
        return metrics;
    }

    ConversionResult convertMetricInfo(Metric metric) {
        String metricPath = metric.getMetricPath();
        List<String> splitList = new ArrayList<>(PIPE_SPLITTER.splitToList(metricPath));
        Mapping mappings;
        if (metricPath.endsWith("Channels|ActiveChannelsCount")) {
            mappings = nameMappings.get("ChannelsGlobal");
        } else {
            mappings = nameMappings.get(splitList.get(splitList.size() - 3));
            if (mappings == null) {
                mappings = nameMappings.get(splitList.get(splitList.size() - 4));
            }
        }

        if (mappings == null) {
            logger.warn("No mapping found for {} with segment {}", metricPath, splitList.get(splitList.size() - 3));
            return null;
        }
        String metricName = mappings.nameMappings.get(splitList.get(splitList.size() - 1));
        if (metricName == null) {
            logger.warn("Unknown metric path {} for mappings {} with segment {} (known keys {})", metricPath, splitList.get(splitList.size() - 3), splitList.get(splitList.size() - 1), mappings.nameMappings.keySet().toArray());
            return null;
        }

        Attributes attr = mappings.attributeMappingFunction.apply(splitList);

        return new ConversionResult(metricName, attr);
    }

}


class ConversionResult {

    String metricName;
    Attributes attributes;

    ConversionResult(String name, Attributes attributes) {
        this.metricName = name;
        this.attributes = attributes;
    }
}