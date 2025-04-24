docker run -p 4317:4317 -v /$(pwd)/otelconf.yaml:/tmp/otelconf.yaml --rm -it quay.io/signalfx/splunk-otel-collector --config /tmp/otelconf.yaml
