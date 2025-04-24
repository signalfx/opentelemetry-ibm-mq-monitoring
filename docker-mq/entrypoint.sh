#!/bin/bash

# Start MQ normally
/opt/mqm/bin/runmqdevserver &

# Wait for queue manager to fully start
until echo "DISPLAY QMSTATUS" | runmqsc QM1 > /dev/null 2>&1; do
  echo "Waiting for QM1 to start..."
  sleep 2
done

# Run MQSC script to configure user permissions
echo "Running MQSC config script..."
runmqsc QM1 < /etc/mqm/config.mqsc

# Wait for MQ to keep running
wait
