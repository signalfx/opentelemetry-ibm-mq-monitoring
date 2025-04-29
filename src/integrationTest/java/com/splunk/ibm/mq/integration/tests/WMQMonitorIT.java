package com.splunk.ibm.mq.integration.tests;

import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.processor.ConfigProcessor;
import com.appdynamics.extensions.opentelemetry.OpenTelemetryMetricWriteHelper;
import com.appdynamics.extensions.util.PathResolver;
import com.appdynamics.extensions.webspheremq.WMQMonitor;
import com.appdynamics.extensions.yml.YmlReader;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import com.splunk.ibm.mq.integration.opentelemetry.TestResultMetricExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration Test for WMQMonitor
 */
class WMQMonitorIT {

    private static final Logger logger = LoggerFactory.getLogger(WMQMonitorIT.class);

    private File getInstallDirectory() {
        File installDir = PathResolver.resolveDirectory(AManagedMonitor.class);
        if (installDir == null) {
            throw new RuntimeException("The install directory cannot be null");
        } else {
            return installDir;
        }
    }

    private void constructConfigYml() {
        File configFile = this.resolvePath(path, this.installDir);
        logger.info("Loading the contextConfiguration from {}", configFile.getAbsolutePath());
        Map<String, ?> rootElem = YmlReader.readFromFileAsMap(configFile);
        if (rootElem == null) {
            logger.error("Unable to get data from the config file");
        } else {
            rootElem = ConfigProcessor.process(rootElem);
            this.configYml = rootElem;
            Boolean enabled = (Boolean)this.configYml.get("enabled");
            if (!Boolean.FALSE.equals(enabled)) {
                this.enabled = true;
                this.setMetricPrefix((String)this.configYml.get("metricPrefix"), this.defaultMetricPrefix);
            } else {
                this.enabled = false;
                logger.error("The contextConfiguration is not enabled {}", this.configYml);
            }

         //   this.context.initialize(this.aMonitorJob, this.getConfigYml(), this.getMetricPrefix());
        }
    }

    @BeforeEach
    void setup() {
        URL resource = getClass().getClassLoader().getResource("conf/test-config.yml");
        if (resource == null) {
            throw new IllegalArgumentException("file not found!");
        }

        File file = Paths.get(resource.toURI()).toFile();
        logger.info("Config file: {}", file.getAbsolutePath());
        String configFile = file.getAbsolutePath();
    }

    @Test
    void test_monitor_with_full_config() throws Exception {


        TestResultMetricExporter testExporter = new TestResultMetricExporter();
        WMQMonitor monitor = new WMQMonitor(new OpenTelemetryMetricWriteHelper(testExporter));
        TaskExecutionContext taskExecCtx = new TaskExecutionContext();

        try {
            Map<String, String> taskArguments = new HashMap<>();
            taskArguments.put("config-file", configFile);
            monitor.execute(taskArguments, taskExecCtx);
        } catch (TaskExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void test_wmqmonitortask() {
        TestResultMetricExporter testExporter = new TestResultMetricExporter();
        MetricWriteHelper metricWriteHelper = new OpenTelemetryMetricWriteHelper(testExporter);

        // WMQMonitorTask wmqTask = new WMQMonitorTask(tasksExecutionServiceProvider, this.getContextConfiguration(), qManager);
    }
}
