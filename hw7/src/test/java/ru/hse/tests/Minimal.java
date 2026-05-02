package ru.hse.tests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.hse.TelegramBotApplication;
import ru.hse.TelegramBotDataStubs;
import ru.hse.bot.EcommerceTelegramBot;
import ru.hse.model.User;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

public class Minimal {
    private TelegramBotApplication app;
    private EcommerceTelegramBot bot;
    StringBuffer botOut;
    private static final long userId = 1l;
    private ByteArrayOutputStream sysOutContent;
    private PrintStream outPS, originalOut;
    @BeforeEach
    public void beforeEach(){
        sysOutContent = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();
        try {
            originalOut = System.out;
            outPS = new PrintStream(sysOutContent, true, utf8);
            System.setOut(outPS);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        app = new TelegramBotApplication(true);
        try {
            app.startServices();
            bot = app.getBot();
            botOut = app.getBotTestWriter().getBuffer();
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
    @AfterEach
    public void afterEach() throws IOException {
        outPS.close();
        sysOutContent.close();
        System.setOut(originalOut);
    }
    private String reactOnMessage (String message){
        return reactOnMessage(message, userId);
    }
    private String reactOnMessage (String message, long userId){
        bot.onUpdateReceived(app.getDataStubs().formUpdateRequest(message, userId));
        try {
            return botOut.toString();
        }finally{
            botOut.setLength(0);
        }
    }

    @Test
    public void testBeforeStart(){
        String res = reactOnMessage("/hello");
        assertTrue(res.length()>0);
        assertTrue(res.toLowerCase().contains("please use /start to create an account first"));
    }
}
