# Description
This app allows to start and stop executables based on a znode. The program watches the specified znode and its children
and prints this znode's children when their state changes (one is created or deleted). It also allows to print current
znode's tree state (you just need to type "TREE" in the console). It also starts the specified program e.g. text editor 
with the specified arguments when the znode exists and kills the program if the znode goes away.

Client can create and delete nodes and their children using:
* create /my_node Zadanie
* create /my_node/a
* create /my_node/a/ab
* deleteall /my_node

# How to run
Necessary files are included in bin/ and config/.
Open terminal and do the following:
* cd /opt/apache-zookeeper-3.6.1/bin/

### One server in default config and one client
* ./zkServer2.sh start
This uses default server config (zoo.cfg).

Default clientPort in default config file is 2181.
* Connect client to server: `./zkCli.sh -server 127.0.0.1:2181`
* goto Both :smiley:

### Replicated Zookeper, a.k.a. two or more servers and clients
You need to run at least two servers (each one contains different port for each client) and two clients. Each
client should connect to different server.

* Run servers (from bin folder path looks like this):
`./zkServer2.sh start ../conf/zoo1.cfg`,
`./zkServer2.sh start ../conf/zoo2.cfg` etc.
* Run clients: `./zkCli.sh -server 127.0.0.1:2181` etc. (2181 is clientPort specified in config file)

Check if configuration is correct - you should be able to create a znode from client's terminal and you should see
the created znode from other clients' terminals (ls -R /).
If so, you can proceed.

## Both
* Run Executor.java with args: `127.0.0.1:clientPort /my_node "ping -c10 8.8.8.8"`
or `127.0.0.1:clientPort /my_node "/usr/bin/gedit"` if you are on Fedora. Replace _clientPort_ with your clientPort
specified in config file.

In order to stop the server, do: `./zkServer2.sh stop`