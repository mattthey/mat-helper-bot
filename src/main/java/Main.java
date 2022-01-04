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
        final String botUsername = System.getenv("botUsername");
        final String botToken = System.getenv("botToken");
        if (botUsername == null || botToken == null)
        {
            throw new RuntimeException("botUsername or botToken is null.");
        }
        startBot(botUsername, botToken);
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