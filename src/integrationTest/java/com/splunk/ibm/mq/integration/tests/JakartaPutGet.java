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
package com.splunk.ibm.mq.integration.tests;

import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibm.mq.pcf.CMQC;
import com.ibm.msg.client.jakarta.jms.JmsConnectionFactory;
import com.ibm.msg.client.jakarta.jms.JmsFactoryFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import com.splunk.ibm.mq.WMQMonitorTask;
import com.splunk.ibm.mq.config.QueueManager;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This code was adapted from https://github.com/ibm-messaging/mq-dev-samples/.
 *
 * <p>A minimal and simple application for Point-to-point messaging.
 *
 * <p>Application makes use of fixed literals, any customisations will require re-compilation of
 * this source file. Application assumes that the named queue is empty prior to a run.
 *
 * <p>Notes:
 *
 * <p>API type: Jakarta API (JMS v3.0, simplified domain)
 *
 * <p>Messaging domain: Point-to-point
 *
 * <p>Provider type: IBM MQ
 *
 * <p>Connection mode: Client connection
 *
 * <p>JNDI in use: No
 */
public class JakartaPutGet {

  private static final Logger logger = LoggerFactory.getLogger(JakartaPutGet.class);

  public static void createQueue(QueueManager manager, String name) {
    MQQueueManager ibmQueueManager = WMQMonitorTask.connectToQueueManager(manager, null);
    PCFMessageAgent agent = WMQMonitorTask.initPCFMesageAgent(manager, ibmQueueManager);
    PCFMessage request = new PCFMessage(CMQCFC.MQCMD_CREATE_Q);
    request.addParameter(com.ibm.mq.constants.CMQC.MQCA_Q_NAME, name);
    request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL);
    try {
      agent.send(request);
    } catch (PCFException e) {
      if (e.reasonCode == CMQCFC.MQRCCF_OBJECT_ALREADY_EXISTS) {
        return;
      }
      throw new RuntimeException(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @param manager Queue manager configuration
   * @param queueName Queue that the application uses to put and get messages to and from
   * @param numberOfMessages Number of messages to send
   * @param sleepIntervalMs Sleep interval in ms
   */
  public static void runPutGet(
      QueueManager manager, String queueName, int numberOfMessages, int sleepIntervalMs) {

    createQueue(manager, queueName);
    JMSContext context = null;
    JMSContext senderContext = null;
    try {
      // Create a connection factory
      JmsFactoryFactory ff = JmsFactoryFactory.getInstance(WMQConstants.JAKARTA_WMQ_PROVIDER);
      JmsConnectionFactory cf = ff.createConnectionFactory();

      // Set the properties
      cf.setStringProperty(WMQConstants.WMQ_HOST_NAME, manager.getHost());
      cf.setIntProperty(WMQConstants.WMQ_PORT, manager.getPort());
      cf.setStringProperty(WMQConstants.WMQ_CHANNEL, manager.getChannelName());
      cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
      cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, manager.getName());
      cf.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, "JakartaPutGet (Jakarta)");
      cf.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
      cf.setStringProperty(WMQConstants.USERID, manager.getUsername());
      cf.setStringProperty(WMQConstants.PASSWORD, manager.getPassword());
      // cf.setStringProperty(WMQConstants.WMQ_SSL_CIPHER_SUITE, "*TLS12ORHIGHER");
      // cf.setIntProperty(MQConstants.CERTIFICATE_VALIDATION_POLICY,
      // MQConstants.MQ_CERT_VAL_POLICY_NONE);

      // Create Jakarta objects
      context = cf.createContext();
      Destination destination = context.createQueue("queue:///" + queueName);

      JMSConsumer consumer = context.createConsumer(destination);
      consumer.setMessageListener(message -> {});

      senderContext = cf.createContext();
      Destination senderDestination = senderContext.createQueue("queue:///" + queueName);

      for (int i = 0; i < numberOfMessages; i++) {
        long uniqueNumber = System.currentTimeMillis() % 1000;
        TextMessage message =
            senderContext.createTextMessage("Your lucky number today is " + uniqueNumber);
        message.setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, 37);
        JMSProducer producer = senderContext.createProducer();
        producer.send(senderDestination, message);

        Thread.sleep(sleepIntervalMs);
      }

    } catch (JMSException | InterruptedException jmsex) {
      throw new RuntimeException(jmsex);
    } finally {
      if (context != null) {
        context.close();
      }
      if (senderContext != null) {
        senderContext.close();
      }
    }
  }
}
