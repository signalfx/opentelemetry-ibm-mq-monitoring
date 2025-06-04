# Capturing logs as metrics

This example shows how to set up the OpenTelemetry Collector to read and tail the log file, and create metrics based on the event code associated with the log.

## Running the example

This example runs by executing `docker compose up` in this folder.

It reads the `log.txt` file, a typical extract from MQ.

It parses the log entries and turn them into metrics.

The example exposes the final metrics as debug statements.

2 log entries match to this metric:
```
2025-05-16T22:20:29.913Z CWWKZ0001I: Application com.ibm.mq.rest started in 1.495 seconds.
2025-05-16T22:20:29.956Z CWWKZ0001I: Application com.ibm.mq.console started in 1.538 seconds.
```

```
collector-1  | Metric #0
collector-1  | Descriptor:
collector-1  |      -> Name: mq.log.codes
collector-1  |      -> Description: 
collector-1  |      -> Unit: 
collector-1  |      -> DataType: Sum
collector-1  |      -> IsMonotonic: true
collector-1  |      -> AggregationTemporality: Delta
collector-1  | NumberDataPoints #0
collector-1  | Data point attributes:
collector-1  |      -> code: Str(CWWKZ0001I)
collector-1  | StartTimestamp: 1970-01-01 00:00:00 +0000 UTC
collector-1  | Timestamp: 2025-05-17 05:11:16.937236967 +0000 UTC
collector-1  | Value: 2
```

# Detailed configuration

## Reading textual format

The log file is tailed by the filelog receiver.

```yaml
  filelog:
    include:
    # Change to the path to the log file used by MQ.
     - /tmp/log.txt
    start_at: end
    operators:
      - type: regex_parser
        parse_from: body
        regex: '^(?P<timestamp>\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d\.\d\d\d)Z\s(?P<code>.*?):.*'
        on_error: drop_quiet
      - type: time_parser
        parse_from: attributes.timestamp
        layout_type: strptime
        layout: "%Y-%m-%dT%H:%M:%S.%L"
        on_error: drop_quiet
```

## JSON format

As of 9.4, logs can be emitted in JSON format.

This example also shows how to read the log entries formatted as JSON.

```yaml
  filelog/json:
    include:
      # Change to the path to the log file used by MQ.
      - /tmp/log.json
    start_at: end
    operators:
      - type: json_parser
        parse_from: body
        parse_to: body.parsed
        on_error: drop_quiet
      - type: time_parser
        parse_from: body.parsed.ibm_datetime
        layout_type: strptime
        layout: "%Y-%m-%dT%H:%M:%S.%L%z"
        on_error: drop_quiet
      - type: copy
        from: body.parsed.ibm_messageId
        to: attributes.code
```

## Long text format

This legacy format has a longer, multiline error log, where each entry is delimited with a dash line.

This format is richer and allows us to determine the host and queue manager as well.

This example also shows how to read those log entries:

```yaml
  filelog/dashes:
    include:
      # Change to the path to the log file used by MQ.
      - /tmp/error_log.txt
    # Change to end for production uses.
    start_at: beginning
    multiline:
      line_start_pattern: "^-----"
    operators:
      - type: regex_parser
        parse_from: body
        regex: '(?m)Host\((?P<host_name>.*?)\)(.|\n)*QMgr\((?P<queue_manager>.*?)\)(.|\n)*Time\((?P<timestamp>.*?)\)(.|\n)*\n(?P<code>\w+):(.|\n)*EXPLANATION'
        on_error: drop_quiet
      - type: time_parser
        parse_from: attributes.timestamp
        layout_type: strptime
        # 2025-05-30T16:52:04.227Z
        layout: "%Y-%m-%dT%H:%M:%S.%L%z"
        on_error: drop_quiet
      - type: move
        from: attributes.host_name
        to: attributes["host.name"]
      - type: move
        from: attributes.queue_manager
        to: attributes["queue.manager"]
```

## Transforming logs into metrics

Now that the logs are parsed, we want to count occurrences of the `code` attribute values.

We use the `countconnector` for this.

```yaml
  count:
    logs:
      mq.log.codes: # the name of the metric
        attributes:
          - key: code # Group by the code attribute.
          # - key: queue.manager # uncomment if using the extended log format.
          # - key: host.name
```

Connectors work as both exporters and receivers across signals.

```yaml
  pipelines:
    logs:
      receivers:
        # Log sources
        - filelog
        - filelog/json
      exporters:
        - count
    metrics:
      receivers:
        - count
      exporters:
        # Metric export
        - debug
```

