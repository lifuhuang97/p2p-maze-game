package cs5223.assignment;

import javafx.application.Application;
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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public class GameApplication extends Application {

    private GridPane gameGrid;

    private VBox root;

    private Label messageLabel;

    private Label currentScoresLabel;

    private Label primaryAndBackupServerNamesLabel;

    private String id;

    private Game game;

    private static int MAX_ATTEMPTS = 4;
    private static int CURRENT_ATTEMPTS = 0;

    public AtomicReference<String> input = new AtomicReference<>();

    @Override
    public void init() throws Exception {
        super.init();
    }

    public GameApplication() {
    }

    public GameApplication(Game game) {
        super();
        System.out.println("GAME INSTANTIATING");
        this.game = game;
        this.id = game.getPlayerName();
    }

    public static void main(String[] args) {
        // extends means our app class is a child class, so we inherit the launch static
        // method
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        gameGrid = new GridPane();
        root = new VBox();
        Scene scene = new Scene(root, 600, 600);
        messageLabel = new Label();
        Label playerNameLabel = new Label("Local Player: " + game.getPlayerName());
        String currentScores = "PLAYER SCORES: ";
        Map<String, Integer> currentScoresMap = this.game.getGameState().getPlayerScore();
        Map<String, String> currentPlayerList = this.game.getGameState().getPlayerList();
        String currentPrimaryServerAddr = this.game.getGameState().getPrimaryServerAddr();
        String currentBackupServerAddr = this.game.getGameState().getBackupServerAddr();
        for (Map.Entry<String, Integer> entry : currentScoresMap.entrySet()) {
            currentScores += entry.getKey() + ": " + entry.getValue() + " || ";
        }
        String primaryAndBackUpServerNames = "";
        for (Map.Entry<String, String> entry : currentPlayerList.entrySet()) {
            if (entry.getValue().equals(currentPrimaryServerAddr)) {
                primaryAndBackUpServerNames += "Primary Server name: " + entry.getKey() + " ";
            } else if (entry.getValue().equals(currentBackupServerAddr)) {
                primaryAndBackUpServerNames += "Backup Server name: " + entry.getKey() + " ";
            }
        }
        currentScoresLabel = new Label(currentScores);
        currentScoresLabel.setTextFill(Color.BLUE);
        primaryAndBackupServerNamesLabel = new Label(primaryAndBackUpServerNames);
        primaryAndBackupServerNamesLabel.setTextFill(Color.RED);
        Label playerInputLabel = new Label("Player Input");
        renderGameGUIGrid(this.game.getGameState(), false, "Please make a move first!");
        root.getChildren().addAll(playerNameLabel, currentScoresLabel, primaryAndBackupServerNamesLabel,
                playerInputLabel, gameGrid);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public void renderGameGUIGrid(GameState gameState, boolean hasInput, String message)
            throws Tile.NoPlayerOnTileException {
        if (hasInput) {
            String currentScoresUpdated = "PLAYER SCORES: ";
            Map<String, Integer> currentScoresMap = this.game.getGameState().getPlayerScore();
            Map<String, String> currentPlayerList = this.game.getGameState().getPlayerList();
            String currentPrimaryServerAddr = this.game.getGameState().getPrimaryServerAddr();
            String currentBackupServerAddr = this.game.getGameState().getBackupServerAddr();
            for (Map.Entry<String, Integer> entry : currentScoresMap.entrySet()) {
                currentScoresUpdated += entry.getKey() + ": " + entry.getValue() + " || ";
            }

            String primaryAndBackUpServerNames = "";
            for (Map.Entry<String, String> entry : currentPlayerList.entrySet()) {
                if (entry.getValue().equals(currentPrimaryServerAddr)) {
                    primaryAndBackUpServerNames += "Primary Server name: " + entry.getKey() + " ";
                } else if (entry.getValue().equals(currentBackupServerAddr)) {
                    primaryAndBackUpServerNames += "Backup Server name: " + entry.getKey() + " ";
                }
            }
            currentScoresLabel.setText(currentScoresUpdated);
            primaryAndBackupServerNamesLabel.setText(primaryAndBackUpServerNames);
            messageLabel.setText(message);
            gameGrid.setGridLinesVisible(false);
            gameGrid.getColumnConstraints().clear();
            gameGrid.getRowConstraints().clear();
            gameGrid.getChildren().clear();
            gameGrid.setGridLinesVisible(true);
        } else {
            messageLabel.setText("Please make a move first.");
            messageLabel.setTextFill(Color.PURPLE);
            root.getChildren().add(messageLabel);
        }
        final int size = gameState.gridSize;
        System.out.println("GAME SIZE IS: " + size);
        Tile[][] tiles = gameState.grid;

        // Position the pane at the center of the screen, both vertically and
        // horizontally
        gameGrid.setGridLinesVisible(true);
        gameGrid.setAlignment(Pos.BOTTOM_CENTER);
        // Add Column Constraints
        ColumnConstraints colConst = new ColumnConstraints();
        colConst.setPercentWidth(100.0 / size);

        RowConstraints rowConst = new RowConstraints();
        rowConst.setPercentHeight(100.0 / size);

        for (int i = 0; i < size; i++) {
            gameGrid.getColumnConstraints().add(colConst);
            gameGrid.getRowConstraints().add(rowConst);
        }

        for (int j = 0; j < size; j++) {
            for (int k = 0; k < size; k++) {
                Tile currentTile = tiles[j][k];
                if (currentTile.hasPlayer()) {
                    String name = currentTile.getPlayer();
                    Label playerNameText = new Label(name);
                    playerNameText.setFont(new Font(15));
                    playerNameText.setTextFill(Color.BLACK);
                    GridPane.setFillWidth(playerNameText, true);
                    playerNameText.setMaxWidth(Double.MAX_VALUE);
                    playerNameText.setAlignment(Pos.CENTER);
                    gameGrid.add(playerNameText, j, size - 1 - k);
                } else if (currentTile.hasTreasure()) {
                    Label treasure = new Label("*");
                    treasure.setStyle("-fx-border-color:red; -fx-background-color: blue;");
                    treasure.setTextFill(Color.WHITE);
                    treasure.setFont(new Font(15));
                    GridPane.setFillWidth(treasure, true);
                    treasure.setMaxWidth(Double.MAX_VALUE);
                    treasure.setAlignment(Pos.CENTER);
                    gameGrid.add(treasure, j, size - 1 - k);
                }
            }
        }
        System.out.println("INPUT:" + this.input);
    }

    private static void log(String str) {
        System.out.println(str);
    }

    private String messageFormatter(String command) {
        String finalMsg = "";
        String refreshMsg = "[00]|" + id + "|";
        String pingMsg = "[01]|" + id + "|";
        String moveMsg = "[02]|" + id + "|";
        String registerMsg = "[03]|" + id + "|";
        String quitMsg = "[04]|" + id + "|";

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
            String priSvrAddr = game.getGameState().getPrimaryServerAddr();
            String[] priSvrAddrArray = priSvrAddr.split("\\:");
            String priSvrIP = priSvrAddrArray[0];
            String priSvrPort = priSvrAddrArray[1];
            sendMessage(priSvrIP, priSvrPort, input);
        } else {
            CURRENT_ATTEMPTS = 0;
            // TODO: * Connect to backup */
            String backupAddr = game.getGameState().getBackupServerAddr();
            String[] backupAddrArr = backupAddr.split("\\:");
            String serverAdd = backupAddrArr[0];
            String port = backupAddrArr[1];
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
            ByteBuffer responseBuffer = ByteBuffer.allocate(1024*1024);
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

    private void updateGUI(String message) {
        log("[CLIENT] Updating GUI");
        try {
            renderGameGUIGrid(this.game.getGameState(), true, message);
        } catch (Tile.NoPlayerOnTileException e) {
            throw new RuntimeException(e);
        }
    }
}
