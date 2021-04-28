#!/bin/bash
cd ../HandmadesServer
sudo docker build -t handmades_node -f ../docker/DockerfileNode .
