package cs5223.assignment;

import cs5223.assignment.GameState;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.*;

public class Ping implements Runnable {

    private Game game;
    private String id;
    private String myAddr;
    private GameState gameState;
    private final static int MAX_ATTEMPTS = 3;

    public Ping(Game game) {
        this.game = game;
        this.id = game.getPlayerName();
        this.myAddr = game.getMyAddr();
    }

    private static void log(String str) {
        System.out.println(str);
    }

    public byte[] convertObjToBytes(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }

    public Object convertFromBytes(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            log("[PING] convert from bytes classnotfound: " + e.getMessage());
            return null;
        }
    } //Error in isReadable()

    public InetSocketAddress convertStringToInetAddress(String addr) {
        String[] priServerStrings = addr.split("\\:");
        InetSocketAddress iNetAddr = new InetSocketAddress(priServerStrings[0],
                Integer.parseInt(priServerStrings[1]));
        return iNetAddr;
    }
    private void ping() throws UnknownHostException, RemoteException, Tile.NoPlayerOnTileException {

        int currentAttempt = 1;
        boolean sending = true;

        String serverAddr;
        String initialPrimary = this.game.getGameState().getPrimaryServerAddr();
        String initialBackup = this.game.getGameState().getBackupServerAddr();

        if (this.game.getServerIdentity() != ServerIdentity.PRIMARY) {
            serverAddr = initialPrimary;
        } else {
            serverAddr = initialBackup;
        }

        SocketChannel channel;
        String message =  "[01]|" + this.id + "|" + this.myAddr;
        if (serverAddr != null) {
            while (sending) {
                try {
                    // System.out.println("SERVER ADDR" + serverAddr);
                    InetSocketAddress serverAddress = convertStringToInetAddress(serverAddr);
                    channel = SocketChannel.open(serverAddress);
                    byte[] messageBytes = convertObjToBytes(message);
                    ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
                    channel.write(buffer);
                    buffer.clear();
                    // log("[PING] Message sent: " + message);
                    channel.close();
                    sending = false;
                    // log("[PING] Connection closed");
                } catch (IOException e) {
                    currentAttempt += 1;
                    try {
                        Thread.sleep(500);
//                        System.err.println("[PING] Ping IOException: " + e.getMessage());
//                        System.out.println("[PING] [" + currentAttempt + "] Retrying ...");

                        if (currentAttempt > MAX_ATTEMPTS) {
                            synchronized (game) {
                                GameState currentState = this.game.getGameState();
                                if (this.game.getServerIdentity() == ServerIdentity.PRIMARY) {
//                                System.out.println("[PING] Setting a new backup");
                                    this.game.setNewBackup();
                                    serverAddr = initialBackup;
//                                System.out.println("[PING] NEW BACKUP SERVER: " + serverAddr);
                                } else {
                                    // if normal or backup
                                    if (currentState.getPrimaryServerAddr().equals(currentState.getBackupServerAddr())) {
                                        System.out.println("[PING] primary and backup same address and failed, looking for new primary");
                                        for (String k : currentState.getPlayerList().keySet()) {
                                            try {
                                                String primaryAddr = this.game.retrievePrimaryServerAddress(this.game.convertStringToInetAddress(currentState.getPlayerList().get(k)));
                                                if (!primaryAddr.equals(currentState.getPrimaryServerAddr())) {
                                                    this.game.getGameState().updatePrimaryServer(primaryAddr);
                                                    serverAddr = currentState.getBackupServerAddr();
                                                    break;
                                                }
                                            } catch (IOException e2) {
                                                System.out.println("[PING] Finding new PRIMARY IOException: " + e);
                                            }
                                        }
                                    } else {
                                        if (this.game.getServerIdentity() == ServerIdentity.BACKUP) {
                                            if (initialPrimary.equals(this.game.getGameState().getPrimaryServerAddr())) {
                                                String primaryServerName = null;
                                                for (String s : currentState.getPlayerList().keySet()) {
                                                    if (currentState.getPlayerList().get(s).equals(initialPrimary)) {
                                                        primaryServerName = s;
                                                        break;
                                                    }
                                                }
                                                this.game.setPlayerMap(this.game.evict(this.id, primaryServerName));
                                                this.game.getGameState().removePlayer(primaryServerName);

                                                System.out.println("[PING] Switching myself to primary");
                                                this.game.getGameState().updatePrimaryServer(this.game.getMyAddr());

                                                System.out.println("[PING] Setting a new backup");
                                                this.game.setServerIdentity(ServerIdentity.PRIMARY);
                                                this.game.setNewBackup();
                                                serverAddr = this.game.getGameState().getBackupServerAddr();
                                            } else {
                                                serverAddr = this.game.getGameState().getPrimaryServerAddr();
                                            }
                                        } else {
                                            System.out.println("[PING] Switching backup to primary");
                                            this.game.getGameState().updatePrimaryServer(initialBackup);
                                            serverAddr = initialBackup;
                                        }

                                        System.out.println("[PINGI] New Ping Target: " + serverAddr);
                                    }
                                }
                                currentAttempt = 1;
                            }
                        }
                    } catch (InterruptedException e2) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public void run() {

        log("[PING] Ping thread is running");

        while (true) {
            try {
                this.ping();
                Thread.sleep(500);
            } catch (InterruptedException | RemoteException | UnknownHostException | Tile.NoPlayerOnTileException e) {
                throw new RuntimeException(e);
            }
        }
    }
}