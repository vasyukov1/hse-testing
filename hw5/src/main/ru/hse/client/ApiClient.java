package ru.hse.client;

import java.io.IOException;
import java.net.ConnectException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.hse.IAccountDataSource;
import ru.hse.IAuthorizationSource;
import ru.hse.OperationResponse;

public class ApiClient implements IAccountDataSource, IAuthorizationSource {
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String LOGIN_FIELD_PREFIX = "{\"login\":\"";
    private static final String SESSION_FIELD_PREFIX = "\",\"session\":";
    private static final String PASSWORD_FIELD_PREFIX = "\",\"password\":\"";
    private static final String AMOUNT_FIELD_PREFIX = ",\"amount\":";
    private static final String JSON_SUFFIX = "}";

    private final String connectionUri;
    private final OkHttpClient client;

    public ApiClient(String url) {
        connectionUri = url;
        client = new OkHttpClient();
    }

    @Override
    public OperationResponse withdraw(String login, long session, double balance) {
        return postJson(
                "/account/withdraw", buildAmountRequest(login, session, Double.toString(balance)));
    }

    @Override
    public OperationResponse deposit(String login, long session, double balance) {
        return postJson(
                "/account/deposit", buildAmountRequest(login, session, Double.toString(balance)));
    }

    @Override
    public OperationResponse getBalance(String login, long session) {
        return postJson("/account/balance", buildSessionRequest(login, Long.toString(session)));
    }

    @Override
    public OperationResponse register(String login, String password) {
        return postJson("/register", buildPasswordRequest(login, password));
    }

    @Override
    public OperationResponse login(String login, String password) {
        return postJson("/login", buildPasswordRequest(login, password));
    }

    @Override
    public OperationResponse logout(String login, Long activeSession) {
        return postJson("/account/logout", buildSessionRequest(login, String.valueOf(activeSession)));
    }

    private OperationResponse postJson(String path, String jsonBody) {
        RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        Request request =
                new Request.Builder()
                        .url(connectionUri + path)
                        .post(body)
                        .addHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .build();

        try (Response response = client.newCall(request).execute()) {
            try (ResponseBody responseBody = response.body()) {
                if (responseBody == null) {
                    return OperationResponse.UNDEFINED_ERROR_RESPONSE;
                }
                return OperationResponse.fromString(responseBody.string());
            }
        } catch (ConnectException exception) {
            return new OperationResponse(OperationResponse.CONNECTION_ERROR, exception.getMessage());
        } catch (IOException exception) {
            return new OperationResponse(OperationResponse.UNDEFINED_ERROR, exception.getMessage());
        }
    }

    private String buildAmountRequest(String login, long session, String amount) {
        return LOGIN_FIELD_PREFIX
                + login
                + SESSION_FIELD_PREFIX
                + session
                + AMOUNT_FIELD_PREFIX
                + amount
                + JSON_SUFFIX;
    }

    private String buildPasswordRequest(String login, String password) {
        return LOGIN_FIELD_PREFIX + login + PASSWORD_FIELD_PREFIX + password + "\"" + JSON_SUFFIX;
    }

    private String buildSessionRequest(String login, String session) {
        return LOGIN_FIELD_PREFIX + login + SESSION_FIELD_PREFIX + session + JSON_SUFFIX;
    }
}
