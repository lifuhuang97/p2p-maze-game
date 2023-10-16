package cs5223.assignment;

import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Integer.parseInt;

public class Tracker implements TrackerInterface {
    // Stores player id and player ip (NOT up to date)
    private int gridSize; // N
    private int numTreasures; // K
    private int portNumber;
    private ConcurrentHashMap<String, String> playerMap = new ConcurrentHashMap<>();

    public Tracker(int gridSize, int numTreasures) {
        this.gridSize = gridSize;
        this.numTreasures = numTreasures;
    }

    public int getGridSize() throws RemoteException {
        return this.gridSize;
    }

    public int getNumTreasures() throws RemoteException {
        return this.numTreasures;
    }

    @Override
    public ConcurrentHashMap<String, String> register(String name, String ipAddress) throws RemoteException {
        synchronized (playerMap) {
            System.out.println("Registering new player: [" + name + "][" + ipAddress + "]");
            if (playerMap.containsKey(name)) {
                System.err.println("'" + name + "' is already in use, please choose another name!");
                return null;
            } else {
                playerMap.put(name, ipAddress);
                System.out.println("Updated player list: ");
                playerMap.forEach((k, v) -> {
                    System.out.println("Name: " + k + " || " + "Ip Address: " + v);
                });
                System.out.println("-----------------------------------------------");
                return playerMap;
            }
        }
    }

    @Override
    public ConcurrentHashMap<String, String> evict(String by, String name) throws RemoteException {
        synchronized (playerMap) {
            System.out.println("Evicting player by " + by + ": [" + name + "]");
            playerMap.remove(name);
            System.out.println("Updated player list: ");
            playerMap.forEach((k, v) -> {
                System.out.println("Name: " + k + " || " + "Ip Address: " + v);
            });
            System.out.println("-----------------------------------------------");
            return playerMap;
        }
    }

    public ConcurrentHashMap<String, String> getPlayerMap() {
        return this.playerMap;
    }

    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("Proper Usage is: java Tracker [port-number] [N] [K]");
            System.exit(0);
        }

        int[] intArgs = new int[3];

        for(int i = 0; i < args.length; i++) {
            try {
                intArgs[i] = Integer.parseInt(args[i]);
            } catch (NumberFormatException e ) {
                System.err.println("Invalid arguments! Proper Usage is: java Tracker [port-number] [N] [K]");
            }
        }

        Registry registry = null;
        Tracker trackerServer = null;
        TrackerInterface stub = null;

        try {
            trackerServer = new Tracker(intArgs[1], intArgs[2]);
            stub = (TrackerInterface) UnicastRemoteObject.exportObject(trackerServer, intArgs[0]);
            registry = LocateRegistry.getRegistry();
            registry.bind("tracker", stub);
            System.out.println("Tracker Server ready");
        } catch (AlreadyBoundException e) {
            try {
                registry.unbind("tracker");
                registry.bind("tracker", stub);
                System.out.println("Tracker Server ready");
            } catch (Exception e2) {
                System.err.println("Server exception: " + e.toString());
            }
        } catch (Exception e3) {
            System.err.println("Server exception: " + e3.toString());
            e3.printStackTrace();
        }
    }
}
