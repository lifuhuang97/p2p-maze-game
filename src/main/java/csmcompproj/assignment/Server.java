package csmcompproj.assignment;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import csmcompproj.assignment.ServerIdentity;

public class Server implements Runnable {

  private Game game;
  private ServerIdentity identity;
  private ExecutorService playerThreadPool;
  private String id;
  private ServerSocketChannel serverSocket;
  private ReentrantLock lock;
  Map<String, Player> playerThreadsMap;
  Map<String, Long> playerPingMap;

  // * Maintains concurrent objects, modified by client handlers */

  public Server(Game game) {
    this.game = game;
    this.id = game.getPlayerName();
    this.identity = game.getServerIdentity();
    this.serverSocket = game.getMyServer();
    this.playerThreadPool = Executors.newFixedThreadPool(100000);
    this.playerThreadsMap = new HashMap<String, Player>();
    this.lock = new ReentrantLock();
  }

  private void updateServerIdentity() {
    this.identity = this.game.getServerIdentity();
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
      log("[SERVER] convert from bytes classnotfound: " + e.getMessage());
      return null;
    }
  }

  private synchronized void register(Selector selector, ServerSocketChannel serverSocket) throws IOException {
    SocketChannel client = serverSocket.accept();
    client.configureBlocking(false);
    client.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(1024 * 1024));
    // log("[SERVER] Connection accepted From: " +
    // client.socket().getRemoteSocketAddress());
  }

  private synchronized void receiveRequest(SelectionKey key) throws IOException {
    SocketChannel client = (SocketChannel) key.channel();
    ByteBuffer buffer = (ByteBuffer) key.attachment();
    buffer.clear();

    int isReading = client.read(buffer);

    if (isReading == -1) {
      // key.cancel();
      client.close();
      return;
    }

    byte[] bytes = new byte[isReading];
    buffer.position(0);
    buffer.get(bytes);

    // ? Message is Action|ID|IP|Port|[Direction]
    String receivedMsg = (String) convertFromBytes(bytes);

    if (receivedMsg.length() > 0 && receivedMsg != null) {
    } else {
      return;
    }

    String[] splitMsg = receivedMsg.split("\\|");
    String senderAction = splitMsg[0];

    // * Special handler for backup */
    if (identity == ServerIdentity.BACKUP || identity == ServerIdentity.NORMAL) {

      if (bytes.length >= 11 && senderAction.equals("[07]")) {

        if (identity == ServerIdentity.NORMAL) {
          game.setServerIdentity(ServerIdentity.BACKUP);
        }

        log("[SERVER] RECEIVED BACKUP SVR MESSAGE");
        log(receivedMsg);
        byte[] gameState = Arrays.copyOfRange(bytes, 11, bytes.length);
        GameState backupState = (GameState) convertFromBytes(gameState);
        game.setGameState(backupState);
        // System.out.println(game.getGameState().getGridSize());
        return;
      }
    }

    if (splitMsg.length <= 1) {
      System.out.println("[SERVER] Null Message Detector: Message is: " + Arrays.toString(splitMsg));
    }
    String senderId = splitMsg[1];

    // ? [00] is refresh, [02] is move, [03] is register, [05] is getPrimary, [06] i
    // registerbackup
    if (senderAction.equals("[00]") || senderAction.equals("[02]") || senderAction.equals("[03]")
        || senderAction.equals("[05]")
        || senderAction.equals("[06]")) {
      key.interestOps(SelectionKey.OP_WRITE);

      playerThreadsMap.put(senderId, new Player(senderId, key, senderAction, game, splitMsg));

      log("[SERVER] Player input received: " + receivedMsg);

      // log("[SERVER] Player thread identity is " +
      // playerThreadsMap.get(senderId).toString());

      playerThreadPool.execute(playerThreadsMap.get(senderId));

    } else if (senderAction.equals("[01]")) {
      // ? [01] is ping
      // * If normal player start getting ping, upgrade to backup */
      if (identity == ServerIdentity.BACKUP) {

        // log("[SERVER] RECEIVED PING AS BACKUP");
        // TODO: Ask wenhao if ping is handling backup-primary status check
      } else if (identity == ServerIdentity.PRIMARY) {
        // log("[SERVER] RECEIVED PING AS PRIMARY");
        // * Ping received, update game lastSeenMap */
        game.setLastSeenMap(senderId);
      }

      // ? [04] is quit
    } else if (senderAction.equals("[04]")) {
      // * Delete from gamestate */
      try {
        game.getGameState().removePlayer(senderId);
      } catch (Tile.NoPlayerOnTileException e) {
        log("[SERVER] NoPlayerOnTile when removing: " + e.getMessage());
      }
    }

  }

  @Override
  public void run() {
    log("[SERVER] Server thread is running");

    try {
      Selector selector = Selector.open();
      int ops = serverSocket.validOps();
      serverSocket.register(selector, ops);

      while (true) {

        updateServerIdentity();

        selector.select();
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

        while (keys.hasNext()) {
          SelectionKey key = keys.next();

          // * Accept a connection */
          if (key.isAcceptable()) {
            register(selector, serverSocket);

            // * Read from a channel */
          } else if (key.isReadable()) {
            try {
              receiveRequest(key);

            } catch (Exception e) {
              log("[SERVER] Error in isReadable(): " + e.getMessage());
              e.printStackTrace();
              SocketChannel client = (SocketChannel) key.channel();
              client.close();
            }
          } else if (key.isWritable()) {

            // * Write if necessary */
            // String pingOutput = "Ping Received";

            // SocketChannel channel = (SocketChannel) key.channel();
            // ByteBuffer buffer = (ByteBuffer) key.attachment();
            // buffer.flip();

            // byte[] messageBytes = pingOutput.getBytes();
            // ByteBuffer testBuffer = ByteBuffer.wrap(messageBytes);
            // channel.write(testBuffer);
            // key.interestOps(SelectionKey.OP_READ);

            // channel.write(buffer);

            // if (buffer.hasRemaining()) {
            // buffer.compact();
            // } else {
            // buffer.clear();
            // }
          }
          keys.remove();

        }

      }
    } catch (IOException e) {
      // TODO: Problem - Connection Reset IOException triggered by ???
      log("[SERVER] IOException here: " + e.getMessage());
      e.printStackTrace();
    }

  }

}