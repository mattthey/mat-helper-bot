import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
    private static final PrintWriter OUT = new PrintWriter(System.out);
    // максимальный размер файла, который может отправить тг 50mb в байтах
    private static final int MAX_SIZE_AUDIO_FILE_TG = 50 * 1024 * 1024;

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
                printHelp(update);
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

            if (callData.startsWith("navigation"))
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

                    execute(editMessageText);
                }
                catch (IOException | TelegramApiException e)
                {
                    e.printStackTrace();
                }
            }
            else if (callData.contains("knigavuhe"))
            {
                final Message message = update.getCallbackQuery().getMessage();
                final String bookTitle = message.getEntities().get(0).getText();
                long start = System.currentTimeMillis();

                System.out.printf("Start download %s for user %s.\n", bookTitle, message.getChat().getUserName());
                final File file = DownloaderAudioBookFromKnigavuhe.downloadPart(callData, bookTitle);
                long end = System.currentTimeMillis();

                System.out.printf("Start send %s for user %s. %d ms\n", bookTitle, message.getChat().getUserName(),
                        end - start);

                sendAudioFile(file, Long.toString(chatId));

                System.out.printf("End send %s for user %s. %d ms\n", bookTitle, message.getChat().getUserName(),
                        end - start);
            }
        }
    }

    private void printHelp(final Update update)
    {
        final String chatId = Long.toString(update.getMessage().getChatId());

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("""
                Бот для скачивания аудиокниг с сайта knigavuhe.org. Отправьте ему ссылку, например
                https://knigavuhe.org/book/garri-potter-i-uznik-azkabana/
                И бот скинет вам список глав, вы можете выбрать любую часть.
                Внимание! Бот находится в стадии разработки, так что возможны некоторые ошибки. Прошу сообщать.
                """);
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
        final List<File> files = splitMp3File(audioFile);

        final SendAudio sendAudio = new SendAudio();
        sendAudio.setChatId(chatId);

        if (files.size() > 1)
        {
            sendMessage("Телеграм имеет ограничения для отправки аудиофайлов больше 50mb. Так что книга будет "
                    + "отправлена по частям Всего файлов " + files.size(), chatId);
        }
        for (final File file : files)
        {
            sendAudio.setAudio(new InputFile(file));
            try
            {
                execute(sendAudio);
            }
            catch (TelegramApiException e)
            {
                e.printStackTrace();
            }
            System.out.printf("Delete file %s - %b.\n", file.getPath(), file.delete());
        }
    }

    private void sendMessage(final String msg, final String chatId)
    {
        final SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(msg);
        try
        {
            execute(sendMessage);
        }
        catch (TelegramApiException e)
        {
            e.printStackTrace();
        }
    }

    private static List<File> splitMp3File(final File fileToSplit)
    {
        final long fileSizeInBits = fileToSplit.length();

        if (fileSizeInBits < MAX_SIZE_AUDIO_FILE_TG)
        {
            System.out.printf("File size = %d bytes it lower max size for audio file in telegram %d bytes (50mb).\n",
                    fileSizeInBits, MAX_SIZE_AUDIO_FILE_TG);
            return List.of(fileToSplit);
        }
        // колличество частей на которые будем резать mp3 файл
        final long countSplit;
        if (fileSizeInBits % MAX_SIZE_AUDIO_FILE_TG == 0)
        {
            countSplit = fileSizeInBits / MAX_SIZE_AUDIO_FILE_TG;
        }
        else
        {
            countSplit = fileSizeInBits / MAX_SIZE_AUDIO_FILE_TG + 1;
        }

        final long sizeOnePart = fileSizeInBits / countSplit;
        List<File> newFiles = new ArrayList<>();


        int maxReadBufferSize = 8 * 1024; //8KB
        try (final BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(fileToSplit)))
        {
            for (long part = 0; part < countSplit; part++)
            {
                final File newFile = Main.OUTPUT_DIR.resolve(
                        fileToSplit.getName().split("\\.")[0] + part + ".mp3").toFile();
                try (final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(newFile)))
                {
                    final int numReads = (int)(sizeOnePart / maxReadBufferSize);
                    final int numRemainingRead = (int)(sizeOnePart % maxReadBufferSize);
                    for (int i = 0; i < numReads; i++)
                    {
                        readWrite(bufferedInputStream, bufferedOutputStream, maxReadBufferSize);
                    }
                    if (numRemainingRead > 0)
                    {
                        readWrite(bufferedInputStream, bufferedOutputStream, numRemainingRead);
                    }
                }
                newFiles.add(newFile);
            }
        }
        catch (IOException e)
        {
            System.out.println(e);
            e.printStackTrace();
            return List.of();
        }
        return newFiles;
    }

    private static void readWrite(BufferedInputStream raf, BufferedOutputStream bw, int numBytes) throws IOException
    {
        byte[] buf = new byte[numBytes];
        int val = raf.read(buf);
        if(val != -1)
        {
            bw.write(buf);
        }
    }
}