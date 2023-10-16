package cs5223.assignment;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public class Client implements Runnable {

    private static int MAX_ATTEMPTS = 4;
    private static int CURRENT_ATTEMPTS = 0;
    GameApplication gameApplication;

    Game game;

    public Client(GameApplication gameApplication, Game game) {
        this.gameApplication = gameApplication;
        this.game = game;
    }

    @Override
    public void run() {
        String priSvrAddr = game.getGameState().getPrimaryServerAddr();
        String[] priSvrAddrArray = priSvrAddr.split("\\:");
        String priSvrIP = priSvrAddrArray[0];
        String priSvrPort = priSvrAddrArray[1];
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String playerInput = sc.nextLine();
            log("[CLIENT] This is input: " + playerInput);
            String message;
            switch (playerInput) {
                case "9":
                    log("[CLIENT] Quitting");
                    // * send message to server to quit */
                    message = sendMessage(priSvrIP, priSvrPort, playerInput);
                    updateGUI(message, gameApplication, game);
                    break;
                case "0":
                    // * Refresh local state */
                    log("[CLIENT] Refreshing local state");
                    message = sendMessage(priSvrIP, priSvrPort, playerInput);
                    updateGUI(message, gameApplication, game);
                    break;
                case "1":
                    // * Move left */
                    log("[CLIENT] Moving left");
                    message = sendMessage(priSvrIP, priSvrPort, playerInput);
                    updateGUI(message, gameApplication, game);
                    break;
                case "2":
                    // * Move down */
                    log("[CLIENT] Moving down");
                    message = sendMessage(priSvrIP, priSvrPort, playerInput);
                    updateGUI(message, gameApplication, game);
                    break;
                case "3":
                    // * Move right */
                    log("[CLIENT] Moving right");
                    message = sendMessage(priSvrIP, priSvrPort, playerInput);
                    updateGUI(message, gameApplication, game);
                    break;
                case "4":
                    // * Move up */
                    log("[CLIENT] Moving up");
                    message = sendMessage(priSvrIP, priSvrPort, playerInput);
                    updateGUI(message, gameApplication, game);
                    break;
                default:
                    log("[CLIENT] Invalid Input");
            }
        }
    }

    private static void log(String str) {
        System.out.println(str);
    }

    private String messageFormatter(String command) {
        String finalMsg = "";
        String refreshMsg = "[00]|" + this.game.getPlayerName() + "|";
        String pingMsg = "[01]|" + this.game.getPlayerName() + "|";
        String moveMsg = "[02]|" + this.game.getPlayerName() + "|";
        String registerMsg = "[03]|" + this.game.getPlayerName() + "|";
        String quitMsg = "[04]|" + this.game.getPlayerName() + "|";

        switch (command) {

            case "PING":
                finalMsg = pingMsg;
                break;
            case "9":
                // * Quit */
                finalMsg = quitMsg;
                break;

            case "0":
                // * Refresh local state */
                finalMsg = refreshMsg;
                break;

            case "1":
                // * Move left */
                finalMsg = moveMsg + "MOVELEFT";
                break;

            case "2":
                // * Move down */
                finalMsg = moveMsg + "MOVEDOWN";
                break;

            case "3":
                // * Move right */
                finalMsg = moveMsg + "MOVERIGHT";
                break;

            case "4":
                // * Move up */
                finalMsg = moveMsg + "MOVEUP";
                break;
        }
        return finalMsg;
    }

    public byte[] convertObjToBytes(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }

    private void retrySendMessage(String input) {

        int delay = 250;

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            log("[CLIENT] retrySendMessage InterruptedException: " + e.getMessage());
        }

        if (CURRENT_ATTEMPTS < MAX_ATTEMPTS) {
            CURRENT_ATTEMPTS += 1;
            String priSvrAddr = this.game.getGameState().getPrimaryServerAddr();
            String[] priSvrAddrArray = priSvrAddr.split("\\:");
            String priSvrIP = priSvrAddrArray[0];
            String priSvrPort = priSvrAddrArray[1];
            sendMessage(priSvrIP, priSvrPort, input);
        } else {
            CURRENT_ATTEMPTS = 0;
            // TODO: * Connect to backup */
            String backupAddr = this.game.getGameState().getBackupServerAddr();
            String[] backupAddrArr = backupAddr.split("\\:");
            String backupSvrIP = backupAddrArr[0];
            String backupSvrPort = backupAddrArr[1];
            sendMessage(backupSvrIP, backupSvrPort, input);
        }
    }

    public Object convertFromBytes(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                ObjectInputStream in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            log("[GAME] convert from bytes classnotfound: " + e.getMessage());
            return null;
        }
    }

    private String sendMessage(String serverAdd, String port, String input) {

        SocketChannel channel;
        String message = messageFormatter(input);

        String reply = null;

        try {
            InetSocketAddress serverAddress = new InetSocketAddress(serverAdd, Integer.parseInt(port));
            channel = SocketChannel.open(serverAddress);
            byte[] messageBytes = convertObjToBytes(message);

            ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
            channel.write(buffer);
            log("[CLIENT] Message sent: " + message);
            buffer.clear();
            if (!input.equals("9")) {
                CompletableFuture<String> asyncReply = CompletableFuture.supplyAsync(() -> {
                    // todo:
                    return receiveReply(channel, buffer);
                });
                while (!asyncReply.isDone()) {
//                    log("[GAME APPLICATION] Waiting for primary server response");
                }
                return asyncReply.get();
            } else {
                log("[CLIENT] Bye");
                // ! For production
                System.exit(0);

            }

        }
        // catch (InterruptedException e) {
        // log("[CLIENT] Thread Sleep InterruptedException: " + e.getMessage());
        // }
        catch (UnresolvedAddressException e) {
            log("[CLIENT] sendMessage UnresolvedAddressException: " + e.getMessage());
        } catch (IOException e) {
            log("[CLIENT] sendMessage IOException: " + e.getMessage());
            this.retrySendMessage(input);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return reply;
    }

    private String receiveReply(SocketChannel channel, ByteBuffer buffer) {
        String replyMsg = null;
        try {
            Object reply = null;
            ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 1024);
            responseBuffer.clear();

            int read = channel.read(responseBuffer);
            if (read == -1) {
                channel.close();
                return null;
            }

            byte[] replyBytes = new byte[read];
            responseBuffer.position(0);
            responseBuffer.get(replyBytes);

            // reply = convertFromBytes(replyBytes);

            int messageLength = (int) convertFromBytes(Arrays.copyOfRange(replyBytes, 0, 81));
            replyMsg = (String) convertFromBytes(Arrays.copyOfRange(replyBytes, 81, 81 + messageLength));
            GameState latestGameState = (GameState) convertFromBytes(
                    Arrays.copyOfRange(replyBytes, 81 + messageLength, replyBytes.length));
            this.game.setGameState(latestGameState);
            System.out.println("[CLIENT] Received reply - Message Length: " + messageLength + "; Message: " + replyMsg
                    + "; GameState: " + latestGameState);

            log("[CLIENT] Game State Quality Check");
            log("[CLIENT] GameState N: " + latestGameState.getPrimaryServerAddr());
            log("[CLIENT] GameState N: " + latestGameState.gridSize);
            log("[CLIENT] GameState N: " + latestGameState.numTreasures);

            return replyMsg;

        } catch (IOException e) {
            log("[CLIENT] Error in receiveReply: " + e.getMessage());
            e.printStackTrace();
        }

        log("[CLIENT] Connection closed");

        return replyMsg;
    }

    private void updateGUI(String message, GameApplication gameApplication, Game game) {
        log("[CLIENT] Updating GUI");
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    gameApplication.renderGameGUIGrid(game.getGameState(), true, message);
                } catch (Tile.NoPlayerOnTileException e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }
}
