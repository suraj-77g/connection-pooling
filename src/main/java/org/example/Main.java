package org.example;

import org.example.pool.BasicConnectionPool;
import org.example.pool.ConnectionPool;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class Main {
    public static void main(String[] args) throws SQLException {
        // Note: You need a JDBC driver in your classpath. E.g., H2 database driver.
        // The H2 in-memory database is great for testing.
        String dbUrl = "jdbc:h2:mem:testdb";
        String user = "sa";
        String password = "";

        ConnectionPool pool = null;
        try {
            // Create the pool with an initial size of 2 and max size of 5
            pool = BasicConnectionPool.create(dbUrl, user, password, 2, 5);

            System.out.println("Pool created. Getting a connection...");

            // --- The Correct Usage Pattern ---
            Connection conn = null;
            try {
                conn = pool.getConnection(); // Borrow a connection
                System.out.println("Connection acquired. Executing a query...");

                // Use the connection
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT 1+1");

                if (rs.next()) {
                    System.out.println("Query result: " + rs.getInt(1));
                }

            } finally {
                if (conn != null) {
                    pool.releaseConnection(conn); // ALWAYS return the connection in a finally block
                    System.out.println("Connection released back to the pool.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (pool != null) {
                try {
                    pool.shutdown();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}