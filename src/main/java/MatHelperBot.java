import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 *
 */
public class MatHelperBot extends TelegramLongPollingBot
{
    private static final String update_msg_text = "1".repeat(64);
    private static final PrintWriter OUT = new PrintWriter(System.out);

    private final String botUsername;
    private final String botToken;

    public MatHelperBot(String botUsername, String botToken)
    {
        this.botUsername = botUsername;
        this.botToken = botToken;
    }

    @Override
    public String getBotUsername()
    {
        return botUsername;
    }

    @Override
    public String getBotToken()
    {
        return botToken;
    }

    @Override
    public void onUpdateReceived(final Update update)
    {
        if (update.hasMessage() && update.getMessage().hasText())
        {
            OUT.printf("New message has been received: %s", update);
            final String text = update.getMessage().getText();
            final String chatId = Long.toString(update.getMessage().getChatId());

            if (!text.contains("knigavuhe"))
            {
                mainMenu(update);
                return;
            }

            final Thread thread = new Thread(new WorkerDownloaderFromKnigavuhe(chatId, text, this));
            thread.setDaemon(true);
            thread.start();
        }
        else if (update.hasCallbackQuery())
        {
            // Set variables
            String callData = update.getCallbackQuery().getData();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callData.equals(update_msg_text))
            {
                String answer = "Updated message text";
                EditMessageText newMessage = new EditMessageText();
                newMessage.setChatId(Long.toString(chatId));
                newMessage.setMessageId(Math.toIntExact(messageId));
                newMessage.setText(answer);
                try
                {
                    execute(newMessage);
                }
                catch (TelegramApiException e)
                {
                    e.printStackTrace();
                }
            }
            else if (callData.startsWith("navigation"))
            {
                final Message message = update.getCallbackQuery().getMessage();
                final MessageEntity fullTitle = message.getEntities().get(0);
                final String fullTitleUrl = fullTitle.getUrl();
                final String fullTitleText = fullTitle.getText();
                final Long newCurrentIdx = Long.parseLong(callData.substring(callData.indexOf('-') + 1));

                try
                {
                    final Document document = Jsoup.connect(fullTitleUrl).get();
                    final List<String> trackUrls = DownloaderAudioBookFromKnigavuhe.getTrackUrls(document);
                    final JSONObject json = WorkerDownloaderFromKnigavuhe.getJson(trackUrls);

                    json.put("idx", newCurrentIdx);

                    // ----
                    final EditMessageText editMessageText = new EditMessageText();
                    editMessageText.setMessageId(messageId);

                    editMessageText.setChatId(Long.toString(chatId));
                    editMessageText.setText("Select part [" + fullTitleText + "](" + fullTitleUrl + ").");
                    editMessageText.enableMarkdown(true);

                    final InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    final List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
                    List<InlineKeyboardButton> rowInline = new ArrayList<>();
                    final int indexStart = json.getInt("idx");
                    for (int i = indexStart; i < json.length() - 1 && indexStart + 10 >= i; i++)
                    {
                        if (i != indexStart && i % 5 == 0)
                        {
                            rowsInline.add(rowInline);
                            rowInline = new ArrayList<>();
                        }
                        final InlineKeyboardButton button = new InlineKeyboardButton();
                        final String strIdx = Integer.toString(i);
                        button.setText(strIdx);
                        final String callableData = json.getString(strIdx);
                        button.setCallbackData(callableData);

                        rowInline.add(button);
                    }

                    final List<InlineKeyboardButton> navigationButton =
                            WorkerDownloaderFromKnigavuhe.getNavigationButton(json);
                    if (!navigationButton.isEmpty())
                    {
                        rowsInline.add(navigationButton);
                    }

                    inlineKeyboardMarkup.setKeyboard(rowsInline);
                    // добавляем кнопки к сообщению
                    editMessageText.setReplyMarkup(inlineKeyboardMarkup);
                    // ----


                    execute(editMessageText);
                }
                catch (IOException | TelegramApiException e)
                {
                    e.printStackTrace();
                }
            }
            else if (callData.contains("knigavuhe"))
            {
                final File file = DownloaderAudioBookFromKnigavuhe.downloadPart(callData);
                sendAudioFile(file, Long.toString(chatId));
                file.delete();
            }
        }
    }

    private void mainMenu(final Update update)
    {
        final String text = update.getMessage().getText();
        final String chatId = Long.toString(update.getMessage().getChatId());

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("You send /start");
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        final InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText("Update message text");
        inlineKeyboardButton.setCallbackData(update_msg_text);
        rowInline.add(inlineKeyboardButton);
        // Set the keyboard to the markup
        rowsInline.add(rowInline);
        // Add it to the message
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try
        {
            execute(message);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }

    private void sendAudioFile(final File audioFile, final String chatId)
    {
        final SendAudio sendAudio = new SendAudio();
        sendAudio.setChatId(chatId);
        sendAudio.setAudio(new InputFile(audioFile));
        try
        {
            execute(sendAudio);
        }
        catch (TelegramApiException e)
        {
            throw new RuntimeException(e);
        }
    }
}