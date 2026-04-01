package ru.hse.client;

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import ru.hse.Account;
import ru.hse.IAccountDataSource;
import ru.hse.IAuthorizationSource;
import ru.hse.OperationException;
import ru.hse.OperationResponse;

public class AccountManager {
    private final IAuthorizationSource serverAuthData;
    private final IAccountDataSource serverAccountsData;
    private final Deque<OperationException> exceptionsList = new ConcurrentLinkedDeque<>();
    private final Lock accountsLock = new ReentrantLock();
    private final Map<String, Account> activeAccounts = new HashMap<>();

    public AccountManager(IAuthorizationSource authSource, IAccountDataSource accountDataSource) {
        serverAuthData =
                Objects.requireNonNull(authSource, "Authorization source cannot be null");
        serverAccountsData =
                Objects.requireNonNull(accountDataSource, "Account data source cannot be null");
    }

    public static String getEncodedPassword(String password) {
        return password == null ? null : "encoded_" + password;
    }

    public Account register(String login, String password) {
        if (login == null || password == null) {
            registerException(new OperationException(OperationResponse.NULL_ARGUMENT_EXCEPTION));
            return null;
        }

        accountsLock.lock();
        try {
            if (activeAccounts.containsKey(login)) {
                registerException(new OperationException(OperationResponse.ALREADY_INITIATED_RESPONSE));
                return null;
            }

            OperationResponse response =
                    buildAccountResponse(login, serverAuthData.register(login, getEncodedPassword(password)));
            if (response.code != OperationResponse.SUCCEED) {
                handleAuthorizationError(response, OperationResponse.ALREADY_LOGGED);
                return null;
            }

            Account account = (Account) response.body;
            activeAccounts.put(login, account);
            return account;
        } finally {
            accountsLock.unlock();
        }
    }

    public Account login(String login, String password) {
        if (login == null || password == null) {
            registerException(new OperationException(OperationResponse.NULL_ARGUMENT_EXCEPTION));
            return null;
        }

        accountsLock.lock();
        try {
            Account activeAccount = activeAccounts.get(login);
            if (activeAccount != null) {
                registerException(
                        new OperationException(
                                new OperationResponse(
                                        OperationResponse.ALREADY_LOGGED, activeAccount.getActiveSession())));
                return null;
            }

            OperationResponse response =
                    buildAccountResponse(login, serverAuthData.login(login, getEncodedPassword(password)));
            if (response.code != OperationResponse.SUCCEED) {
                handleAuthorizationError(response, OperationResponse.NO_USER_INCORRECT_PASSWORD);
                return null;
            }

            Account account = (Account) response.body;
            activeAccounts.put(login, account);
            return account;
        } finally {
            accountsLock.unlock();
        }
    }

    public boolean logout(Account account) {
        if (account == null || account.getLogin() == null) {
            registerException(new OperationException(OperationResponse.NULL_ARGUMENT_EXCEPTION));
            return false;
        }

        accountsLock.lock();
        try {
            Account activeAccount = activeAccounts.get(account.getLogin());
            if (activeAccount == null) {
                registerException(new OperationException(OperationResponse.NOT_LOGGED_RESPONSE));
                return false;
            }

            OperationResponse response = callLogout(activeAccount);
            if (response.code == OperationResponse.SUCCEED) {
                activeAccount.clearSession();
                activeAccounts.remove(account.getLogin());
                return true;
            }

            switch (response.code) {
                case OperationResponse.CONNECTION_ERROR:
                case OperationResponse.UNDEFINED_ERROR:
                case OperationResponse.NOT_LOGGED:
                case OperationResponse.INCORRECT_SESSION:
                case OperationResponse.INCORRECT_RESPONSE:
                    registerException(new OperationException(response));
                    break;
                default:
                    registerException(
                            new OperationException(
                                    new OperationResponse(OperationResponse.INCORRECT_RESPONSE, response)));
                    break;
            }
            return false;
        } finally {
            accountsLock.unlock();
        }
    }

    public Collection<OperationException> getExceptions() {
        return List.copyOf(exceptionsList);
    }

    private OperationResponse buildAccountResponse(String login, OperationResponse response) {
        if (response.code != OperationResponse.SUCCEED) {
            return response;
        }

        Long sessionId = extractSessionId(response.body);
        if (sessionId == null) {
            return new OperationResponse(OperationResponse.INCORRECT_RESPONSE, response);
        }

        Account account = new Account(login);
        account.activateSession(sessionId);
        account.initDataStorage(serverAccountsData);
        return new OperationResponse(OperationResponse.SUCCEED, account);
    }

    private Long extractSessionId(Object responseBody) {
        if (responseBody instanceof Long sessionId) {
            return sessionId;
        }
        if (responseBody instanceof Integer sessionId) {
            return sessionId.longValue();
        }
        if (responseBody instanceof String sessionId) {
            try {
                return Long.parseLong(sessionId);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void handleAuthorizationError(OperationResponse response, int expectedBusinessErrorCode) {
        switch (response.code) {
            case OperationResponse.CONNECTION_ERROR:
            case OperationResponse.UNDEFINED_ERROR:
            case OperationResponse.INCORRECT_RESPONSE:
                registerException(new OperationException(response));
                break;
            default:
                if (response.code == expectedBusinessErrorCode
                        || response.code == OperationResponse.ALREADY_LOGGED) {
                    registerException(new OperationException(response));
                } else {
                    registerException(
                            new OperationException(
                                    new OperationResponse(OperationResponse.INCORRECT_RESPONSE, response)));
                }
                break;
        }
    }

    private OperationResponse callLogout(Account account) {
        Long activeSession = account.getActiveSession();
        if (activeSession == null) {
            return OperationResponse.NOT_LOGGED_RESPONSE;
        }

        OperationResponse response = serverAuthData.logout(account.getLogin(), activeSession);
        return switch (response.code) {
            case OperationResponse.SUCCEED,
                 OperationResponse.CONNECTION_ERROR,
                 OperationResponse.UNDEFINED_ERROR,
                 OperationResponse.NOT_LOGGED,
                 OperationResponse.INCORRECT_SESSION -> response;
            default -> new OperationResponse(OperationResponse.INCORRECT_RESPONSE, response);
        };
    }

    private void registerException(OperationException exception) {
        exceptionsList.add(exception);
    }
}
