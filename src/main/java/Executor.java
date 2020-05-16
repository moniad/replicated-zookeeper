/**
 * A simple example program to use DataMonitor to start and
 * stop executables based on a znode. The program watches the
 * specified znode and saves the data that corresponds to the
 * znode in the filesystem. It also starts the specified program
 * with the specified arguments when the znode exists and kills
 * the program if the znode goes away.
 */

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

public class Executor
        implements Watcher, Runnable, DataMonitor.DataMonitorListener {
    String znode;
    DataMonitor dm;
    ZooKeeper zk;
    String exec;
    Process child;

    public Executor(String hostPort, final String znode, String exec) throws IOException {
        this.exec = exec;
        this.znode = znode;
        zk = new ZooKeeper(hostPort, 3000, this);
        dm = new DataMonitor(zk, znode, null, this);
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
                            if (zk.exists(znode, true) != null) {
                                System.out.println(znode);
                                dm.printAllChildren(znode);
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

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err
                    .println("USAGE: Executor hostPort znode filename program [args ...]");
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

    /***************************************************************************
     * We do process any events ourselves, we just need to forward them on.
     *
     * @see org.apache.zookeeper.Watcher#process(org.apache.zookeeper.proto.WatcherEvent)
     */
    public void process(WatchedEvent event) {
        dm.process(event);
    }

    public void run() {
        try {
            synchronized (this) {
                while (!dm.dead) {
                    wait();
                }
            }
        } catch (InterruptedException e) {
        }
    }

    public void closing(int rc) {
        synchronized (this) {
            notifyAll();
        }
    }

    static class StreamWriter extends Thread {
        OutputStream os;

        InputStream is;

        StreamWriter(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
            start();
        }

        public void run() {
            byte[] b = new byte[80];
            int rc;
            try {
                while ((rc = is.read(b)) > 0) {
                    os.write(b, 0, rc);
                }
            } catch (IOException e) {
            }

        }
    }

    public void exists(byte[] data) {
        if (data == null) {
            if (child != null) {
                System.out.println("Killing process");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {
                }
            }
            child = null;
        } else {
            if (child != null) {
                System.out.println("Stopping child");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Starting child");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        child = Runtime.getRuntime().exec(exec);
                        dm.subscribeForEachZnodeChildEvent(znode);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    new StreamWriter(child.getInputStream(), System.out);
                    new StreamWriter(child.getErrorStream(), System.err);
                }
            }).start();
        }
    }
}