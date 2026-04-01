package ru.hse.client;

import java.util.Collection;

import ru.hse.Account;
import ru.hse.IAccountDataSource;
import ru.hse.IAuthorizationSource;
import ru.hse.OperationException;
import ru.hse.OperationResponse;

public class Client {
    private final AccountManager accountManager;

    public Client(IAuthorizationSource authSource, IAccountDataSource dataSource) {
        accountManager = new AccountManager(authSource, dataSource);
    }

    public Client(String url) {
        ApiClient baseApiClient = new ApiClient(url);
        accountManager = new AccountManager(baseApiClient, baseApiClient);
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public Account register(String login, String password) throws OperationException {
        int initialExceptionCount = accountManager.getExceptions().size();
        Account account = accountManager.register(login, password);
        if (account == null) {
            rethrowNewExceptions(initialExceptionCount, true, true, true, false, false);
        }
        return account;
    }

    public Account login(String login, String password) throws OperationException {
        int initialExceptionCount = accountManager.getExceptions().size();
        Account account = accountManager.login(login, password);
        if (account == null) {
            rethrowNewExceptions(initialExceptionCount, true, true, false, true, true);
        }
        return account;
    }

    public boolean logout(Account account) throws OperationException {
        int initialExceptionCount = accountManager.getExceptions().size();
        if (!accountManager.logout(account)) {
            rethrowNewExceptions(initialExceptionCount, false, true, true, true, false);
            return false;
        }
        return true;
    }

    public static double getBalance(Account account) throws OperationException {
        return extractBalance(account.getBalance());
    }

    public static double withdraw(Account account, double amount) throws OperationException {
        return extractBalance(account.withdraw(amount));
    }

    public static double deposit(Account account, double amount) throws OperationException {
        return extractBalance(account.deposit(amount));
    }

    private void rethrowNewExceptions(
            int initialExceptionCount,
            boolean includeAlreadyInitiated,
            boolean includeUndefined,
            boolean includeNotLogged,
            boolean includeIncorrectCredentials,
            boolean includeAlreadyLogged)
            throws OperationException {
        int currentIndex = 0;
        Collection<OperationException> exceptions = accountManager.getExceptions();
        for (OperationException exception : exceptions) {
            if (currentIndex < initialExceptionCount) {
                currentIndex++;
                continue;
            }
            currentIndex++;
            if (shouldThrow(
                    exception,
                    includeAlreadyInitiated,
                    includeUndefined,
                    includeNotLogged,
                    includeIncorrectCredentials,
                    includeAlreadyLogged)) {
                throw exception;
            }
            System.err.println(exception);
        }
    }

    private boolean shouldThrow(
            OperationException exception,
            boolean includeAlreadyInitiated,
            boolean includeUndefined,
            boolean includeNotLogged,
            boolean includeIncorrectCredentials,
            boolean includeAlreadyLogged) {
        return switch (exception.response.code) {
            case OperationResponse.NULL_ARGUMENT, OperationResponse.CONNECTION_ERROR -> true;
            case OperationResponse.ALREADY_INITIATED -> includeAlreadyInitiated;
            case OperationResponse.UNDEFINED_ERROR -> includeUndefined;
            case OperationResponse.NOT_LOGGED, OperationResponse.INCORRECT_SESSION -> includeNotLogged;
            case OperationResponse.NO_USER_INCORRECT_PASSWORD -> includeIncorrectCredentials;
            case OperationResponse.ALREADY_LOGGED -> includeAlreadyLogged;
            default -> false;
        };
    }

    private static double extractBalance(OperationResponse response) throws OperationException {
        if (response.code == OperationResponse.SUCCEED && response.body instanceof Double balance) {
            return balance;
        }
        if (response.code == OperationResponse.INCORRECT_RESPONSE) {
            System.err.println(response);
            return Double.NaN;
        }
        throw new OperationException(response);
    }
}
