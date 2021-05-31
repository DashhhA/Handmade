#!/bin/bash
cd ../HandmadesServer
sudo docker build -t danmakarov/handmades_node:0.0.2-testing -f ../docker/DockerfileNode .
