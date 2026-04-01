package ru.hse.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ru.hse.OperationResponse;

public class AccountSecurity implements IAccountChangeVerifier, IAccountAuthListener {
    private static final double SUSPICION_THRESHOLD = 250.0d;

    private final Map<String, Double> degreeOfSuspect = new ConcurrentHashMap<>();
    private final Map<String, Double> maxChange = new ConcurrentHashMap<>();

    @Override
    public int approveChange(String user, double change) {
        if (user == null) {
            return OperationResponse.NULL_ARGUMENT;
        }

        double absoluteChange = Math.abs(change);
        if (change < 0 && isSuspect(user, absoluteChange)) {
            return OperationResponse.UNDEFINED_ERROR;
        }

        updateSuspicion(user, absoluteChange);
        return OperationResponse.SUCCEED;
    }

    @Override
    public void accountLogin(String login) {
        degreeOfSuspect.putIfAbsent(login, 0.0d);
        maxChange.putIfAbsent(login, 0.0d);
    }

    @Override
    public void accountLogout(String login) {
        degreeOfSuspect.remove(login);
        maxChange.remove(login);
    }

    private boolean isSuspect(String user, double absoluteChange) {
        double suspicionLevel = degreeOfSuspect.getOrDefault(user, 0.0d);
        double highestChange = maxChange.getOrDefault(user, 0.0d);
        return suspicionLevel > SUSPICION_THRESHOLD && absoluteChange > highestChange;
    }

    private void updateSuspicion(String user, double absoluteChange) {
        degreeOfSuspect.merge(user, absoluteChange, Double::sum);
        maxChange.merge(user, absoluteChange, Math::max);
    }
}
