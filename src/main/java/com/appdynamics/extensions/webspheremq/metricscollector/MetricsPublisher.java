package com.appdynamics.extensions.webspheremq.metricscollector;

import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

public interface MetricsPublisher {

    void publishMetrics() throws TaskExecutionException;

}
