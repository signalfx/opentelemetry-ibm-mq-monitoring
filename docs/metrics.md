# Produced Metrics


## Metric `mq.message.retry.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.message.retry.count` | Gauge | `{messages}` | Number of message retries | Unknown stability. |


### `mq.message.retry.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.status`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.status` | Gauge | `1` | Channel status | Unknown stability. |


### `mq.status` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.max.sharing.conversations`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.max.sharing.conversations` | Gauge | `{conversations}` | Maximum number of conversations permitted on this channel instance. | Unknown stability. |


### `mq.max.sharing.conversations` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.current.sharing.conversations`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.current.sharing.conversations` | Gauge | `{conversations}` | Current number of conversations permitted on this channel instance. | Unknown stability. |


### `mq.current.sharing.conversations` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.byte.received`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.byte.received` | Gauge | `{bytes}` | Number of bytes received | Unknown stability. |


### `mq.byte.received` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.byte.sent`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.byte.sent` | Gauge | `{bytes}` | Number of bytes sent | Unknown stability. |


### `mq.byte.sent` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.buffers.received`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.buffers.received` | Gauge | `{buffers}` | Buffers received | Unknown stability. |


### `mq.buffers.received` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.buffers.sent`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.buffers.sent` | Gauge | `{buffers}` | Buffers sent | Unknown stability. |


### `mq.buffers.sent` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.message.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.message.count` | Gauge | `{messages}` | Message count | Unknown stability. |


### `mq.message.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.start.time` | int | The job name | `1748462702` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `job.name` | string | The job name | `0000074900000003` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.open.input.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.open.input.count` | Gauge | `{applications}` | Count of applications sending messages to the queue | Unknown stability. |


### `mq.open.input.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.open.output.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.open.output.count` | Gauge | `{applications}` | Count of applications consuming messages from the queue | Unknown stability. |


### `mq.open.output.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.high.queue.depth`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.high.queue.depth` | Gauge | `{percent}` | The current high queue depth | Unknown stability. |


### `mq.high.queue.depth` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.service.interval`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.service.interval` | Gauge | `{percent}` | The queue service interval | Unknown stability. |


### `mq.service.interval` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.uncommitted.messages`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.uncommitted.messages` | Gauge | `{messages}` | Number of uncommitted messages | Unknown stability. |


### `mq.uncommitted.messages` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.oldest.msg.age`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.oldest.msg.age` | Gauge | `microseconds` | Queue message oldest age | Unknown stability. |


### `mq.oldest.msg.age` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.current.max.queue.filesize`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.current.max.queue.filesize` | Gauge | `mib` | Current maximum queue file size | Unknown stability. |


### `mq.current.max.queue.filesize` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.current.queue.filesize`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.current.queue.filesize` | Gauge | `mib` | Current queue file size | Unknown stability. |


### `mq.current.queue.filesize` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.instances.per.client`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.instances.per.client` | Gauge | `{instances}` | Instances per client | Unknown stability. |


### `mq.instances.per.client` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.message.deq.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.message.deq.count` | Gauge | `{messages}` | Message dequeue count | Unknown stability. |


### `mq.message.deq.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.message.enq.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.message.enq.count` | Gauge | `{messages}` | Message enqueue count | Unknown stability. |


### `mq.message.enq.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.queue.depth`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.queue.depth` | Gauge | `{messages}` | Current queue depth | Unknown stability. |


### `mq.queue.depth` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.service.interval.event`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.service.interval.event` | Gauge | `1` | Queue service interval event | Unknown stability. |


### `mq.service.interval.event` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.reusable.log.size`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.reusable.log.size` | Gauge | `mib` | The amount of space occupied, in megabytes, by log extents available to be reused. | Unknown stability. |


### `mq.reusable.log.size` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.manager.active.channels`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.manager.active.channels` | Gauge | `{channels}` | The queue manager active maximum channels limit | Unknown stability. |


### `mq.manager.active.channels` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.restart.log.size`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.restart.log.size` | Gauge | `mib` | Size of the log data required for restart recovery in megabytes. | Unknown stability. |


### `mq.restart.log.size` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.max.queue.depth`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.max.queue.depth` | Gauge | `{messages}` | Maximum queue depth | Unknown stability. |


### `mq.max.queue.depth` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.onqtime.1`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.onqtime.1` | Gauge | `microseconds` | Amount of time, in microseconds, that a message spent on the queue, over a short period | Unknown stability. |


### `mq.onqtime.1` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.onqtime.2`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.onqtime.2` | Gauge | `microseconds` | Amount of time, in microseconds, that a message spent on the queue, over a longer period | Unknown stability. |


### `mq.onqtime.2` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `queue.name` | string | The queue name | `DEV.DEAD.LETTER.QUEUE` | `Required` | Unknown stability. |
| `queue.type` | string | The queue type | `local-normal` | `Required` | Unknown stability. |



## Metric `mq.message.received.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.message.received.count` | Gauge | `{messages}` | Number of messages received | Unknown stability. |


### `mq.message.received.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.message.sent.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.message.sent.count` | Gauge | `{messages}` | Number of messages sent | Unknown stability. |


### `mq.message.sent.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.max.instances`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.max.instances` | Gauge | `{instances}` | Max channel instances | Unknown stability. |


### `mq.max.instances` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `channel.name` | string | The name of the channel | `DEV.ADMIN.SVRCONN` | `Required` | Unknown stability. |
| `channel.type` | string | The type of the channel | `server-connection`; `cluster-receiver`; `amqp` | `Required` | Unknown stability. |
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.connection.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.connection.count` | Gauge | `{connections}` | Active connections count | Unknown stability. |


### `mq.connection.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.manager.status`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.manager.status` | Gauge | `1` | Queue manager status | Unknown stability. |


### `mq.manager.status` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.heartbeat`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.heartbeat` | Gauge | `1` | Queue manager heartbeat | Unknown stability. |


### `mq.heartbeat` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.archive.log.size`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.archive.log.size` | Gauge | `mib` | Queue manager archive log size | Unknown stability. |


### `mq.archive.log.size` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.manager.max.active.channels`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.manager.max.active.channels` | Gauge | `{channels}` | Queue manager max active channels | Unknown stability. |


### `mq.manager.max.active.channels` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.manager.statistics.interval`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.manager.statistics.interval` | Gauge | `1` | Queue manager statistics interval | Unknown stability. |


### `mq.manager.statistics.interval` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |



## Metric `mq.publish.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.publish.count` | Gauge | `{publications}` | Topic publication count | Unknown stability. |


### `mq.publish.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `topic.name` | string | The name of the topic | `dev/` | `Required` | Unknown stability. |



## Metric `mq.subscription.count`

| Name     | Instrument Type | Unit (UCUM) | Description    | Stability |
| -------- | --------------- | ----------- | -------------- | --------- |
| `mq.subscription.count` | Gauge | `{subscriptions}` | Topic subscription count | Unknown stability. |


### `mq.subscription.count` Attributes

| Attribute  | Type | Description  | Examples  | [Requirement Level](https://opentelemetry.io/docs/specs/semconv/general/attribute-requirement-level/) | Stability |
|---|---|---|---|---|---|
| `queue.manager` | string | The name of the queue manager | `MQ1` | `Required` | Unknown stability. |
| `topic.name` | string | The name of the topic | `dev/` | `Required` | Unknown stability. |


