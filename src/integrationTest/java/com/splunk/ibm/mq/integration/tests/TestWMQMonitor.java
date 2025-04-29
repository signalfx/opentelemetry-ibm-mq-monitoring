/*
 * Copyright Splunk Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.splunk.ibm.mq.integration.tests;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.util.AssertUtils;
import com.appdynamics.extensions.webspheremq.WMQMonitor;
import com.appdynamics.extensions.webspheremq.WMQMonitorTask;
import com.appdynamics.extensions.webspheremq.config.QueueManager;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The TestWMQMonitor class extends the WMQMonitor class and provides a test implementation
 * of the WebSphere MQ monitoring functionality. It is intended for internal integration test purposes
 * and facilitates custom configuration through a test configuration file and a test metric write helper.
 */
class TestWMQMonitor extends WMQMonitor {

    private final MetricWriteHelper overrideHelper;

    TestWMQMonitor(String testConfigFile, MetricWriteHelper overrideHelper) {
        super(overrideHelper);
        this.overrideHelper = overrideHelper;
        Map<String, String> args = new HashMap<>();
        args.put("config-file", testConfigFile);
        initialize(args);
    }

    /**
     * Executes a test run for monitoring WebSphere MQ queue managers based on the provided configuration "testConfigFile".
     * <p>
     * The method retrieves "queueManagers" from the yml configuration file and uses a custom MetricWriteHelper if provided, initializes a TasksExecutionServiceProvider,
     * and executes the WMQMonitorTask
     */
    void testrun() {
        List<Map> queueManagers = (List<Map>) this.getContextConfiguration().getConfigYml().get("queueManagers");
        AssertUtils.assertNotNull(queueManagers, "The 'queueManagers' section in config.yml is not initialised");
        ObjectMapper mapper = new ObjectMapper();
        // we override this helper to pass in our opentelemetry helper instead.
        if (this.overrideHelper != null) {
            TasksExecutionServiceProvider tasksExecutionServiceProvider = new TasksExecutionServiceProvider(this, this.overrideHelper);

            for (Map queueManager : queueManagers) {
                QueueManager qManager = mapper.convertValue(queueManager, QueueManager.class);
                WMQMonitorTask wmqTask = new WMQMonitorTask(tasksExecutionServiceProvider, this.getContextConfiguration(), qManager);
                wmqTask.run();
            }
        }
    }
}
