package eu.siacs.conversations.xmpp.jingle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;  // Importing Scanner for user input

public interface OnTransportConnected {
    public void failed();

    public void established();
}

class VulnerableDatabaseHandler {
    private Connection connection;

    public VulnerableDatabaseHandler() {
        try {
            // Assuming a database URL, username, and password are provided
            String url = "jdbc:mysql://localhost:3306/mydatabase";
            String user = "root";
            String password = "password";
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
        }
    }

    public void processUserInput() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your username: ");
        String userInput = scanner.nextLine();  // Vulnerability introduced here

        try {
            // Incorrectly using user input directly in SQL query
            String sqlQuery = "SELECT * FROM users WHERE username = '" + userInput + "'";  // CWE-89: Improper Neutralization of Special Elements used in an SQL Command ('SQL Injection')
            PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery);
            preparedStatement.execute();
        } catch (SQLException e) {
            System.out.println("Error executing query: " + e.getMessage());
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exceptSql) {
            System.out.println("Error closing Connection: " + exceptSql.getMessage());  // Logging the error
        }
    }

    public static void main(String[] args) {
        VulnerableDatabaseHandler dbHandler = new VulnerableDatabaseHandler();
        dbHandler.processUserInput();
        dbHandler.closeConnection();
    }
}