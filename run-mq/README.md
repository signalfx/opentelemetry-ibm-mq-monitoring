
# IBM MQ Docker image

Here is how to build it for Apple Silicon M1 ARM64 architecture for development :

https://community.ibm.com/community/user/integration/blogs/richard-coppen/2023/06/30/ibm-mq-9330-container-image-now-available-for-appl

After that, a docker image for MQ arm64 should show up in your local list of images.

Then, run the container with the volume

```bash
docker run --rm -it --name ibmmq \
    --publish 1414:1414 \
    --publish 9443:9443 \
    -e LICENSE=accept \
    -e MQ_QMGR_NAME=QM1 \
    -e MQ_ADMIN_USER=admin \
    -e MQ_ADMIN_PASSWORD=passw0rd \
    -e MQ_APP_USER=app \
    -e MQ_APP_PASSWORD=passw0rd \
    ibm-mqadvanced-server-dev:9.4.2.1-arm64
```

