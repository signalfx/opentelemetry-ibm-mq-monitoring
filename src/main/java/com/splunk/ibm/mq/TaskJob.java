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
package com.splunk.ibm.mq;

import java.util.concurrent.Callable;

/** This class just runs a delegate and times it and logs any exceptions that might be thrown. */
public final class TaskJob implements Callable<Void> {

  private final String name;
  private final Runnable task;

  public TaskJob(String name, Runnable task) {
    this.name = name;
    this.task = task;
  }

  @Override
  public Void call() {
    try {
      long startTime = System.currentTimeMillis();
      task.run();
      long diffTime = System.currentTimeMillis() - startTime;
      if (diffTime > 60000L) {
        WMQMonitor.logger.warn("{} Task took {} ms to complete", name, diffTime);
      } else {
        WMQMonitor.logger.debug("{} Task took {} ms to complete", name, diffTime);
      }
    } catch (Exception e) {
      WMQMonitor.logger.error("Error while running task name = " + name, e);
    }
    return null;
  }
}
