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

import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/** A miserable hack to encapsulate the state that is shared between queue collectors */
public final class QueueCollectorSharedState {

  private final ConcurrentHashMap<String, String> queueNameToType = new ConcurrentHashMap<>();

  private static final QueueCollectorSharedState INSTANCE = new QueueCollectorSharedState();

  public static QueueCollectorSharedState getInstance() {
    return INSTANCE;
  }

  private QueueCollectorSharedState() {}

  public void putQueueType(String name, String value) {
    queueNameToType.put(name, value);
  }

  @Nullable
  public String getType(String name) {
    return queueNameToType.get(name);
  }

  // Only exists for testing and should not normally ever be called.
  void resetForTest() {
    queueNameToType.clear();
  }
}
