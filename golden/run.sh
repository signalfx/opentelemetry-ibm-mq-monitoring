#!/bin/bash

set -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
java -cp $SCRIPT_DIR/../build/libs/ibm-mq-monitoring-0.1.0-all.jar:$SCRIPT_DIR/../build/libs/com.ibm.mq.allclient.jar \
  -Dotel.logs.exporter=none -Dotel.traces.exporter=none \
  com.splunk.ibm.mq.opentelemetry.Main $SCRIPT_DIR/config.yml
