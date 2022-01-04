import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 *
 */
public class WorkerDownloaderFromKnigavuhe implements Runnable
{
    private final Path outDirectory;
    private final String chatId;
    private final String uri;
    private final TelegramLongPollingBot bot;

    public WorkerDownloaderFromKnigavuhe(String chatId, String uri, TelegramLongPollingBot bot)
    {
        this.outDirectory = Main.OUTPUT_DIR.resolve(Path.of(Long.toString(System.currentTimeMillis())));
        try
        {
            Files.createDirectories(outDirectory);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        this.chatId = chatId;
        this.uri = uri.replace("m.knigavuhe.org", "knigavuhe.org");
        this.bot = bot;
    }

    @Override
    public void run()
    {
        try
        {
            final Document document;
            try
            {
                document = Jsoup.connect(uri).get();
            }
            catch (IOException e)
            {
                sendErrorMessage(e);
                return;
            }

            final String title = DownloaderAudioBookFromKnigavuhe.getTitle(document);
            final String author = DownloaderAudioBookFromKnigavuhe.getAuthor(document);
            // TODO подхимичить
            final String fullTitle = title + " - " + author;
            System.out.printf("Download %s - %s\n", author, title);

            final List<String> trackUrls = DownloaderAudioBookFromKnigavuhe.getTrackUrls(document);
            final JSONObject json = getJson(trackUrls);
//            System.out.println(json);

            final SendMessage sendMessage = prepare(fullTitle, json, chatId, uri);

            try
            {
                bot.execute(sendMessage);
            }
            catch (TelegramApiException e)
            {
                e.printStackTrace();
            }
        }
        finally
        {
            Main.recursiveDeleteDirectory(outDirectory);
        }
    }

    private void sendErrorMessage(Throwable cause)
    {
        cause.printStackTrace();

        final SendMessage sendMessage = new SendMessage();
        sendMessage.setText(cause.getMessage());
        sendMessage.setChatId(chatId);
        try
        {
            bot.execute(sendMessage);
        }
        catch (TelegramApiException ex)
        {
            ex.printStackTrace();
        }
    }

    public static JSONObject getJson(List<String> trackUrls)
    {
        final JSONObject jsonObject = new JSONObject();
        for (int i = 0; i < trackUrls.size(); i++)
        {
            jsonObject.put(Integer.toString(i), trackUrls.get(i));
        }
        jsonObject.put("idx", "0");
        return jsonObject;
    }

    public static SendMessage prepare(String fullTitle, JSONObject jsonObject, String chatId,
            String uri)
    {
        final SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Select part [" + fullTitle + "](" + uri + ").");
        sendMessage.enableMarkdown(true);

        final InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        final List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        final int indexStart = jsonObject.getInt("idx");
        for (int i = indexStart; i < jsonObject.length() - 1 && indexStart + 10 >= i; i++)
        {
            if (i != indexStart && i % 5 == 0)
            {
                rowsInline.add(rowInline);
                rowInline = new ArrayList<>();
            }
            final InlineKeyboardButton button = new InlineKeyboardButton();
            final String strIdx = Integer.toString(i);
            button.setText(strIdx);
            final String callableData = jsonObject.getString(strIdx);
            button.setCallbackData(callableData);

            rowInline.add(button);
        }
        if (!rowInline.isEmpty())
        {
            rowsInline.add(rowInline);
        }

        final List<InlineKeyboardButton> navigationButton = getNavigationButton(jsonObject);
        if (!navigationButton.isEmpty())
        {
            rowsInline.add(navigationButton);
        }

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        // добавляем кнопки к сообщению
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        return sendMessage;
    }

    public static List<InlineKeyboardButton> getNavigationButton(final JSONObject jsonObject)
    {
        List<InlineKeyboardButton> navigation = new ArrayList<>();

        final long currentIdx = jsonObject.getLong("idx");
        if (currentIdx != 0)
        {
            final JSONObject previousPage = new JSONObject(jsonObject.toString());
            final long newPreviousIdx = currentIdx - 10 > 1 ? currentIdx - 10 : 0;
            previousPage.put("idx", newPreviousIdx);

            final InlineKeyboardButton previousButton = new InlineKeyboardButton();
            previousButton.setText("previous");
            previousButton.setCallbackData("navigation-" + newPreviousIdx);

            navigation.add(previousButton);
        }

        if (currentIdx + 10 < jsonObject.length())
        {
            final JSONObject nextPage = new JSONObject(jsonObject.toString());
            final long newNextIdx = currentIdx + 10 < jsonObject.length() ? currentIdx + 10 : jsonObject.length();
            nextPage.put("idx", newNextIdx);

            final InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("next");
            nextButton.setCallbackData("navigation-" + newNextIdx);

            navigation.add(nextButton);
        }

        return navigation;
    }
}