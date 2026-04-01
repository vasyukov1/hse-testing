package ru.hse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Base64;

public class OperationResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final char SEPARATOR = '|';
    private static final int STRING_BODY_MODE = 1;
    private static final int SERIALIZABLE_BODY_MODE = 2;
    private static final int RESPONSE_BODY_MODE = 3;

    public static final int SUCCEED = 0;
    public static final int ALREADY_LOGGED = 1;
    public static final int NOT_LOGGED = 2;
    public static final int NO_USER_INCORRECT_PASSWORD = 3;
    public static final int INCORRECT_RESPONSE = 4;
    public static final int UNDEFINED_ERROR = 5;
    public static final int INCORRECT_SESSION = 6;
    public static final int NO_MONEY = 7;
    public static final int ENCODING_ERROR = 8;
    public static final int ALREADY_INITIATED = 9;
    public static final int NULL_ARGUMENT = 10;
    public static final int CONNECTION_ERROR = 11;

    public static final OperationResponse ACCOUNT_MANAGER_RESPONSE = new OperationResponse(ALREADY_LOGGED, null);
    public static final OperationResponse NO_USER_INCORRECT_PASSWORD_RESPONSE = new OperationResponse(NO_USER_INCORRECT_PASSWORD, null);
    public static final OperationResponse UNDEFINED_ERROR_RESPONSE = new OperationResponse(UNDEFINED_ERROR, null);
    public static final OperationResponse NOT_LOGGED_RESPONSE = new OperationResponse(NOT_LOGGED, null);
    public static final OperationResponse INCORRECT_SESSION_RESPONSE = new OperationResponse(INCORRECT_SESSION, null);
    public static final OperationResponse SUCCEED_RESPONSE = new OperationResponse(SUCCEED, null);
    public static final OperationResponse NO_MONEY_RESPONSE = new OperationResponse(NO_MONEY, null);
    public static final OperationResponse ENCODING_ERROR_RESPONSE = new OperationResponse(ENCODING_ERROR, null);
    public static final OperationResponse ALREADY_INITIATED_RESPONSE = new OperationResponse(ALREADY_INITIATED, null);
    public static final OperationResponse NULL_ARGUMENT_EXCEPTION = new OperationResponse(NULL_ARGUMENT, null);
    public static final OperationResponse CONNECTION_ERROR_RESPONSE = new OperationResponse(CONNECTION_ERROR, null);

    public final int code;
    public final Object body;

    public OperationResponse(int code, Object obj) {
        this.code = code;
        this.body = obj;
    }

    public String toResultString() {
        StringBuilder builder = new StringBuilder().append(code);
        if (body == null) {
            return builder.toString();
        }
        if (body instanceof OperationResponse responseBody) {
            return builder.append(SEPARATOR).append(RESPONSE_BODY_MODE).append(SEPARATOR).append(responseBody.toResultString()).toString();
        }
        if (body instanceof String textBody) {
            return builder.append(SEPARATOR).append(STRING_BODY_MODE).append(SEPARATOR).append(textBody).toString();
        }
        if (body instanceof Serializable serializableBody) {
            try {
                return builder.append(SEPARATOR).append(SERIALIZABLE_BODY_MODE).append(SEPARATOR).append(responseToString(serializableBody)).toString();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to serialize operation response body", exception);
            }
        }
        throw new IllegalStateException("Operation response body must be serializable");
    }

    public static OperationResponse fromString(String rawResponse) {
        try {
            return parseResponse(rawResponse);
        } catch (NumberFormatException | IOException exception) {
            return new OperationResponse(UNDEFINED_ERROR, rawResponse);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to deserialize operation response body", exception);
        }
    }

    private static OperationResponse parseResponse(String rawResponse) throws IOException, ClassNotFoundException {
        int codeSeparator = rawResponse.indexOf(SEPARATOR);
        if (codeSeparator < 0) {
            return parseCodeOnly(rawResponse);
        }

        int parsedCode = Integer.parseInt(rawResponse.substring(0, codeSeparator));
        String rawBody = rawResponse.substring(codeSeparator + 1);
        int modeSeparator = rawBody.indexOf(SEPARATOR);
        if (modeSeparator < 0) {
            return new OperationResponse(UNDEFINED_ERROR, rawResponse);
        }

        int mode = Integer.parseInt(rawBody.substring(0, modeSeparator));
        String payload = rawBody.substring(modeSeparator + 1);
        return switch (mode) {
            case STRING_BODY_MODE -> new OperationResponse(parsedCode, payload);
            case SERIALIZABLE_BODY_MODE -> new OperationResponse(parsedCode, responseFromString(payload));
            case RESPONSE_BODY_MODE -> new OperationResponse(parsedCode, fromString(payload));
            default -> new OperationResponse(UNDEFINED_ERROR, rawResponse);
        };
    }

    private static OperationResponse parseCodeOnly(String rawResponse) {
        int parsedCode = Integer.parseInt(rawResponse);
        return new OperationResponse(parsedCode, null);
    }

    public static String codeToErrorMessage(int code) {
        return switch (code) {
            case SUCCEED -> "SUCCEED";
            case ALREADY_LOGGED -> "ALREADY LOGGED OR REGISTERED";
            case NOT_LOGGED -> "NOT LOGGED";
            case NO_USER_INCORRECT_PASSWORD -> "INCORRECT PASSWORD OR NO SUCH USER";
            case INCORRECT_RESPONSE -> "INCORRECT RESPONSE";
            case UNDEFINED_ERROR -> "UNDEFINED ERROR";
            case INCORRECT_SESSION -> "INCORRECT SESSION NUMBER";
            case NO_MONEY -> "NOT ENOUGH MONEY ON BALANCE";
            case ENCODING_ERROR -> "ENCODING CANNOT BE MADE";
            case ALREADY_INITIATED -> "ACCOUNT WAS ALREADY INITIATED";
            case NULL_ARGUMENT -> "Null argument is prohibited";
            case CONNECTION_ERROR -> "No connection to server";
            default -> "CODE_" + code;
        };
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(codeToErrorMessage(code));
        if (body != null) {
            builder.append('[').append(body).append(']');
        }
        return builder.toString();
    }

    public static String responseToString(Serializable value) throws IOException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
            objectStream.writeObject(value);
            objectStream.flush();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        }
    }

    /**
     * Deserializes a Base64 encoded string back to an object.
     */
    @SuppressFBWarnings("SECOBDES")
    public static Object responseFromString(String encodedValue) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(encodedValue);
        try (ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return objectStream.readObject();
        }
    }
}
