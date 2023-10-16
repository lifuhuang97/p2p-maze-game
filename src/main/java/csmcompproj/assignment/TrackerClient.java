package csmcompproj.assignment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Map;

public class TrackerClient {

    private String trackerAddr;
    private String trackerPort;
    private Registry registry;
    private TrackerInterface tracker;
    protected String playerName;

    public TrackerClient(String trackerAddr, String trackerPort, String playerName) throws RemoteException {
        this.trackerAddr = trackerAddr;
        this.trackerPort = trackerPort;
        this.playerName = playerName;
        this.registry = LocateRegistry.getRegistry(trackerAddr);
        try {
            this.tracker = (TrackerInterface) registry.lookup("tracker");
        } catch (NotBoundException e) {
            System.err.println("Tracker Client exception: " + e);
        }
    }

    public int getGridSizeFromTracker() throws RemoteException {
        return this.tracker.getGridSize();
    }

    public int getNumTreasuresFromTracker() throws RemoteException {
        return this.tracker.getNumTreasures();
    }

    public Map<String, String> register(int port) throws RemoteException, UnknownHostException {
        Map<String, String> playerMap = this.tracker.register(
                this.playerName,
                InetAddress.getLocalHost().getHostAddress() + ":" + port);

        if (playerMap == null) {
            System.err.println("'" + this.playerName + "' is already in use, please choose another name!");
            System.exit(1);
            return null;
        } else {
            return playerMap;
        }
    }

    public Map<String, String> evict(String by, String playerName) throws RemoteException, UnknownHostException {
        return this.tracker.evict(by, playerName);
    }

    public Map<String, String> getPlayerMapFromTracker() throws RemoteException, UnknownHostException {
        return this.tracker.getPlayerMap();
    }

    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("Proper Usage is: java TrackerClient [IP-address] [port-number] [player-id]");
            System.exit(1);
        }

        String host = (args.length < 1) ? null : args[0];
        try {
            TrackerClient client = new TrackerClient(host, args[1], args[2]);
            System.out.println("grid size: " + client.getGridSizeFromTracker());
            System.out.println("num treasures: " + client.getNumTreasuresFromTracker());
            Map<String, String> response = client.register(1000999);
            System.out.println("response: " + response);
//            response = client.evict("ab");
//            System.out.println("response: " + response);
            response = client.register(1000999);
            System.out.println("response: " + response);
            response = client.register(1000999);
            System.out.println("response: " + response);
        } catch (Exception e) {
            System.err.println("Tracker Client exception: " + e);
            e.printStackTrace();
        }
    }
}