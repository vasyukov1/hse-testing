package ru.hse.server;

import io.javalin.Javalin;
import io.javalin.http.Context;
import jakarta.servlet.http.HttpSession;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import ru.hse.Account;
import ru.hse.OperationException;
import ru.hse.OperationResponse;

public class ApiServer {
    private static final String SERVER_DESCRIPTION =
            "Server is online. Possible routes are: /register, /login, "
                    + "/account/logout, /account/withdraw, /account/deposit, /account/balance";
    private static final String REQUIRED_CREDENTIALS_MESSAGE = "Username и password обязательны";
    private static final String INVALID_SESSION_MESSAGE =
            "Произошла ошибка при разлогировании. Возможно номер сессии указан некорректно "
                    + "или аккаунт/сессия не существуют";

    private final AccountServer server;
    private final Javalin app;
    private final List<IAccountAuthListener> authListeners = new CopyOnWriteArrayList<>();
    private final CountDownLatch startedLatch = new CountDownLatch(1);

    public ApiServer(AccountServer server, int port) {
        this.server = server;
        app = Javalin.create(config -> {
        }).start(port);
        registerRoutes();
        startedLatch.countDown();
        System.out.println("Server is running on http://localhost:" + port);
    }

    public boolean addAuthListener(IAccountAuthListener listener) {
        return authListeners.add(listener);
    }

    public boolean removeAuthListener(IAccountAuthListener listener) {
        return authListeners.remove(listener);
    }

    public Collection<IAccountAuthListener> getAuthListeners() {
        return List.copyOf(authListeners);
    }

    public boolean isStarted() {
        return startedLatch.getCount() == 0;
    }

    public void awaitStarted() throws InterruptedException {
        startedLatch.await();
    }

    public void stop() {
        app.stop();
    }

    private void registerRoutes() {
        app.get("/", context -> context.result(SERVER_DESCRIPTION));
        app.post("/register", this::handleRegister);
        app.post("/login", this::handleLogin);
        app.post("/account/logout", this::handleLogout);
        app.post(
                "/account/withdraw", context -> handleAmountOperation(context, OperationType.WITHDRAW));
        app.post(
                "/account/deposit", context -> handleAmountOperation(context, OperationType.DEPOSIT));
        app.post("/account/balance", this::handleBalance);
    }

    private void handleRegister(Context context) {
        AuthRequest request = context.bodyAsClass(AuthRequest.class);
        if (hasBlankCredentials(request.login(), request.password())) {
            context.result(validationErrorResponse(REQUIRED_CREDENTIALS_MESSAGE));
            return;
        }

        try {
            Account account = server.register(request.login(), request.password());
            context.result(
                    new OperationResponse(OperationResponse.SUCCEED, account.getActiveSession())
                            .toResultString());
        } catch (OperationException exception) {
            context.result(exception.toResultString());
        }
    }

    private void handleLogin(Context context) {
        AuthRequest request = context.bodyAsClass(AuthRequest.class);
        if (hasBlankCredentials(request.login(), request.password())) {
            context.result(validationErrorResponse(REQUIRED_CREDENTIALS_MESSAGE));
            return;
        }

        try {
            Account account = server.login(request.login(), request.password());
            notifyLogin(request.login());
            context.result(
                    new OperationResponse(OperationResponse.SUCCEED, account.getActiveSession())
                            .toResultString());
        } catch (OperationException exception) {
            context.result(exception.toResultString());
        }
    }

    private void handleLogout(Context context) {
        LoggedRequest request = context.bodyAsClass(LoggedRequest.class);
        try {
            Account account = requireSession(request.login(), request.session());
            if (account == null) {
                context.result(invalidSessionResponse());
                return;
            }

            server.logout(account);
            notifyLogout(request.login());
            HttpSession session = context.req().getSession(false);
            if (session != null) {
                session.invalidate();
            }
            context.result(Integer.toString(OperationResponse.SUCCEED));
        } catch (OperationException exception) {
            context.result(exception.toResultString());
        }
    }

    private void handleAmountOperation(Context context, OperationType operationType) {
        LoggedRequestDouble request = context.bodyAsClass(LoggedRequestDouble.class);
        Account account = requireSession(request.login(), request.session());
        if (account == null) {
            context.result(invalidSessionResponse());
            return;
        }

        OperationResponse response =
                operationType == OperationType.WITHDRAW
                        ? account.withdraw(request.amount())
                        : account.deposit(request.amount());
        context.result(response.toResultString());
    }

    private void handleBalance(Context context) {
        LoggedRequest request = context.bodyAsClass(LoggedRequest.class);
        Account account = requireSession(request.login(), request.session());
        if (account == null) {
            context.result(invalidSessionResponse());
            return;
        }
        context.result(account.getBalance().toResultString());
    }

    private Account requireSession(String login, Long session) {
        if (login == null || session == null) {
            return null;
        }
        return server.testSession(login, session);
    }

    private boolean hasBlankCredentials(String login, String password) {
        return login == null || password == null || login.isBlank() || password.isBlank();
    }

    private String invalidSessionResponse() {
        return new OperationResponse(OperationResponse.NOT_LOGGED, INVALID_SESSION_MESSAGE)
                .toResultString();
    }

    private String validationErrorResponse(String message) {
        return new OperationResponse(OperationResponse.NOT_LOGGED, message).toResultString();
    }

    private void notifyLogin(String login) {
        for (IAccountAuthListener listener : getAuthListeners()) {
            listener.accountLogin(login);
        }
    }

    private void notifyLogout(String login) {
        for (IAccountAuthListener listener : getAuthListeners()) {
            listener.accountLogout(login);
        }
    }

    private enum OperationType {
        WITHDRAW,
        DEPOSIT
    }

    private record AuthRequest(String login, String password) {
    }

    private record LoggedRequest(String login, Long session) {
    }

    private record LoggedRequestDouble(String login, double amount, Long session) {
    }
}
