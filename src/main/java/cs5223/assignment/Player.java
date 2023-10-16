package cs5223.assignment;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class Player implements Runnable {

  private SelectionKey key;
  private SocketChannel channel;
  private Game gameRef;
  private ByteBuffer buffer;
  private String id;
  private String action;
  private byte[] reply;
  private String[] fullMsg;
  private ReentrantLock lock;

  // ! Test code
  private GameState testState;

  public Player(String id, SelectionKey key, String action, Game gameRef, String[] fullMsg) {
    this.key = key;
    this.id = id;
    this.channel = (SocketChannel) key.channel();
    this.buffer = (ByteBuffer) key.attachment();
    this.gameRef = gameRef;
    this.action = action;
    this.fullMsg = fullMsg;
    this.reply = new byte[1024 * 1024];
    this.lock = gameRef.getGameLock();
  }

  private static void log(String str) {
    System.out.println(str);
  }

  private int getDirection(String command) {
    switch (command) {
      case "MOVELEFT":
        return 1;
      case "MOVEDOWN":
        return 2;

      case "MOVERIGHT":
        return 3;

      case "MOVEUP":
        return 4;

      default:
        return 0;
    }
  }

  private byte[] convertObjToBytes(Object obj) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(obj);
      reply = bos.toByteArray();
      return bos.toByteArray();
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

  private byte[] responseArrayMerger(byte[] msgLen, byte[] msg, byte[] gamestate) {
    int msgLengthlength = msgLen.length;
    int msglength = msg.length;
    int gamestatelength = gamestate.length;
    int newarrsize = msgLengthlength + msglength + gamestatelength;

    byte[] newMsgFrame = new byte[newarrsize];

    for (int i = 0; i < msgLengthlength; i = i + 1) {
      newMsgFrame[i] = msgLen[i];
    }

    for (int i = 0; i < msglength; i = i + 1) {
      newMsgFrame[msgLengthlength + i] = msg[i];
    }

    for (int i = 0; i < gamestatelength; i = i + 1) {
      // Storing the elements in the
      // resultant array
      newMsgFrame[msgLengthlength + msglength + i] = gamestate[i];
    }

    return newMsgFrame;

  }

  private void updateBackupWithNewState() throws IOException {
    GameState latestGameState = gameRef.getGameState();
    String message = "[07]";
    SocketChannel channel;

    try {
      InetSocketAddress backupServerAddr = gameRef
          .convertStringToInetAddress(gameRef.getGameState().getBackupServerAddr());
      channel = SocketChannel.open(backupServerAddr);

      byte[] msgBytes = convertObjToBytes(message);
      byte[] gameStateBytes = convertObjToBytes(latestGameState);
      byte[] completeMsgBytes = backupStateArrayMerger(msgBytes, gameStateBytes);

      ByteBuffer buffer = ByteBuffer.wrap(completeMsgBytes);
      while (buffer.hasRemaining()) {
        channel.write(buffer);

      }
      log("[PLAYER THREAD PRIMARY] BACKUP STATE SENT");
      buffer.clear();
    } catch (IOException e) {
      log("[PLAYER THREAD] Failed to write to backup with new state");
      e.printStackTrace();
    }

  }

  private void sendNormalResponse(SocketChannel client) throws IOException {

    GameState latestGameState = gameRef.getGameState();
    boolean isWritable = false;

    while (!isWritable) {

      if (key.isWritable() && latestGameState != null) {
        // log("[PLAYER] sendResponse writable now");
        isWritable = true;

        byte[] messageBytes = new byte[1024 * 1024];

        messageBytes = convertObjToBytes(latestGameState);
        // log("[PLAYER] GameState converted to bytes");

        ByteBuffer testBuffer = ByteBuffer.wrap(messageBytes);

        while (testBuffer.hasRemaining()) {
          client.write(testBuffer);
        }
        testBuffer.clear();
        key.interestOps(SelectionKey.OP_READ);
        // log("[PLAYER] Yay done sending gamestate");
      }

    }

  }

  private void sendResponse(SocketChannel client, String msg) throws IOException {

    boolean isWritable = false;

    while (!isWritable) {

      if (key.isWritable()) {

        GameState latestGameState = gameRef.getGameState();

        if (latestGameState == null) {
          log("[PLAYER] SendResponse: Somehow game state is null");
          continue;
        }

        // log("[PLAYER] sendResponse writable now");
        isWritable = true;

        byte[] messageBytes = new byte[1024 * 1024];

        byte[] msgBytes = convertObjToBytes(msg);
        Integer msgBytesLength = msgBytes.length;
        byte[] msgLenBytes = convertObjToBytes(msgBytesLength);
        byte[] gameStateBytes = convertObjToBytes(latestGameState);

        messageBytes = responseArrayMerger(msgLenBytes, msgBytes, gameStateBytes);

        ByteBuffer testBuffer = ByteBuffer.wrap(messageBytes);

        while (testBuffer.hasRemaining()) {
          client.write(testBuffer);
        }
        testBuffer.clear();
        key.interestOps(SelectionKey.OP_READ);

      }

    }

  }

  private void sendPriServerAddrResponse(SocketChannel client) throws IOException {
    // ! Test Code

    GameState latestGameState = gameRef.getGameState();

    boolean isWritable = false;

    while (!isWritable) {

      if (key.isWritable() && latestGameState != null) {
        // log("[PLAYER] sendPriServerAddr writable now");
        isWritable = true;

        byte[] messageBytes = new byte[1024 * 1024];

        messageBytes = convertObjToBytes(latestGameState.getPrimaryServerAddr());
        // log("[PLAYER] Pri Server Addr converted to bytes");

        ByteBuffer testBuffer = ByteBuffer.wrap(messageBytes);

        while (testBuffer.hasRemaining()) {
          client.write(testBuffer);
        }
        testBuffer.clear();
        key.interestOps(SelectionKey.OP_READ);
        // System.out.println("[PLAYER] Yay done sending pri server addr");
      }

    }

  }

  @Override
  public void run() {

    switch (action) {

      case "[00]":

//        this.lock.lock();

        try {
          sendResponse(channel, "Game Updated Successfully");
        } catch (IOException e) {
          log("[PLAYER UPDATE] IOException: " + e.getMessage());
          e.printStackTrace();
        } finally {
          System.out.println("[PLAYER LOCK] Unlocked lock after updating");
//          this.lock.unlock();
        }
        break;

      case "[02]":
//        this.lock.lock();
        // log("[PLAYER] Yo Move");
        int direction = getDirection(fullMsg[2]);
        try {
          gameRef.getGameState().movePlayer(id, direction);
        } catch (Tile.TileOccupiedException e) {
          log("[PLAYER MOVE] Error: " + e.getMessage());
          try {
            System.out.println("[PLAYER LOCK] Unlocked lock after failed occupied move");

//            this.lock.unlock();
            sendResponse(channel, "Tile is already Occupied!");
            if (!gameRef.getGameState().getPlayerList().get(id)
                .equals(gameRef.getGameState().getBackupServerAddr())) {
              updateBackupWithNewState();
            }
          } catch (IOException err) {
            log("[PLAYER MOVE] IOException: " + err.getMessage());
            err.printStackTrace();
          }
        } catch (Tile.NoPlayerOnTileException e) {
          log("[PLAYER MOVE] Error: " + e.getMessage());
        } catch (Tile.NoTreasureOnTileException e) {
          log("[PLAYER MOVE] Error: " + e.getMessage());
        } catch (Tile.TileAlreadyHasTreasureException e) {
          log("[PLAYER MOVE] Error: " + e.getMessage());
        } catch (GameState.UnrecognisedDirectionException e) {
          log("[PLAYER MOVE] Error: " + e.getMessage());
        } catch (GameState.MoveOutOfRangeException e) {
          log("[PLAYER MOVE] Error: " + e.getMessage());
          System.out.println("[PLAYER LOCK] Unlocked lock after out of range move");
//          this.lock.unlock();
          try {
            sendResponse(channel, "Move is Out Of Range!");
            if (!gameRef.getGameState().getPlayerList().get(id)
                .equals(gameRef.getGameState().getBackupServerAddr())) {
//              updateBackupWithNewState();
            }
          } catch (IOException err) {
            log("[PLAYER MOVE] IOException: " + err.getMessage());
            err.printStackTrace();
          } finally {
            System.out.println("[PLAYER LOCK] Unlocked lock after IOException ");
          }
        } finally {
          System.out.println("[PLAYER LOCK] Unlocking Lock after processing move");
//          this.lock.unlock();
          try {
            sendResponse(channel, "Moved Successfully");
            updateBackupWithNewState();
          } catch (IOException e) {
            log("[PLAYER MOVE] IOException: " + e.getMessage());
            e.printStackTrace();
          }
        }

        break;
      case "[03]":
        // ? [03] is register as normal player
//        this.lock.lock();
        try {
          gameRef.getGameState().addPlayer(id, fullMsg[2]);
          gameRef.setLastSeenMap(id);
          gameRef.setPlayerMap(gameRef.getPlayerMapFromTracker());
          sendNormalResponse(channel);
          updateBackupWithNewState();
        } catch (IOException e) {
          log("[PLAYER UPDATE] IOException: " + e.getMessage());
          e.printStackTrace();
        } catch (Tile.TileOccupiedException e) {
          throw new RuntimeException(e);
        } finally {
//          this.lock.unlock();
        }
        break;
      case "[06]":
        try {
          String backupServerAddr = fullMsg[2];
          gameRef.getGameState().addPlayer(id, fullMsg[2]);
          gameRef.setLastSeenMap(id);
          gameRef.setPlayerMap(gameRef.getPlayerMapFromTracker());
          gameRef.getGameState().updateBackupServer(backupServerAddr);
          sendNormalResponse(channel);

        } catch (IOException e) {
          log("[PLAYER UPDATE] IOException: " + e.getMessage());
          e.printStackTrace();
        } catch (Tile.TileOccupiedException e) {
          throw new RuntimeException(e);
        }
        break;

      case "[05]":
        // log("[PLAYER] Yo Get Primary");
        try {
          sendPriServerAddrResponse(channel);
        } catch (IOException e) {
          log("[PLAYER GETPRIMARY] IOException: " + e.getMessage());
          e.printStackTrace();
        }
        break;
      default:
        break;
    }

  }
}