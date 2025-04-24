java \
   -Djavax.net.ssl.keyStore=key.jks \
   -Djavax.net.ssl.keyStorePassword=passw0rd \
   -Djavax.net.ssl.trustStore=key.jks \
   -Djavax.net.ssl.trustStorePassword=passw0rd \
   -cp ../target/ibm-mq-monitoring-extension-opentelemetry.jar:../target/libs/com.ibm.mq.allclient.jar \
   com.appdynamics.extensions.opentelemetry.Main \
   ./config.yml
