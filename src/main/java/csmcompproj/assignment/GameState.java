package csmcompproj.assignment;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class GameState implements Serializable {

    public volatile int gridSize; // N
    public volatile int numTreasures; // K
    private volatile int maxPlayers; // N*N - K - 1
    private volatile String primaryServerAddr;
    private volatile String backupServerAddr;
    private volatile Map<String, String> playerList; // name to ip
    private volatile Map<String, Integer[]> playerPosition; // name to pos
    private volatile Map<String, Integer> playerScore; // name to score
    public volatile Tile[][] grid;
    public volatile Set<Tile> freeTiles;

    private ReentrantLock lock;

    public GameState(int n, int k, String playerName, String serverAddr) throws
            UnknownHostException,
            Tile.TileAlreadyHasTreasureException,
            Tile.TileOccupiedException {
        this.lock = new ReentrantLock();
        this.gridSize = n;
        this.numTreasures = k;
        this.maxPlayers = n * n - k - 1;
        this.primaryServerAddr = serverAddr;
        this.grid = new Tile[n][n];
        this.playerList = new ConcurrentHashMap<>();
        this.playerPosition = new ConcurrentHashMap<>();
        this.playerScore = new ConcurrentHashMap<>();
        this.freeTiles = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                this.grid[i][j] = new Tile(i, j);
                this.freeTiles.add(grid[i][j]);
            }
        }

        for (int i = 0; i < k; i++) {
            this.generateTreasure();
        }

        this.addPlayer(playerName, this.primaryServerAddr);

    }

    public int getGridSize() {
        return this.gridSize;
    }

    private Tile popRandomTile() {
//        this.lock.lock();
        try {
            int randomIdx = new Random().nextInt(this.freeTiles.size());
            Tile chosen = (Tile) this.freeTiles.toArray()[randomIdx];
            this.freeTiles.remove(chosen);
            return chosen;
        } finally {
//            this.lock.unlock();
        }
    }

    private void generateTreasure() throws Tile.TileAlreadyHasTreasureException {
//        this.lock.lock();
        try {
            Tile randomTile = this.popRandomTile();
            randomTile.placeTreasure();
        } finally {
//            this.lock.unlock();
        }
    }

    private void addPlayerScore(String playerName) {
//        this.lock.lock();
        try {
            this.playerScore.put(playerName, this.playerScore.get(playerName) + 1);
        } finally {
//            this.lock.unlock();
        }
    }

    public void addPlayer(String playerName, String playerAddr) throws Tile.TileOccupiedException {
//        this.lock.lock();
        try {
            this.playerList.put(playerName, playerAddr);
            this.playerScore.put(playerName, 0);
            Tile randomTile = this.popRandomTile();
            this.playerPosition.put(playerName, new Integer[]{randomTile.getX(), randomTile.getY()});
            randomTile.addPlayer(playerName);
        } finally {
//            this.lock.unlock();
        }
    }

    public void removePlayer(String playerName) throws Tile.NoPlayerOnTileException {
//        this.lock.lock();
        try {
            Integer[] playerPos = this.playerPosition.get(playerName);
            Tile playerTile = this.grid[playerPos[0]][playerPos[1]];

            this.playerList.remove(playerName);
            this.playerScore.remove(playerName);
            this.playerPosition.remove(playerName);
            playerTile.removePlayer();
            this.freeTiles.add(playerTile);
            System.out.println("[GAMESTATE] Player " + playerName + " deleted successfully");
        } finally {
//            this.lock.unlock();
        }
    }

    public void movePlayer(String playerName, int direction) throws Tile.TileOccupiedException,
            Tile.NoPlayerOnTileException,
            Tile.NoTreasureOnTileException,
            Tile.TileAlreadyHasTreasureException,
            UnrecognisedDirectionException,
            MoveOutOfRangeException {
        System.out.println("move player " + playerName + "; holdcount: " + this.lock.getHoldCount());
//        this.lock.lock();
        System.out.println("move player " + playerName + "; islocked: " + this.lock.isHeldByCurrentThread());
        try {
            Integer[] currentPos = this.playerPosition.get(playerName);
            Tile currentTile = this.grid[currentPos[0]][currentPos[1]];

            int newX = currentPos[0], newY = currentPos[1];

            switch (direction) {
                case 1:
                    newX -= 1;
                    break;
                case 2:
                    newY -= 1;
                    break;
                case 3:
                    newX += 1;
                    break;
                case 4:
                    newY += 1;
                    break;
                default:
                    throw new UnrecognisedDirectionException();
            }

            if (newX < 0 || newX >= this.gridSize || newY < 0 || newY >= this.gridSize) {
                throw new MoveOutOfRangeException();
            }

            Tile newTile = this.grid[newX][newY];

            assert currentTile.getPlayer() == playerName;

            int count = 0;
//           Added cuz somehow multiple threads are here, while loop until other thread finishes updating map
             while(!currentTile.getPlayer().equals(playerName)){
                 if (count >= 10) break;
//                 currentPos = this.playerPosition.get(playerName);
//                 currentTile = this.grid[currentPos[0]][currentPos[1]];
                 Thread.sleep(30);
                 count++;
             }

            // add/remove player name to/from tile
            newTile.addPlayer(playerName);
            currentTile.removePlayer();

            // update free tiles
            this.freeTiles.remove(newTile);
            this.freeTiles.add(currentTile);

            // update player pos
            this.playerPosition.put(playerName, new Integer[]{newX, newY});

            // check for treasure in new pos
            if (newTile.hasTreasure()) {
                newTile.removeTreasure();
                this.generateTreasure();
                this.addPlayerScore(playerName);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
//            this.lock.unlock();
        }

        // ! Test code
        // System.out.println("[GAMESTATE] Player " + playerName + " moved successfully");
        // System.out.println("[GAMESTATE] Old Pos: " + Arrays.toString(currentPos));
        // System.out.println("[GAMESTATE] New Pos: [" + newX + ", " + newY + "]");
    }

    public Map<String, String> getPlayerList() {
        return this.playerList;
    }

    public Map<String, Integer> getPlayerScore() {
        return this.playerScore;
    }

    public String getPrimaryServerAddr() {
        return this.primaryServerAddr;
    }

    public void updatePrimaryServer(String primaryServerAddr) {
//        this.lock.lock();
        try {
            this.primaryServerAddr = primaryServerAddr;
        } finally {
//            this.lock.unlock();
        }
    }

    public String getBackupServerAddr() {
        return this.backupServerAddr;
    }

    public void updateBackupServer(String backupServerAddr) {
        this.backupServerAddr = backupServerAddr;
    }

    public class UnrecognisedDirectionException extends Exception {
        public UnrecognisedDirectionException() {
            super("Unrecognised direction!");
        }
    }

    public class MoveOutOfRangeException extends Exception {
        public MoveOutOfRangeException() {
            super("Move is out of range!");
        }
    }
}
