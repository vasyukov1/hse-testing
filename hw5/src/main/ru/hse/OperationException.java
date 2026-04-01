package ru.hse;

public class OperationException extends Exception {
    private static final long serialVersionUID = 1L;

    public final OperationResponse response;

    public OperationException(OperationResponse resp) {
        super(resp == null ? null : resp.toString());
        response = resp;
    }

    public String toResultString() {
        return response.toResultString();
    }

    @Override
    public String toString() {
        return response.toString();
    }
}
