package csmcompproj.assignment;

import java.io.Serializable;
import java.util.concurrent.locks.ReentrantLock;

public class Tile implements Serializable {

    private int x;
    private int y;
    private volatile boolean hasTreasure;
    private volatile String playerName;
    private ReentrantLock lock;

    public Tile(int x, int y) {
        this.x = x;
        this.y = y;
        this.hasTreasure = false;
        this.lock = new ReentrantLock();
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public boolean hasTreasure() {
        this.lock.lock();
        try {
            return this.hasTreasure;
        } finally {
            this.lock.unlock();
        }
    }

    public void placeTreasure() throws TileAlreadyHasTreasureException {
        this.lock.lock();
        try {
            if (this.hasTreasure) {
                throw new TileAlreadyHasTreasureException(this.getX(), this.getY());
            }
            this.hasTreasure = true;
        } finally {
            this.lock.unlock();
        }
    }

    public void removeTreasure() throws NoTreasureOnTileException {
        this.lock.lock();
        try {
            if (!this.hasTreasure) {
                throw new NoTreasureOnTileException(this.getX(), this.getY());
            }
            this.hasTreasure = false;
        } finally {
            this.lock.unlock();
        }
    }

    public boolean hasPlayer() {
        this.lock.lock();
        try {
            return playerName != null;
        } finally {
            this.lock.unlock();
        }
    }

    public void addPlayer(String playerName) throws TileOccupiedException {
        this.lock.lock();
        try {
            if (this.playerName != null) {
                throw new TileOccupiedException(this.getX(), this.getY(), this.playerName);
            }
            this.playerName = playerName;
        } finally {
            this.lock.unlock();
        }
    }

    public void removePlayer() throws NoPlayerOnTileException {
        this.lock.lock();
        try {
            if (this.playerName == null) {
                throw new NoPlayerOnTileException(this.getX(), this.getY());
            }
            this.playerName = null;
        } finally {
            this.lock.unlock();
        }
    }

    public String getPlayer() throws NoPlayerOnTileException {
        this.lock.lock();
        try {
            if (this.playerName == null) {
                throw new NoPlayerOnTileException(this.getX(), this.getY());
            }
            return this.playerName;
        } finally {
            this.lock.unlock();
        }
    }

    public class NoPlayerOnTileException extends Exception {
        public NoPlayerOnTileException(int X, int Y) {
            super(String.format("Tile [%d, %d] does not have any players!", X, Y));
        }
    }

    public class NoTreasureOnTileException extends Exception {
        public NoTreasureOnTileException(int X, int Y) {
            super(String.format("Tile [%d, %d] does not have treasure!", X, Y));
        }
    }

    public class TileOccupiedException extends Exception {
        public TileOccupiedException(int X, int Y, String playerName) {
            super(String.format("Tile [%d, %d] already occupied by '%s'", X, Y, playerName));
        }
    }

    public class TileAlreadyHasTreasureException extends Exception {
        public TileAlreadyHasTreasureException(int X, int Y) {
            super(String.format("Tile [%d, %d] already has treasure!", X, Y));
        }
    }
}
