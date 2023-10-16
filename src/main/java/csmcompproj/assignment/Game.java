package csmcompproj.assignment;

import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.rmi.RemoteException;
import java.util.concurrent.locks.*;


public class Game extends TrackerClient {

  // * Set in init of Game instance, wont be changed */
  private String playerName;
  private String myAddr;
  private ServerSocketChannel myServerSocket = null;
  private ReentrantLock lock;

  // * Set after successfully contacting tracker */

  // * Changeable across game session */
  private volatile ServerIdentity identity;
  private volatile Map<String, String> playerMap;
  private volatile GameState gameState;
  private volatile Map<String, Long> lastSeenMap;

  public Game(String trackerAddr, String trackerPort, String playerName, ServerSocketChannel myServerSocket)
      throws RemoteException {
    super(trackerAddr, trackerPort, playerName);
    this.playerName = playerName;
    this.myServerSocket = myServerSocket;
    this.myAddr = myServerSocket.socket().getInetAddress().getHostAddress() + ":"
        + myServerSocket.socket().getLocalPort();
    this.lastSeenMap = new ConcurrentHashMap<>();
    this.lock = new ReentrantLock();

    System.out.println("[GAME] MY ADDR: " + this.myAddr);

    try {
      this.playerMap = this.register(myServerSocket.socket().getLocalPort());
      System.out.println("Player Map: " + this.playerMap);
      if (playerMap.size() == 1) {

        this.identity = ServerIdentity.PRIMARY;
        System.out.println("[GAME] Set identity to PRIMARY");
        this.gameState = new GameState(this.getGridSizeFromTracker(), this.getNumTreasuresFromTracker(),
            this.playerName, this.myAddr);
        System.out.println("[GAME] Initialised new GameState");

      } else if (playerMap.size() == 2) {

        this.identity = ServerIdentity.BACKUP;
        System.out.println("[GAME] Set identity to BACKUP");
        for (String k : this.playerMap.keySet()) {
          if (!k.equals(this.playerName)) {
            this.gameState = retrieveGameStateFromPrimary(convertStringToInetAddress(this.playerMap.get(k)), true);
            System.out.println("[GAME] Retrieved GameState from PRIMARY");
            break;
          }
        }

      } else {
        this.identity = ServerIdentity.NORMAL;

        boolean gameStateRetrieved = false;
        int MAX_ATTEMPT = 100;
        int CURR_ATTEMPT = 0;

        Set<String> deadPriServerAddr = new HashSet<String>();
        System.out.println("[GAME] Set identity to NORMAL");

        while (!gameStateRetrieved) {
          for (String k : this.playerMap.keySet()) {
            if (!k.equals(this.playerName)) {
              CURR_ATTEMPT += 1;
              String fetchedPrimaryAddr = null;
              try {
                fetchedPrimaryAddr = retrievePrimaryServerAddress(convertStringToInetAddress(this.playerMap.get(k)));
                System.out.println("[GAME] Found PRIMARY ADDR FROM RANDOM PLAYER:  " + fetchedPrimaryAddr);

                if (fetchedPrimaryAddr == null) {
                  continue;
                }

                if (deadPriServerAddr.contains(fetchedPrimaryAddr)) {
                  continue;
                }

                GameState fetchedGameState = retrieveGameStateFromPrimary(
                    convertStringToInetAddress(fetchedPrimaryAddr),
                    false);

                if (fetchedGameState == null) {
                  deadPriServerAddr.add(fetchedPrimaryAddr);
                  continue;
                } else {
                  this.gameState = fetchedGameState;
                  System.out.println("[GAME] Retrieved GameState from PRIMARY");
                  gameStateRetrieved = true;
                  break;
                }

              } catch (IOException e) {
                System.out.println("[GAME] Connection Error: " + e);
                deadPriServerAddr.add(fetchedPrimaryAddr);
                continue;
              }
            }
          }
          if (CURR_ATTEMPT >= MAX_ATTEMPT) {
            log("[GAME] Max Attempts Reached: Cannot find gamestate from any server, exiting...");
            break;

          }
        }
        System.out.println("[GAME] Register complete, game state fetched is: " + gameStateRetrieved);

      }
    } catch (UnknownHostException e) {
      System.err.println("UnknownHostException: " + e);
    } catch (Tile.TileAlreadyHasTreasureException | Tile.TileOccupiedException e) {
      System.err.println("Tile Exception: " + e);
    }
  }

  public String getPlayerName() {
    return this.playerName;
  }

  public String getMyAddr() {
    return this.myAddr;
  }

  public ReentrantLock getGameLock(){
    return this.lock;
  }

  public ServerSocketChannel getMyServer() {
    return this.myServerSocket;
  }

  public ServerIdentity getServerIdentity() {
    return this.identity;
  }

  public void setServerIdentity(ServerIdentity newIdentity) {
    this.identity = newIdentity;
  }

  public void setPlayerList(Map<String, String> newPlayerMap) {
    // * Process received data from tracker (probably some form of bytearray) */
    //
    this.playerMap = newPlayerMap;
  }

  protected Map<String, String> getPlayerMap() {
    return playerMap;
  }

  protected void setPlayerMap(Map<String, String> newPlayerMap) {
    this.playerMap = newPlayerMap;
  }

  public GameState getGameState() {
    synchronized (this) {
      return this.gameState;
    }
  }

  public void setGameState(GameState newGameState) {
    synchronized (this) {
      this.gameState = newGameState;
    }
  }

  public Map<String, Long> getLastSeenMap() {
    return this.lastSeenMap;
  }

  private static void log(String str) {
    System.out.println(str);
  }

  public void setLastSeenMap(String senderId) {
    // log("[GAME] PRIMARY SVR UPDATING LAST SEEN MAP FOR " + senderId);
    Long currentTime = System.currentTimeMillis();
    if (lastSeenMap.containsKey(senderId)) {
      lastSeenMap.replace(senderId, currentTime);
    } else {
      lastSeenMap.put(senderId, currentTime);
    }
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
      log("[GAME] convert from bytes classnotfound: " + e.getMessage());
      return null;
    }
  }

  public InetSocketAddress convertStringToInetAddress(String addr) {
    String[] priServerStrings = addr.split("\\:");
    InetSocketAddress iNetAddr = new InetSocketAddress(priServerStrings[0],
        Integer.parseInt(priServerStrings[1]));
    return iNetAddr;
  }

  public GameState retrieveGameStateFromPrimary(InetSocketAddress addr, boolean isBackup) {
    String message;

    if (isBackup) {
      message = "[06]|" + this.playerName + "|" + myAddr;
    } else {
      message = "[03]|" + this.playerName + "|" + myAddr;
    }

    GameState reply = null;
    SocketChannel channel;

    try {
      channel = SocketChannel.open(addr);
      byte[] messageBytes = convertObjToBytes(message);
      ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
      channel.write(buffer);
      log("[GAME] Request for gamestate sent");
      buffer.clear();
      buffer = ByteBuffer.allocate(1024 * 1024);
      buffer.clear();
      int read = channel.read(buffer);

      if (read == -1) {
        return null;
      }

      byte[] replyBytes = new byte[read];
      buffer.position(0);
      buffer.get(replyBytes);

      reply = (GameState) convertFromBytes(replyBytes);
      log("[GAME] Received game state: " + reply.getClass().getSimpleName());

    } catch (IOException e) {
      log("[GAME] IOException in retrieveGameStateFromPrimary: " + e.getMessage());
      e.printStackTrace();
    }

    return reply;

  }

  public String retrievePrimaryServerAddress(InetSocketAddress addr) throws IOException {

    log("[GAME] This is retrievePriSvrAdr: " + addr);

    String message = "[05]|" + this.playerName + "|";
    String reply = null;
    SocketChannel channel = null;

    channel = SocketChannel.open(addr);
    byte[] messageBytes = convertObjToBytes(message);
    ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
    channel.write(buffer);
    log("[GAME] Request for primary server addr sent");
    buffer.clear();
    buffer = ByteBuffer.allocate(1024 * 1024);
    buffer.clear();
    int read = channel.read(buffer);

    if (read == -1) {
      return null;
    }

    byte[] replyBytes = new byte[read];
    buffer.position(0);
    buffer.get(replyBytes);

    reply = (String) convertFromBytes(replyBytes);
    log("[GAME] Received pri server address: " + reply);

    channel.close();

    return reply;
  }

  private void updateBackupWithNewState() throws IOException {
    GameState latestGameState = this.getGameState();
    String message = "[07]";
    SocketChannel channel;

    try {
      InetSocketAddress backupServerAddr = this
          .convertStringToInetAddress(this.getGameState().getBackupServerAddr());
      channel = SocketChannel.open(backupServerAddr);

      byte[] msgBytes = convertObjToBytes(message);
      byte[] gameStateBytes = convertObjToBytes(latestGameState);
      byte[] completeMsgBytes = backupStateArrayMerger(msgBytes, gameStateBytes);

      ByteBuffer buffer = ByteBuffer.wrap(completeMsgBytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);

      }
      log("[GAME] BACKUP STATE SENT");
      buffer.clear();
    } catch (IOException e) {
      log("[GAME] Failed to write to backup with new state");
      e.printStackTrace();
    }

  }

  private byte[] backupStateArrayMerger(byte[] msg, byte[] gamestate) {
    int msglength = msg.length;
    int gamestatelength = gamestate.length;
    int newarrsize = msglength + gamestatelength;

    byte[] newMsgFrame = new byte[newarrsize];

    for (int i = 0; i < msglength; i = i + 1) {
      newMsgFrame[i] = msg[i];
    }

    for (int i = 0; i < gamestatelength; i = i + 1) {
      // Storing the elements in the
      // resultant array
      newMsgFrame[msglength + i] = gamestate[i];
    }

    return newMsgFrame;

  }

  public void setNewBackup() {
    for (String v : this.gameState.getPlayerList().values()) {
      try {
        if (!v.equals(this.gameState.getPrimaryServerAddr()) && !v.equals(this.gameState.getBackupServerAddr())) {
          this.gameState.updateBackupServer(v);
          System.out.println("[GAME] UPDATED BACKUP SERVER TO: " + v);
          this.updateBackupWithNewState();
          break;
        }
      } catch (IOException e) {
        System.out.println("[GAME] IOException while sending state to backup: " + v);
      }
    }
  }

  public static void main(String[] args) {

    // ! Pre-loading verifications
    if (args == null || args.length == 0 || args.length != 3) {
      log("[GAME] Please input 'IP Port PlayerId'");
      log("[GAME] Terminating program...");
      System.exit(0);
    }

    if (args[2].length() != 2) {
      log("[GAME] Please input only two characters as your player ID");
      log("[GAME] Terminating program...");
      System.exit(0);
    }

    // Initialize ServerSocketChannel to get IP&Port to register with tracker
    try {
      ServerSocketChannel myServer = ServerSocketChannel.open();

      // * Use the one with 0 for random port */
      String myIpAddress = InetAddress.getLocalHost().getHostAddress();
      InetSocketAddress serverAddress = new InetSocketAddress(myIpAddress, 0);
      myServer.socket().bind(serverAddress);
      myServer.configureBlocking(false);

      // * Get self IP and Port */
      Game game = new Game(args[0], args[1], args[2], myServer);

      ThreadPoolExecutor gameThreadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
      Runnable server = new Thread(new Server(game));

      // Runnable client = new Thread(new Client(game));
      Runnable ping = new Thread(new Ping(game));
      log("[GAME] Running Server thread");
      gameThreadPool.execute(server);
      log("[GAME] Running Client thread");
      // gameThreadPool.execute(client);
      log("[GAME] Running Ping thread");
      gameThreadPool.execute(ping);
      log("[GAME] Client, Server & Ping threads running...");

      GameApplication gameApplication = new GameApplication(game);
      Client client = new Client(gameApplication, game);
      gameApplication.init();
      Platform.startup(() -> {
        Stage stage = new Stage();
        try {
          gameApplication.start(stage);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      try {
        Thread.sleep(1000);
      } catch (Exception e) {
        e.printStackTrace();
      }
      gameThreadPool.execute(client);
      Map<String, Integer> chanceMap = new HashMap<>();
      while (true) {
        try {
          if (game.getServerIdentity() == ServerIdentity.PRIMARY) {
            System.out.println("[GAME] Updated playermap from tracker: " + game.playerMap);
            System.out.println("[GAME] Last Seen Map: " + game.getLastSeenMap());
            for (String player : game.getPlayerMap().keySet()) {
              if (game.lastSeenMap.containsKey(player)) {
                if (System.currentTimeMillis() - game.lastSeenMap.get(player) > 2000 && gameThreadPool.getActiveCount() == 3) {
                  System.out.println("[GAME] Evicting player by " + game.playerName + ": " + player);
                  game.setPlayerMap(game.evict(game.playerName, player));
                  game.getGameState().removePlayer(player);
                  game.lastSeenMap.remove(player);
                  game.updateBackupWithNewState();
                  log("[GAME] Player [" + player + "] evicted");
                }
              } else {
                if (!player.equals(game.playerName)) {
                  if (chanceMap.containsKey(player)) {
                    if (chanceMap.get(player) > 2) {
                      System.out.println("[GAME] Evicting stale player by " + game.playerName + ": " + player);
                      game.setPlayerMap(game.evict(game.playerName, player));
                      chanceMap.remove(player);
                    } else {
                      System.out.println("[GAME] Give chance to stale player by " + game.playerName + ": " + player);
                      chanceMap.put(player, chanceMap.get(player)+1);
                    }
                  } else {
                    chanceMap.put(player, 1);
                  }
                }
              }
            }
          }
//            for (String player : game.lastSeenMap.keySet()) {
//              if (System.currentTimeMillis() - game.lastSeenMap.get(player) > 1000) {
//                game.evict(game.playerName, player);
//                game.getGameState().removePlayer(player);
//                game.lastSeenMap.remove(player);
//                game.updateBackupWithNewState();
//                log("[GAME] Player [" + player + "] evicted");
//              }
//            }
//          }
          Thread.sleep(2000);
        } catch (Tile.NoPlayerOnTileException e) {
          log("[GAME] NoPlayerOnTileException: " + e);
        } catch (InterruptedException e) {
          log("[GAME] InterruptedException: " + e);
        }
      }

    } catch (RemoteException e) {
      log("[GAME] RemoteException thrown: " + e.getMessage());
    } catch (IOException e) {
      log("[GAME] IOException thrown: " + e.getMessage());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

}