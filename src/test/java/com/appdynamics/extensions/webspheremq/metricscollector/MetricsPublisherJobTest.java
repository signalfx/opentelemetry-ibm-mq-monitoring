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
package com.appdynamics.extensions.webspheremq.metricscollector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

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
    MetricsPublisherJob job =
        new MetricsPublisherJob(
            () -> {
              publishCalled.set(true);
              throw new IllegalStateException("Boom");
            },
            latch);

    assertThrows(IllegalStateException.class, () -> job.run());

    assertThat(publishCalled).isTrue();
    assertThat(latch.getCount()).isEqualTo(0);
  }

  @Test
  void testTaskExecutionException() {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean publishCalled = new AtomicBoolean(false);
    MetricsPublisherJob job =
        new MetricsPublisherJob(
            () -> {
              publishCalled.set(true);
              throw new TaskExecutionException("Boom");
            },
            latch);
    job.run();
    assertThat(publishCalled).isTrue();
    assertThat(latch.getCount()).isEqualTo(0);
  }
}
