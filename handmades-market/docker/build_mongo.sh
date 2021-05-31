#!/bin/bash
sudo docker rmi handmades_mongo:latest
# sudo docker build -t handmades_mongo_tmp -f DockerfileMongodb .
sudo docker create -p 27017:27017 --name mongo_handmades_cntnr mongo:latest --replSet rs0 --bind_ip_all
sudo docker cp mongodb_init.js mongo_handmades_cntnr:/docker-entrypoint-initdb.d
sudo docker commit mongo_handmades_cntnr handmades_mongo:latest
sudo docker rm mongo_handmades_cntnr
# sudo docker rmi handmades_mongo_tmp
