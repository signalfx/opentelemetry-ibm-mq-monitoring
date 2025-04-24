FROM ibm-mqadvanced-server-dev:9.4.2.0-arm64

# Switch to root to add user and set password
USER root

# Create user 'app' with password
RUN useradd app && \
    echo "app:passw0rd" | chpasswd

# Grant MQ permissions (done at runtime using an MQSC script)
COPY config.mqsc /etc/mqm/config.mqsc

# Set entrypoint to run MQSC commands after QM starts
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Switch back to mqm (the default user that runs MQ)
USER 1001

CMD ["/entrypoint.sh"]
