/*
 * A simple class that monitors the data and existence of a ZooKeeper
 * node. It uses asynchronous ZooKeeper APIs.
 */

import java.util.Arrays;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Stat;

public class DataMonitor implements Watcher, StatCallback {
    ZooKeeper zk;
    String znode;
    Watcher chainedWatcher;
    boolean dead;
    DataMonitorListener listener;
    byte[] prevData;

    public DataMonitor(ZooKeeper zk, String znode, Watcher chainedWatcher,
                       DataMonitorListener listener) {
        this.zk = zk;
        this.znode = znode;
        this.chainedWatcher = chainedWatcher;
        this.listener = listener;
        // Get things started by checking if the node exists. We are going
        // to be completely event driven
        zk.exists(znode, true, this, null);
        System.out.println("Initial " + znode + "'s children state");
        int childrenCount = subscribeForEachZnodeChildEvent(znode);
        System.out.println(znode + " has " + childrenCount + (childrenCount == 1 ? " child." : " children."));
        System.out.println("==================================");
    }

    /**
     * Other classes use the DataMonitor by implementing this method
     */
    public interface DataMonitorListener {
        /**
         * The existence status of the node has changed.
         */
        void exists(byte[] data);
        /**
         * The ZooKeeper session is no longer valid.
         *
         * @param rc the ZooKeeper reason code
         */
        void closing(int rc);
    }

    public void process(WatchedEvent event) {
        String path = event.getPath();
        if (event.getType() == Event.EventType.None) {
            System.out.println("NONE");
            // We are are being told that the state of the
            // connection has changed
            switch (event.getState()) {
                case SyncConnected:
                    // In this particular example we don't need to do anything
                    // here - watches are automatically re-registered with
                    // server and any watches triggered while the client was
                    // disconnected will be delivered (in order of course)
                    break;
                case Expired:
                    // It's all over
                    dead = true;
                    listener.closing(KeeperException.Code.SessionExpired);
                    break;
            }
        } else if (event.getType() == Event.EventType.NodeChildrenChanged) {
            System.out.println("Znode child's state changed. Printing all znode's children:");
            int childrenCount = subscribeForEachZnodeChildEvent(znode);
            System.out.println(znode + " has " + childrenCount + (childrenCount == 1 ? " child." : " children."));
            System.out.println("==================================");
        } else {
//            System.out.println("OTHER EVENT");
            if (path != null && path.equals(znode)) {
//                System.out.println("PATH IS ZNODE!");
                // Something has changed on the node, let's find out
                zk.exists(znode, true, this, null);
            }
        }
        if (chainedWatcher != null) {
            chainedWatcher.process(event);
        }
    }

    public void printAllChildren(String znode) throws KeeperException, InterruptedException {
        for (String child : zk.getChildren(znode, true)) {
            System.out.println(znode + '/' + child);
            printAllChildren(znode + '/' + child);
        }
    }

    int subscribeForEachZnodeChildEvent(String znode) {
        int childrenCount = 0;
        try {
            for (String child : zk.getChildren(znode, true)) {
                System.out.println(znode + '/' + child);
                childrenCount++;
                childrenCount += subscribeForEachZnodeChildEvent(znode + '/' + child);
            }
        } catch (KeeperException e) {
            System.out.println("Znode probably doesn't exist. Try to create it and run the program again.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return childrenCount;
    }

    public void processResult(int rc, String path, Object ctx, Stat stat) {
        boolean exists;
        switch (rc) {
            case Code.Ok:
                exists = true;
                break;
            case Code.NoNode:
                exists = false;
                break;
            case Code.SessionExpired:
            case Code.NoAuth:
                dead = true;
                listener.closing(rc);
                return;
            default:
                // Retry errors
                zk.exists(znode, true, this, null);
                return;
        }

        byte[] b = null;
        if (exists) {
            try {
                b = zk.getData(znode, false, null);
            } catch (KeeperException e) {
                // We don't need to worry about recovering now. The watch
                // callbacks will kick off any exception handling
                e.printStackTrace();
            } catch (InterruptedException e) {
                return;
            }
        }
        if ((b == null && b != prevData)
                || (b != null && !Arrays.equals(prevData, b))) {
            listener.exists(b);
            prevData = b;
        }
    }
}