package ru.hse.server;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Server {
    private ServerStorage storage;
    private ApiServer apiServer;

    public void start(int port) {
        start(port, "accounts");
    }

    public void start(int port, String databaseName) {
        storage = new ServerStorage(databaseName);
        storage.initialize();
        ServerLogicProxy serverLogicProxy = new ServerLogicProxy(storage);
        AccountServer accountServer = new AccountServer(storage, serverLogicProxy);
        AccountSecurity security = new AccountSecurity();
        apiServer = new ApiServer(accountServer, port);

        storage.setChangeVerifier(security);
        apiServer.addAuthListener(security);
    }

    public void stop() {
        if (apiServer != null) {
            apiServer.stop();
        }
        if (storage != null) {
            storage.close();
        }
    }

    public void waitStarted() {
        if (apiServer == null || apiServer.isStarted()) {
            return;
        }

        try {
            apiServer.awaitStarted();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        int port = 7000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                port = 7000;
            }
        }

        server.start(port);
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            System.out.print("Enter stop to stop a server: ");
            while (!"stop".equals(scanner.nextLine().trim())) {
                System.out.print("Wrong command, enter stop to stop a server: ");
            }
        }
        server.stop();
    }
}
