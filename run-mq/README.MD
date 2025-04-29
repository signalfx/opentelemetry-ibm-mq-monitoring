ibm MQ Docker image
Here is how to build it for Apple Silicon M1 ARM64 architecture for development :

      https://community.ibm.com/community/user/integration/blogs/richard-coppen/2023/06/30/ibm-mq-9330-container-image-now-available-for-appl

After that, a docker image for MQ arm64 should show up as an image on your docker



To run a container of the image, first create a volume.

      docker volume create qm1data

Then, run the container with the volume

      docker run --name ibmmq  --env LICENSE=accept --env MQ_QMGR_NAME=QM1 --volume qm1data:/mnt/mqm --publish 1414:1414 --publish 9443:9443 --detach --env MQ_ADMIN_USER=admin  --env MQ_APP_PASSWORD=passw0rd -d ibm-mqadvanced-server-dev:9.4.2.0-arm64

