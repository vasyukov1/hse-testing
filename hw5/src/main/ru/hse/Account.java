package ru.hse;

public class Account {
    private IAccountDataSource storage;
    private final String login;
    private Long activeSession;

    public String getLogin() {
        return login;
    }

    public Long getActiveSession() {
        return activeSession;
    }

    public Account(String login) {
        this.login = login;
    }

    public OperationResponse withdraw(double amount) {
        if (storage == null) {
            return OperationResponse.CONNECTION_ERROR_RESPONSE;
        }
        if (activeSession == null) {
            return OperationResponse.NOT_LOGGED_RESPONSE;
        }
        OperationResponse response = storage.withdraw(login, activeSession, amount);
        switch (response.code) {
            case OperationResponse.CONNECTION_ERROR:
                return OperationResponse.CONNECTION_ERROR_RESPONSE;
            case OperationResponse.INCORRECT_SESSION:
                return OperationResponse.INCORRECT_SESSION_RESPONSE;
            case OperationResponse.NOT_LOGGED:
                return OperationResponse.NOT_LOGGED_RESPONSE;
            case OperationResponse.NO_MONEY:
                if (response.body instanceof Double) {
                    return new OperationResponse(OperationResponse.NO_MONEY, response.body);
                }
                break;
            case OperationResponse.UNDEFINED_ERROR:
                return response;
            case OperationResponse.SUCCEED:
                if (response.body instanceof Double) {
                    return new OperationResponse(OperationResponse.SUCCEED, response.body);
                }
                break;
            default:
                break;
        }
        return new OperationResponse(OperationResponse.INCORRECT_RESPONSE, response);
    }

    public OperationResponse deposit(double amount) {
        if (storage == null) {
            return OperationResponse.CONNECTION_ERROR_RESPONSE;
        }
        if (activeSession == null) {
            return OperationResponse.NOT_LOGGED_RESPONSE;
        }
        OperationResponse response = storage.deposit(login, activeSession, amount);
        switch (response.code) {
            case OperationResponse.CONNECTION_ERROR:
                return OperationResponse.CONNECTION_ERROR_RESPONSE;
            case OperationResponse.NOT_LOGGED:
                return OperationResponse.NOT_LOGGED_RESPONSE;
            case OperationResponse.UNDEFINED_ERROR:
                return response;
            case OperationResponse.SUCCEED:
                if (response.body instanceof Double) {
                    return new OperationResponse(OperationResponse.SUCCEED, response.body);
                }
                break;
            default:
                break;
        }
        return new OperationResponse(OperationResponse.INCORRECT_RESPONSE, response);
    }

    public OperationResponse getBalance() {
        if (storage == null) {
            return OperationResponse.CONNECTION_ERROR_RESPONSE;
        }
        if (activeSession == null) {
            return OperationResponse.NOT_LOGGED_RESPONSE;
        }
        OperationResponse response = storage.getBalance(login, activeSession);
        switch (response.code) {
            case OperationResponse.CONNECTION_ERROR:
                return OperationResponse.CONNECTION_ERROR_RESPONSE;
            case OperationResponse.NOT_LOGGED:
                return OperationResponse.NOT_LOGGED_RESPONSE;
            case OperationResponse.INCORRECT_SESSION:
                return OperationResponse.INCORRECT_SESSION_RESPONSE;
            case OperationResponse.UNDEFINED_ERROR:
                return response;
            case OperationResponse.SUCCEED:
                if (response.body instanceof Double) {
                    return new OperationResponse(OperationResponse.SUCCEED, response.body);
                }
                break;
            default:
                break;
        }
        return new OperationResponse(OperationResponse.INCORRECT_RESPONSE, response);
    }

    public void initDataStorage(IAccountDataSource serverAccountsData) {
        storage = serverAccountsData;
    }

    public void activateSession(Long sessionId) {
        activeSession = sessionId;
    }

    public void clearSession() {
        activeSession = null;
    }
}
