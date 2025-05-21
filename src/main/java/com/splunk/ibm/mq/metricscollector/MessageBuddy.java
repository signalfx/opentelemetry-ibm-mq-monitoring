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
package com.splunk.ibm.mq.metricscollector;

import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageBuddy {
  private static final Logger logger = LoggerFactory.getLogger(MessageBuddy.class);

  private MessageBuddy() {}

  static String channelName(PCFMessage message) throws PCFException {
    return message.getStringParameterValue(CMQCFC.MQCACH_CHANNEL_NAME).trim();
  }

  static String topicName(PCFMessage message) throws PCFException {
    return message.getStringParameterValue(CMQC.MQCA_TOPIC_STRING).trim();
  }

  public static String listenerName(PCFMessage message) throws PCFException {
    return message.getStringParameterValue(CMQCFC.MQCACH_LISTENER_NAME).trim();
  }

  public static String queueName(PCFMessage message) throws PCFException {
    return message.getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
  }

  @Nullable
  public static Integer getIntParameterValue(PCFMessage message, int param) {
    try {
      return message.getIntParameterValue(param);
    } catch (PCFException e) {
      logger.error("Error fetching parameter value " + param, e);
      return null;
    }
  }
}
