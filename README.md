Open terminal and do the following:

cd /opt/apache-zookeeper-3.6.1/bin/
./zkServer2.sh start
Default client port in default config file (zoo.cfg) is 2181.
Connect client to server: 
./zkCli.sh -server 127.0.0.1:2181

Run Executor.java with args:
127.0.0.1:2181 /my_node "ping -c10 8.8.8.8"
and...

Client can create and delete nodes and their children using:
create /my_node Zadanie
create /my_node/a
create /my_node/a/ab
deleteall /my_node

./zkServer2.sh stop