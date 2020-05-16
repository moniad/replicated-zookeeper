/*
 * A simple class that monitors the existence of a ZooKeeper node. It uses asynchronous ZooKeeper APIs.
 */

import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class ZnodeStateMonitor implements Watcher, StatCallback {
    ZooKeeper zk;
    String znode;
    Watcher chainedWatcher;
    boolean dead;
    ZnodeStateChangeListener listener;

    public ZnodeStateMonitor(ZooKeeper zooKeeper, String znode, Watcher chainedWatcher,
                             ZnodeStateChangeListener listener) {
        this.zk = zooKeeper;
        this.znode = znode;
        this.chainedWatcher = chainedWatcher;
        this.listener = listener;
        // checking if the node exists
        zooKeeper.exists(znode, true, this, null);
        System.out.println("Initial " + znode + "'s children state");
        int childrenCount = subscribeForEachZnodeChildEvent(znode);
        System.out.println(znode + " has " + childrenCount + (childrenCount == 1 ? " child." : " children."));
        System.out.println("==================================");
    }

    // custom interface used to communicate back to Executor
    public interface ZnodeStateChangeListener {
        /**
         * The existence status of the node has changed.
         */
        void exists();

        /**
         * The ZooKeeper session is no longer valid.
         *
         * @param rc the ZooKeeper reason code
         */
        void closing(int rc);
    }

    public void process(WatchedEvent event) {
        String path = event.getPath();
        if (event.getType() == Event.EventType.None) { // The state of the connection has changed
            switch (event.getState()) {
                case SyncConnected: // Watches are automatically re-registered with server and any watches triggered
                    // while the client was disconnected will be delivered (in order of course)
                    break;
                case Expired: // session expired
                    dead = true;
                    listener.closing(KeeperException.Code.SessionExpired);
                    break;
            }
        } else if (event.getType() == Event.EventType.NodeChildrenChanged) {
            System.out.println("==================================");
            System.out.println("Znode child's state changed. Printing all znode's children:");
            int childrenCount = subscribeForEachZnodeChildEvent(znode);
            System.out.println(znode + " has " + childrenCount + (childrenCount == 1 ? " child." : " children."));
            System.out.println("==================================");
        } else {
            if (path != null && path.equals(znode)) {
                // Something has changed on the specified node, let's find out
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
        switch (rc) {
            case Code.Ok:
            case Code.NoNode:
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
        listener.exists();
    }
}