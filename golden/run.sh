#!/bin/bash

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

java -cp $SCRIPT_DIR/../target/ibm-mq-monitoring-extension-opentelemetry.jar:$SCRIPT_DIR/../target/libs/com.ibm.mq.allclient.jar \
  -Dotel.logs.exporter=none -Dotel.traces.exporter=none \
  com.splunk.ibm.mq.opentelemetry.Main $SCRIPT_DIR/config.yml
