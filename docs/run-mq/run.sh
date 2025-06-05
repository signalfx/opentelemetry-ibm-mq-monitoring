java \
   -Djavax.net.ssl.keyStore=key.jks \
   -Djavax.net.ssl.keyStorePassword=passw0rd \
   -Djavax.net.ssl.trustStore=key.jks \
   -Djavax.net.ssl.trustStorePassword=passw0rd \
   -cp ../build/lib/ibm-mq-monitoring-0.1.0-all.jar:../target/libs/com.ibm.mq.allclient.jar \
   com.splunk.ibm.mq.opentelemetry.Main \
   ../src/integrationTest/resources/conf/test-config.yml
