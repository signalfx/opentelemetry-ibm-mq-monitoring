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

import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A simple job that just runs a MetricsPublisher one time. */
public final class MetricsPublisherJob implements Runnable {

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
