package org.example.pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class BasicConnectionPool implements ConnectionPool {

    private final String url;
    private final String user;
    private final String password;
    private final int maxSize;

    private final BlockingQueue<Connection> availableConnections;
    private final List<Connection> allConnections;

    private volatile boolean isShutdown = false;

    /**
     * Creates a new Connection Pool.
     *
     * @param url       the database URL
     * @param user      the database user
     * @param password  the database password
     * @param initialSize the initial number of connections
     * @param maxSize   the maximum number of connections
     * @throws SQLException if there is an error creating the initial connections
     */
    private BasicConnectionPool(String url, String user, String password, int initialSize, int maxSize) throws SQLException {
        if (initialSize <= 0 || maxSize <= 0 || initialSize > maxSize) {
            throw new IllegalArgumentException("Invalid pool size parameters.");
        }
        this.url = url;
        this.user = user;
        this.password = password;
        this.maxSize = maxSize;

        this.availableConnections = new LinkedBlockingQueue<>(maxSize);
        this.allConnections = new ArrayList<>(maxSize);

        // "Prime the pump" by creating the initial connections
        for (int i = 0; i < initialSize; i++) {
            Connection connection = createNewConnection();
            availableConnections.offer(connection); // Use offer, it won't block
            allConnections.add(connection);
        }
    }

    public static BasicConnectionPool create(String url, String user, String password, int initialSize, int maxSize) throws SQLException {
        return new BasicConnectionPool(url, user, password, initialSize, maxSize);
    }

    /**
     * Borrows a connection from the pool.
     *
     * @return a Connection from the pool.
     * @throws SQLException if a database access error occurs or the pool is shut down.
     */
    @Override
    public Connection getConnection() throws SQLException {
        if (isShutdown) {
            throw new SQLException("Connection pool has been shut down.");
        }

        try {
            return availableConnections.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a connection.", e);
        }
    }

    /**
     * Returns a connection to the pool.
     *
     * @param connection the Connection to return.
     * @return true if the connection was returned successfully, false otherwise.
     */
    @Override
    public boolean releaseConnection(Connection connection) {
        if (isShutdown || connection == null) {
            return false;
        }

        try {
            // Check if the connection is still valid before returning it.
            if (connection.isClosed() || !connection.isValid(1)) {
                System.err.println("Released a broken connection. Discarding and replacing.");
                allConnections.remove(connection);
                Connection newConnection = createNewConnection();
                allConnections.add(newConnection);
                return availableConnections.offer(newConnection);
            } else {
                return availableConnections.offer(connection);
            }
        } catch (SQLException e) {
            System.err.println("Error while validating or replacing a connection.");
            allConnections.remove(connection);
            return false;
        }
    }

    /**
     * Shuts down the pool and closes all connections.
     */
    @Override
    public void shutdown() throws SQLException {
        System.out.println("Shutting down connection pool...");
        isShutdown = true;

        SQLException exceptions = null;
        for (Connection connection : allConnections) {
            try {
                connection.close();
            } catch (SQLException e) {
                if(exceptions == null) {
                    exceptions = new SQLException("Errors occurred during shutdown.");
                }
                exceptions.setNextException(e);
            }
        }
        allConnections.clear();
        availableConnections.clear();
        System.out.println("Connection pool shut down successfully.");

        if(exceptions != null) {
            throw exceptions;
        }
    }

    private Connection createNewConnection() throws SQLException {
        return DriverManager.getConnection(this.url, this.user, this.password);
    }

}