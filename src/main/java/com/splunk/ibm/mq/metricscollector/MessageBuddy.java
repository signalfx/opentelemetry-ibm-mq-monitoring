package com.splunk.ibm.mq.metricscollector;

import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;

public class MessageBuddy {

  private MessageBuddy(){}

  static String channelName(PCFMessage message) throws PCFException {
    return message.getStringParameterValue(CMQCFC.MQCACH_CHANNEL_NAME).trim();
  }

  static String topicName(PCFMessage message) throws PCFException {
    return message.getStringParameterValue(CMQC.MQCA_TOPIC_STRING).trim();
  }

}
