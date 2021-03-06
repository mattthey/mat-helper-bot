package com.github.mattthey.mathelperbot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Date;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.sun.net.httpserver.HttpServer;

/**
 * Точка входа
 */
public class Main
{
    private static final String BOT_DESCRIPTION = """
            A bot for downloading audiobooks from knigavuhe.org. Send him a link, for example
            https://knigavuhe.org/book/garri-potter-i-uznik-azkabana/
            And the bot will drop you a list of chapters, you can choose any part.
            Attention! The bot is under development, so some bugs are possible. Please let me know.
            """;
    public static final Path OUTPUT_DIR = Path.of("output");

    public static void main(String[] args) throws TelegramApiException, IOException
    {
        final String botUsername = System.getenv("botUsername");
        final String botToken = System.getenv("botToken");
        if (botUsername == null || botToken == null)
        {
            throw new RuntimeException("botUsername or botToken is null.");
        }
        startBot(botUsername, botToken);
        startHttpServer();
    }

    private static void startBot(final String botUsername, final String botToken) throws TelegramApiException, IOException
    {
        prepareOutputDirectory();

        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        final MatHelperBot matHelperBot = new MatHelperBot(botUsername, botToken);
        telegramBotsApi.registerBot(matHelperBot);
    }

    private static void prepareOutputDirectory() throws IOException
    {
        recursiveDeleteDirectory(OUTPUT_DIR);
        Files.createDirectory(OUTPUT_DIR);
    }

    private static void recursiveDeleteDirectory(Path path)
    {
        if (!Files.exists(path))
        {
            return;
        }
        try
        {
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            {
                Files.newDirectoryStream(path)
                        .forEach(Main::recursiveDeleteDirectory);
            }
            Files.delete(path);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Для деплоя на heroku это необходимо.
     */
    private static void startHttpServer()
    {
        final int port = Integer.parseInt(System.getenv("PORT"));
        final HttpServer httpServer;
        try
        {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/", exchange ->
            {
                System.out.println("Update date " + new Date());
                exchange.sendResponseHeaders(200, BOT_DESCRIPTION.length());
                OutputStream os = exchange.getResponseBody();
                os.write(BOT_DESCRIPTION.getBytes());
                os.close();
            });
            httpServer.start();
            System.out.printf("Http server start on port %d.\n", port);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}