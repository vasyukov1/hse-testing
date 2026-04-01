package ru.hse.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import ru.hse.Account;
import ru.hse.IAccountDataSource;
import ru.hse.IAuthorizationSource;
import ru.hse.OperationException;
import ru.hse.OperationResponse;

public class AccountServer {
    private final Lock accountsLock = new ReentrantLock();
    private final Map<String, Account> activeAccounts = new HashMap<>();
    private final IAccountDataSource dataSource;
    private final IAuthorizationSource authSource;

    public AccountServer(IAuthorizationSource authorizationSource, IAccountDataSource accountData) {
        authSource = Objects.requireNonNull(authorizationSource, "Authorization source is required");
        dataSource = Objects.requireNonNull(accountData, "Account data source is required");
        Runtime.getRuntime().addShutdownHook(new Thread(this::logoutAllSilently));
    }

    public Account register(String login, String password) throws OperationException {
        if (login == null || password == null) {
            throw new OperationException(OperationResponse.NULL_ARGUMENT_EXCEPTION);
        }

        accountsLock.lock();
        try {
            if (activeAccounts.containsKey(login)) {
                throw new OperationException(OperationResponse.ALREADY_INITIATED_RESPONSE);
            }

            OperationResponse response = authSource.register(login, password);
            if (response.code != OperationResponse.SUCCEED) {
                throw new OperationException(response);
            }

            Account account = createActiveAccount(login, response.body);
            activeAccounts.put(login, account);
            return account;
        } finally {
            accountsLock.unlock();
        }
    }

    public Account login(String login, String password) throws OperationException {
        if (login == null || password == null) {
            throw new OperationException(
                    new OperationResponse(OperationResponse.NO_USER_INCORRECT_PASSWORD, null));
        }

        accountsLock.lock();
        try {
            Account activeAccount = activeAccounts.get(login);
            if (activeAccount != null) {
                throw new OperationException(
                        new OperationResponse(
                                OperationResponse.ALREADY_LOGGED, activeAccount.getActiveSession()));
            }

            OperationResponse response = authSource.login(login, password);
            if (response.code != OperationResponse.SUCCEED) {
                throw new OperationException(response);
            }

            Account account = createActiveAccount(login, response.body);
            activeAccounts.put(login, account);
            return account;
        } finally {
            accountsLock.unlock();
        }
    }

    public Account testSession(String login, Long session) {
        if (login == null || session == null) {
            return null;
        }

        accountsLock.lock();
        try {
            Account activeAccount = activeAccounts.get(login);
            if (activeAccount != null && Objects.equals(activeAccount.getActiveSession(), session)) {
                return activeAccount;
            }
            return null;
        } finally {
            accountsLock.unlock();
        }
    }

    public void logout(Account account) throws OperationException {
        if (account == null || account.getLogin() == null) {
            throw new OperationException(
                    new OperationResponse(OperationResponse.NO_USER_INCORRECT_PASSWORD, null));
        }

        accountsLock.lock();
        try {
            Account activeAccount = activeAccounts.get(account.getLogin());
            if (activeAccount == null) {
                throw new OperationException(
                        new OperationResponse(OperationResponse.NO_USER_INCORRECT_PASSWORD, null));
            }

            OperationResponse response =
                    authSource.logout(activeAccount.getLogin(), activeAccount.getActiveSession());
            if (response.code != OperationResponse.SUCCEED) {
                throw new OperationException(response);
            }

            activeAccount.clearSession();
            activeAccounts.remove(activeAccount.getLogin());
        } finally {
            accountsLock.unlock();
        }
    }

    private Account createActiveAccount(String login, Object rawSessionId) throws OperationException {
        Long sessionId = parseSessionId(rawSessionId);
        if (sessionId == null) {
            throw new OperationException(
                    new OperationResponse(OperationResponse.INCORRECT_RESPONSE, rawSessionId));
        }

        Account account = new Account(login);
        account.initDataStorage(dataSource);
        account.activateSession(sessionId);
        return account;
    }

    private Long parseSessionId(Object rawSessionId) {
        if (rawSessionId instanceof Long sessionId) {
            return sessionId;
        }
        if (rawSessionId instanceof Integer sessionId) {
            return sessionId.longValue();
        }
        if (rawSessionId instanceof String sessionId) {
            try {
                return Long.parseLong(sessionId);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void logoutAllSilently() {
        List<Account> accountsToLogout;
        accountsLock.lock();
        try {
            accountsToLogout = new ArrayList<>(activeAccounts.values());
        } finally {
            accountsLock.unlock();
        }

        for (Account account : accountsToLogout) {
            try {
                logout(account);
            } catch (OperationException exception) {
                System.err.println(exception);
            }
        }
    }
}
