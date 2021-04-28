#!/bin/bash
sudo docker rmi handmades_mongo:latest
sudo docker build -t handmades_mongo_tmp -f DockerfileMongodb .
sudo docker create --name mongo_handmades_cntnr handmades_mongo_tmp:latest --replSet rs0
sudo docker commit mongo_handmades_cntnr handmades_mongo:latest
sudo docker rmi handmades_mongo_tmp
