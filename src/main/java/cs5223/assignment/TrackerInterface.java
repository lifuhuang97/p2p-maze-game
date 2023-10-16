package cs5223.assignment;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;

public interface TrackerInterface extends Remote {
    int getGridSize() throws RemoteException;
    int getNumTreasures() throws RemoteException;
    ConcurrentHashMap<String, String> getPlayerMap() throws RemoteException;
    ConcurrentHashMap<String, String> register(String name, String ipAddress) throws RemoteException;
    ConcurrentHashMap<String, String> evict(String by, String name) throws RemoteException;

}
