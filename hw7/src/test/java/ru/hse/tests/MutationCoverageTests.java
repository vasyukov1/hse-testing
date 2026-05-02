package ru.hse.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.hse.TelegramBotApplication;
import ru.hse.TelegramBotDataStubs;
import ru.hse.bot.EcommerceTelegramBot;
import ru.hse.model.Product;
import ru.hse.model.User;
import ru.hse.service.ProductService;
import ru.hse.service.UserService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationCoverageTests {
    private static final long USER_ID = 1L;

    private TelegramBotApplication app;
    private EcommerceTelegramBot bot;
    private StringBuffer botOut;
    private ByteArrayOutputStream stdOut;
    private ByteArrayOutputStream stdErr;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private PrintStream outStream;
    private PrintStream errStream;

    @BeforeEach
    void setUp() throws Exception {
        originalOut = System.out;
        originalErr = System.err;
        stdOut = new ByteArrayOutputStream();
        stdErr = new ByteArrayOutputStream();
        outStream = new PrintStream(stdOut, true, StandardCharsets.UTF_8);
        errStream = new PrintStream(stdErr, true, StandardCharsets.UTF_8);
        System.setOut(outStream);
        System.setErr(errStream);

        app = new TelegramBotApplication(true);
        app.startServices();
        bot = app.getBot();
        botOut = app.getBotTestWriter().getBuffer();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (app != null) {
            app.stop();
        }
        if (outStream != null) {
            outStream.close();
        }
        if (errStream != null) {
            errStream.close();
        }
        if (stdOut != null) {
            stdOut.close();
        }
        if (stdErr != null) {
            stdErr.close();
        }
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String reactOnMessage(String message) {
        return reactOnMessage(message, USER_ID);
    }

    private String reactOnMessage(String message, long userId) {
        bot.onUpdateReceived(app.getDataStubs().formUpdateRequest(message, userId));
        try {
            return botOut.toString();
        } finally {
            botOut.setLength(0);
        }
    }

    @Test
    void helpCommandShowsOnlyGeneralHelp() {
        reactOnMessage("/start");

        String response = reactOnMessage("/help");

        assertTrue(response.contains("Available commands:"));
        assertFalse(response.contains("Command not recognized"));
    }

    @Test
    void helpSubcommandShowsOnlySpecificHelp() {
        reactOnMessage("/start");

        String response = reactOnMessage("/help cart");

        assertTrue(response.contains("/cart checkout - Create new order"));
        assertFalse(response.contains("Command not recognized"));
    }

    @Test
    void couponApplyReportsMissingAndRepeatedCoupons() {
        reactOnMessage("/start");

        String missingCoupon = reactOnMessage("/coupon apply missing");
        String firstApply = reactOnMessage("/coupon apply 3DRwBBrcFThKXq9zNIdPihfg3eaQ7g");
        String repeatedApply = reactOnMessage("/coupon apply 3DRwBBrcFThKXq9zNIdPihfg3eaQ7g");

        assertTrue(missingCoupon.contains("is not found"));
        assertTrue(firstApply.contains("was successfully applied"));
        assertTrue(firstApply.contains("Your balance: $1000"));
        assertTrue(repeatedApply.contains("was already applied"));
    }

    @Test
    void addToCartAcceptsExactlyThreeArgumentsAndAllowsZeroRemainingBalance() {
        reactOnMessage("/start");
        reactOnMessage("/coupon apply 3DRwBBrcFThKXq9zNIdPihfg3eaQ7g");

        String response = reactOnMessage("/cart add 2 2");
        String cartState = reactOnMessage("/cart");

        assertTrue(response.contains("2x good2 added to your cart"));
        assertFalse(response.contains("Invalid command format"));
        assertTrue(cartState.contains("Total: $1000"));
        assertTrue(cartState.contains("Your balance: $1000"));
    }

    @Test
    void addToCartRejectsZeroQuantity() {
        reactOnMessage("/start");

        String response = reactOnMessage("/cart add 2 0");

        assertTrue(response.contains("Quantity must be greater than zero"));
    }

    @Test
    void addToCartRejectsItemsThatWouldExceedBalance() {
        reactOnMessage("/start");
        reactOnMessage("/coupon apply 3DRwBBrcFThKXq9zNIdPihfg3eaQ7g");

        String response = reactOnMessage("/cart add 2 3");
        String cartState = reactOnMessage("/cart");

        assertTrue(response.contains("Insufficient balance"));
        assertTrue(cartState.contains("shopping cart is empty"));
    }

    @Test
    void onUpdateReceivedWritesIncomingMessageToStdout() {
        reactOnMessage("/start");

        reactOnMessage("/products");

        assertTrue(stdOut.toString(StandardCharsets.UTF_8).contains("Received message: '/products'"));
    }

    @Test
    void onUpdateReceivedWritesErrorAndStackTraceToStderr() {
        User existingUser = new User(1L, USER_ID, "USER_1", new HashSet<>());
        EcommerceTelegramBot brokenBot = new EcommerceTelegramBot(
                new StringWriter(),
                "test",
                "test",
                new UserService(null) {
                    @Override
                    public User getUserByChatId(Long chatId) {
                        return existingUser;
                    }
                },
                new ProductService(null) {
                    @Override
                    public java.util.List<Product> getAllProducts() {
                        return Collections.emptyList();
                    }
                },
                null,
                null
        );
        brokenBot = Mockito.spy(brokenBot);
        Mockito.doThrow(new RuntimeException("exception happened"))
                .when(brokenBot)
                .sendMessage(USER_ID, "Welcome back, USER_1! Your account is already set up.");

        brokenBot.onUpdateReceived(new TelegramBotDataStubs().formUpdateRequest("/start", USER_ID));

        String stderrOutput = stdErr.toString(StandardCharsets.UTF_8);
        assertTrue(stderrOutput.contains("Error processing message"));
        assertTrue(stderrOutput.contains("RuntimeException: exception happened"));
    }

    @Test
    void sendMessagePopulatesChatIdAndTextBeforeExecute() throws TelegramApiException {
        User existingUser = new User(1L, USER_ID, "USER_1", new HashSet<>());
        EcommerceTelegramBot directBot = new EcommerceTelegramBot(
                null,
                "test",
                "test",
                new UserService(null) {
                    @Override
                    public User getUserByChatId(Long chatId) {
                        return existingUser;
                    }
                },
                new ProductService(null) {
                    @Override
                    public java.util.List<Product> getAllProducts() {
                        return Collections.emptyList();
                    }
                },
                null,
                null
        );
        EcommerceTelegramBot spyBot = Mockito.spy(directBot);
        StringBuilder captured = new StringBuilder();
        Mockito.doAnswer(invocation -> {
            SendMessage message = invocation.getArgument(0);
            captured.append(message.getChatId()).append("|").append(message.getText());
            return null;
        }).when(spyBot).execute(Mockito.any(SendMessage.class));

        spyBot.sendMessage(42L, "hello");

        assertEquals("42|hello", captured.toString());
    }

    @Test
    void updateWithMissingMessageIsIgnored() {
        assertDoesNotThrow(() -> bot.onUpdateReceived(new org.telegram.telegrambots.meta.api.objects.Update()));
    }
}
