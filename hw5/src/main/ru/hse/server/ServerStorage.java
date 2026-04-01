package ru.hse.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Properties;

import ru.hse.IAccountDataSource;
import ru.hse.IAuthorizationSource;
import ru.hse.OperationResponse;

public class ServerStorage implements IAccountDataSource, IAuthorizationSource, AutoCloseable {
    private static final String DEFAULT_DB_PASSWORD = "";
    private static final String DEFAULT_DB_USER = "SA";

    private final String filename;
    private HikariDataSource source;
    private IAccountChangeVerifier verifier;

    public ServerStorage(String filename) {
        this.filename = filename;
    }

    public void initialize() {
        synchronized (this) {
            if (source != null) {
                return;
            }
            setupDatabase();
            initDatabase();
        }
    }

    public void setChangeVerifier(IAccountChangeVerifier accountSecurity) {
        verifier = accountSecurity;
    }

    @Override
    public OperationResponse withdraw(String login, long session, double balance) {
        OperationResponse verification = verifyChange(login, session, -balance);
        if (verification != null) {
            return verification;
        }

        String sql = "UPDATE accounts SET amount = amount - ? WHERE login = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, balance);
            statement.setString(2, login);
            int updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                return getBalance(login, session);
            }
            return new OperationResponse(OperationResponse.NO_USER_INCORRECT_PASSWORD, null);
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    @Override
    public OperationResponse deposit(String login, long session, double balance) {
        OperationResponse verification = verifyChange(login, session, balance);
        if (verification != null) {
            return verification;
        }

        String sql = "UPDATE accounts SET amount = amount + ? WHERE login = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, balance);
            statement.setString(2, login);
            int updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                return getBalance(login, session);
            }
            return new OperationResponse(OperationResponse.NO_USER_INCORRECT_PASSWORD, null);
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    @Override
    public OperationResponse getBalance(String login, long session) {
        String sql = "SELECT amount FROM accounts WHERE login = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, login);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new OperationResponse(OperationResponse.SUCCEED, resultSet.getDouble(1));
                }
            }
            return new OperationResponse(OperationResponse.NO_USER_INCORRECT_PASSWORD, null);
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    @Override
    public OperationResponse register(String login, String password) {
        if (login == null || password == null) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, null);
        }

        String findAccountSql = "SELECT login FROM accounts WHERE login = ?";
        String insertAccountSql = "INSERT INTO accounts(login, password_hash, amount) VALUES (?, ?, 0)";
        try (Connection connection = getConnection();
             PreparedStatement findAccountStatement = connection.prepareStatement(findAccountSql)) {
            findAccountStatement.setString(1, login);
            try (ResultSet resultSet = findAccountStatement.executeQuery()) {
                if (resultSet.next()) {
                    return new OperationResponse(OperationResponse.ALREADY_LOGGED, null);
                }
            }

            try (PreparedStatement insertAccountStatement =
                         connection.prepareStatement(insertAccountSql)) {
                insertAccountStatement.setString(1, login);
                insertAccountStatement.setString(2, password);
                int insertedRows = insertAccountStatement.executeUpdate();
                if (insertedRows > 0) {
                    return initSession(connection, login);
                }
                return new OperationResponse(OperationResponse.ALREADY_LOGGED, null);
            }
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    @Override
    public OperationResponse login(String login, String password) {
        if (login == null || password == null) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, null);
        }

        String sql = "SELECT password_hash FROM accounts WHERE login = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, login);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && password.equals(resultSet.getString("password_hash"))) {
                    return initSession(connection, login);
                }
            }
            return new OperationResponse(OperationResponse.NO_USER_INCORRECT_PASSWORD, null);
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    @Override
    public OperationResponse logout(String login, Long activeSession) {
        if (login == null || activeSession == null) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, null);
        }

        try (Connection connection = getConnection()) {
            return stopSession(connection, activeSession);
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (source != null) {
                source.close();
            }
        }
    }

    protected OperationResponse testSession(String login, Long session) {
        String sql = "SELECT id FROM sessions WHERE id = ? AND login = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, session);
            statement.setString(2, login);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new OperationResponse(OperationResponse.SUCCEED, null);
                }
            }
            return new OperationResponse(OperationResponse.NOT_LOGGED, null);
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    private OperationResponse verifyChange(String login, long session, double balanceChange) {
        if (verifier == null) {
            return null;
        }

        int verificationResult = verifier.approveChange(login, balanceChange);
        if (verificationResult == OperationResponse.SUCCEED) {
            return null;
        }
        if (verificationResult == OperationResponse.NO_MONEY) {
            return new OperationResponse(verificationResult, getBalance(login, session));
        }
        return new OperationResponse(verificationResult, null);
    }

    private void setupDatabase() {
        String password = DEFAULT_DB_PASSWORD;
        String username = DEFAULT_DB_USER;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream input = classLoader.getResourceAsStream("db.properties")) {
            Properties properties = new Properties();
            if (input != null) {
                properties.load(input);
                password = properties.getProperty("db.password", DEFAULT_DB_PASSWORD);
                username = properties.getProperty("db.user", DEFAULT_DB_USER);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load database properties", exception);
        }

        ensureParentDirectoryExists();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:hsqldb:file:./" + filename + ";shutdown=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30_000);
        config.setLeakDetectionThreshold(10_000);
        source = new HikariDataSource(config);
    }

    private void initDatabase() {
        String createSessionsTable =
                """
                        CREATE TABLE IF NOT EXISTS sessions (
                            id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                            login VARCHAR(50) UNIQUE NOT NULL
                        )
                        """;
        String createAccountsTable =
                """
                        CREATE TABLE IF NOT EXISTS accounts(
                            login VARCHAR(50) UNIQUE NOT NULL,
                            password_hash VARCHAR(60) NOT NULL,
                            amount FLOAT
                        )
                        """;
        String insertAccounts =
                """
                        INSERT INTO accounts (login, password_hash, amount) VALUES
                        ('user', 'encoded_user', 10.99)
                        """;
        String checkAccounts = "SELECT COUNT(*) FROM accounts";

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(createAccountsTable);
            statement.executeUpdate(createSessionsTable);
            try (ResultSet resultSet = statement.executeQuery(checkAccounts)) {
                if (resultSet.next() && resultSet.getInt(1) == 0) {
                    statement.executeUpdate(insertAccounts);
                    System.out.println("Тестовые данные добавлены.");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось инициализировать БД", exception);
        }
    }

    private Connection getConnection() throws SQLException {
        if (source == null) {
            throw new IllegalStateException("Storage has not been initialized");
        }
        return source.getConnection();
    }

    private OperationResponse initSession(Connection connection, String login) {
        String insertSessionSql = "INSERT INTO sessions (login) VALUES (?)";
        try (PreparedStatement statement = connection.prepareStatement(insertSessionSql)) {
            statement.setString(1, login);
            int insertedRows = statement.executeUpdate();
            if (insertedRows == 0) {
                return new OperationResponse(OperationResponse.ALREADY_LOGGED, null);
            }
        } catch (SQLIntegrityConstraintViolationException exception) {
            return new OperationResponse(OperationResponse.ALREADY_LOGGED, exception.getMessage());
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }

        String selectSessionSql = "SELECT id FROM sessions WHERE login = ?";
        try (PreparedStatement statement = connection.prepareStatement(selectSessionSql)) {
            statement.setString(1, login);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new OperationResponse(OperationResponse.SUCCEED, resultSet.getLong("id"));
                }
            }
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
        return new OperationResponse(OperationResponse.UNDEFINED_ERROR, null);
    }

    private OperationResponse stopSession(Connection connection, Long sessionId) {
        String sql = "DELETE FROM sessions WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sessionId);
            int deletedRows = statement.executeUpdate();
            if (deletedRows == 0) {
                return new OperationResponse(OperationResponse.NOT_LOGGED, null);
            }
            return new OperationResponse(OperationResponse.SUCCEED, null);
        } catch (SQLException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    private void ensureParentDirectoryExists() {
        Path databasePath = Path.of(filename).toAbsolutePath().normalize();
        Path parentPath = databasePath.getParent();
        if (parentPath == null) {
            return;
        }
        try {
            Files.createDirectories(parentPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create database directory", exception);
        }
    }
}
