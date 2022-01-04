import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 *
 */
public class Main
{
    public static final Path OUTPUT_DIR = Path.of("output");

    public static void main(String[] args) throws TelegramApiException, IOException
    {
        System.out.printf("Arguments size: %d.\n", args.length);
        System.out.printf("botUsername = '%s'\n", System.getProperty("botUsername", "NO PROPERTY"));
        System.out.printf("botUsername = '%s'\n", System.getenv("botUsername"));
        if (args.length < 2)
        {
            for (String arg : args)
                System.out.println(arg);
            throw new RuntimeException("No arguments");
        }

        startBot(args[0], args[1]);
    }

    private static void startBot(final String botUsername, final String botToken) throws TelegramApiException, IOException
    {
        prepare();

        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        final MatHelperBot matHelperBot = new MatHelperBot(botUsername, botToken);
        telegramBotsApi.registerBot(matHelperBot);
    }

    private static void prepare() throws IOException
    {
        recursiveDeleteDirectory(OUTPUT_DIR);
        Files.createDirectory(OUTPUT_DIR);
    }

    public static void recursiveDeleteDirectory(Path path)
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
}