/*
 * A simple example program to use DataMonitor to start and stop executables based on a znode. The program watches
 * the specified znode and its children and prints this znode's children on their state change.
 * It also allows to print current znode's tree state. It also starts the specified program
 * with the specified arguments when the znode exists and kills the program if the znode goes away.
 */

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.util.Scanner;

public class Executor
        implements Watcher, Runnable, ZnodeStateMonitor.ZnodeStateChangeListener {
    String znode;
    ZnodeStateMonitor stateMonitor;
    ZooKeeper zooKeeper;
    String exec;
    Process child;

    public Executor(String hostIp, final String znode, String exec) throws IOException {
        this.exec = exec;
        this.znode = znode;
        zooKeeper = new ZooKeeper(hostIp, 40000, this);
        stateMonitor = new ZnodeStateMonitor(zooKeeper, znode, null, this);

        // thread listing tree
        new Thread(new Runnable() {
            @Override
            public void run() {
                String listingCommand = "TREE";
                Scanner in = new Scanner(System.in);
                String s;
                while (true) {
                    System.out.println("==================================");
                    System.out.println("Type " + listingCommand + " to list all " + znode + "'s tree: ");
                    s = in.nextLine();

                    if (s.equals(listingCommand)) {
                        try {
                            if (zooKeeper.exists(znode, true) != null) {
                                System.out.println(znode);
                                stateMonitor.printAllChildren(znode);
                            } else {
                                System.out.println(znode + " has no children.");
                            }
                        } catch (KeeperException e) {
                            System.out.println("Znode probably doesn't exist. Try to create it and run the program again.");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("You typed: " + s);
                    }
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        runApp(args);
    }

    private static void runApp(String[] args) {
        if (args.length < 3) {
            System.err.println("USAGE: Executor hostPort znode filename program [args ...]");
            System.exit(2);
        }
        String hostPort = args[0];
        String znode = args[1];
        String exec = args[2];

        try {
            new Executor(hostPort, znode, exec).run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // part of ZooKeeper Java API
    // forwards events such as state changes of the ZooKeeper connection or session to DataMonitor
    public void process(WatchedEvent event) {
        stateMonitor.process(event);
    }

    public void run() {
        try {
            synchronized (this) {
                while (!stateMonitor.dead) {
                    wait();
                }
            }
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
        }
    }

    public void closing(int rc) {
        synchronized (this) {
            notifyAll();
        }
    }

    public void exists() {
        try {
            if (zooKeeper.exists(znode, true) == null) { // check if znode exists
                if (child != null) {
                    System.out.println("Killing process");
                    child.destroy();
                    try {
                        child.waitFor();
                    } catch (InterruptedException e) {
                        System.err.println(e.getMessage());
                    }
                    child = null;
                }
            } else {
                new Thread(new Runnable() { // thread executing program passed as an arg
                    @Override
                    public void run() {
                        System.out.println("Starting child");
                        try {
                            child = Runtime.getRuntime().exec(exec);
                            stateMonitor.subscribeForEachZnodeChildEvent(znode);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        new StreamWriter(child.getInputStream(), System.out);
                        new StreamWriter(child.getErrorStream(), System.err);
                    }
                }).start();
            }
        } catch (KeeperException |
                InterruptedException e) {
            e.printStackTrace();
        }
    }
}