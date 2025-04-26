package com.appdynamics.extensions.webspheremq.metricscollector;

import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsPublisherJobTest {
    @Test
    void testSuccess() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean publishCalled = new AtomicBoolean(false);
        MetricsPublisherJob job = new MetricsPublisherJob(() -> publishCalled.set(true), latch);
        job.run();
        assertThat(publishCalled).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test
    void otherExceptionsLeak() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean publishCalled = new AtomicBoolean(false);
        MetricsPublisherJob job = new MetricsPublisherJob(() -> {
            publishCalled.set(true);
            throw new IllegalStateException("Boom");
        }, latch);

        assertThrows(IllegalStateException.class, () -> job.run());

        assertThat(publishCalled).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test
    void testTaskExecutionException() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean publishCalled = new AtomicBoolean(false);
        MetricsPublisherJob job = new MetricsPublisherJob(() -> {
            publishCalled.set(true);
            throw new TaskExecutionException("Boom");
        }, latch);
        job.run();
        assertThat(publishCalled).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
    }

}