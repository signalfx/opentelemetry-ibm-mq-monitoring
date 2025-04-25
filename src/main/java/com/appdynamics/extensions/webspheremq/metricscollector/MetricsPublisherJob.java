package com.appdynamics.extensions.webspheremq.metricscollector;

import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * A simple job that just runs a MetricsPublisher one time.
 */
public class MetricsPublisherJob implements Runnable {

    public static final Logger logger = LoggerFactory.getLogger(MetricsPublisherJob.class);
    private final MetricsPublisher delegate;
    private final CountDownLatch latch;

    public MetricsPublisherJob(MetricsPublisher delegate, CountDownLatch latch) {
        this.delegate = delegate;
        this.latch = latch;
    }

    @Override
    public void run() {
        try {
            delegate.publishMetrics();
        } catch (TaskExecutionException e) {
            logger.error("Error executing " + delegate.getClass().getSimpleName(), e);
        } finally {
            latch.countDown();
        }
    }
}
