#!/bin/bash
sudo docker stop node_handmades_cntnr
sudo docker rm node_handmades_cntnr
sudo docker rmi node_handmades
./build_node
sudo docker create --name node_handmades_cntnr handmades_node
sudo docker start node_handmades_cntnr
sudo docker exec -it node_handmades_cntnr /bin/bash
