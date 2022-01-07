package com.github.mattthey.mathelperbot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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
 * Телеграм бот @mathelper_bot для скачивания аудиокниг с сайта knigavuhe.org
 */
public class MatHelperBot extends TelegramLongPollingBot
{
    private static final String BOT_DESCRIPTION = """
                Бот для скачивания аудиокниг с сайта knigavuhe.org. Отправьте ему ссылку, например
                https://knigavuhe.org/book/garri-potter-i-uznik-azkabana/
                И бот скинет вам список глав, вы можете выбрать любую часть.
                Внимание! Бот находится в стадии разработки, так что возможны некоторые ошибки. Прошу сообщать.
                """;
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
        try
        {
            if (update.hasMessage() && update.getMessage().hasText())
            {
                System.out.printf("New message has been received: %s", update);
                final String text = update.getMessage().getText();
                final String chatId = Long.toString(update.getMessage().getChatId());

                if (!text.contains("knigavuhe"))
                {
                    printHelp(update);
                    return;
                }

                sendAudioBookMenu(chatId, text);
            }
            else if (update.hasCallbackQuery())
            {
                // Set variables
                String callData = update.getCallbackQuery().getData();
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                long chatId = update.getCallbackQuery().getMessage().getChatId();

                if (callData.startsWith("navigation"))
                {
                    // для навигации и выбора главы, которую нужно скачать
                    final Message message = update.getCallbackQuery().getMessage();
                    final MessageEntity fullTitle = message.getEntities().get(0);
                    final String fullTitleUrl = fullTitle.getUrl();
                    final String fullTitleText = fullTitle.getText();
                    final int newCurrentIdx = Integer.parseInt(callData.substring(callData.indexOf('-') + 1));

                    final int totalSize = DownloaderAudioBookFromKnigavuhe.getTotalSizeParts(fullTitleUrl);

                    final EditMessageText editMessageText = getEditMessageText(messageId, Long.toString(chatId),
                            fullTitleText, fullTitleUrl, newCurrentIdx,
                            totalSize);

                    execute(editMessageText);
                }
                else
                {
                    // скачивание конкретной главы
                    final Message message = update.getCallbackQuery().getMessage();
                    final MessageEntity messageEntity = message.getEntities().get(0);
                    final String bookTitle = messageEntity.getText();
                    final String url = messageEntity.getUrl();
                    final String trackUrl = DownloaderAudioBookFromKnigavuhe.getTrackUrlById(
                            url, Integer.parseInt(callData)
                    );
                    long start = System.currentTimeMillis();

                    System.out.printf("Start download %s for user %s.\n", bookTitle, message.getChat().getUserName());
                    final File file = DownloaderAudioBookFromKnigavuhe.downloadPart(trackUrl);
                    long end = System.currentTimeMillis();

                    System.out.printf("Download complete %d ms. Start send %s for user %s.\n", end - start,
                            bookTitle, message.getChat().getUserName());

                    sendAudioFile(file, Long.toString(chatId));

                    System.out.printf("End send %s for user %s. %d ms\n", bookTitle, message.getChat().getUserName(),
                            end - start);
                }
            }
        }
        catch (TelegramApiException | IOException e)
        {
            e.printStackTrace();
        }
    }

    private void printHelp(final Update update)
    {
        final String chatId = Long.toString(update.getMessage().getChatId());

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(BOT_DESCRIPTION);
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
        System.out.printf("Delete file %s - %b.\n", audioFile.getPath(), audioFile.delete());
    }

    /**
     * Отправить текстовое сообщение
     * @param msg текст сообщения
     * @param chatId идентификатор чата
     */
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

    private void sendAudioBookMenu(final String chatId, final String uri) throws TelegramApiException
    {
        final String correctUri = uri.replace("m.knigavuhe.org", "knigavuhe.org");
        final Document document;
        try
        {
            document = Jsoup.connect(correctUri).get();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            sendMessage(e.getMessage(), chatId);
            return;
        }

        final String fullTitle = document.getElementsByClass("page_title").get(0).text();
        System.out.println("Download menu for " + fullTitle);

        // update
        final Elements playlist = document.getElementById("player_playlist").select("div.book_playlist_item");
        final int size = playlist.size();

        final SendMessage sendMessage = getSendMessageWithMenuForSelectPartsAudioBook(fullTitle, size,
                chatId, correctUri);

        execute(sendMessage);
    }

    private static EditMessageText getEditMessageText(final int messageId, final String chatId,
            final String fullTitleText, final String fullTitleUrl, final int newCurrentIdx, final int totalSize)
    {
        final EditMessageText editMessageText = new EditMessageText();
        editMessageText.setMessageId(messageId);

        editMessageText.setChatId(chatId);
        editMessageText.setText("Select part [" + fullTitleText + "](" + fullTitleUrl + ").");
        editMessageText.enableMarkdown(true);

        final InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        final List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        for (long i = newCurrentIdx; i < totalSize && newCurrentIdx + 10 > i; i++)
        {
            if (i != newCurrentIdx && i % 5 == 0)
            {
                rowsInline.add(rowInline);
                rowInline = new ArrayList<>();
            }
            final InlineKeyboardButton button = new InlineKeyboardButton();
            final String strIdx = Long.toString(i);
            button.setText(strIdx);
            button.setCallbackData(strIdx);

            rowInline.add(button);
        }
        if (!rowInline.isEmpty())
        {
            rowsInline.add(rowInline);
        }

        final List<InlineKeyboardButton> navigationButton = getNavigationButton(newCurrentIdx, totalSize);
        if (!navigationButton.isEmpty())
        {
            rowsInline.add(navigationButton);
        }

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        // добавляем кнопки к сообщению
        editMessageText.setReplyMarkup(inlineKeyboardMarkup);

        return editMessageText;
    }

    private static SendMessage getSendMessageWithMenuForSelectPartsAudioBook(
            final String fullTitle, final int totalLength, final String chatId, final String uri)
    {
        final SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Select part [" + fullTitle + "](" + uri + ").");
        sendMessage.enableMarkdown(true);

        final InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        final List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        for (int i = 0; i < 10 && i < totalLength; i++)
        {
            if (i != 0 && i % 5 == 0)
            {
                rowsInline.add(rowInline);
                rowInline = new ArrayList<>();
            }
            final InlineKeyboardButton button = new InlineKeyboardButton();
            final String strIdx = Integer.toString(i);
            button.setText(strIdx);
            button.setCallbackData(strIdx);

            rowInline.add(button);
        }
        if (!rowInline.isEmpty())
        {
            rowsInline.add(rowInline);
        }

        final List<InlineKeyboardButton> navigationButton = getNavigationButton(0, totalLength);
        if (!navigationButton.isEmpty())
        {
            rowsInline.add(navigationButton);
        }

        inlineKeyboardMarkup.setKeyboard(rowsInline);
        // добавляем кнопки к сообщению
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        return sendMessage;
    }

    private static List<InlineKeyboardButton> getNavigationButton(final long currentIdx, final int totalLength)
    {
        List<InlineKeyboardButton> navigation = new ArrayList<>();

        if (currentIdx != 0)
        {
            final long newPreviousIdx = Math.max(currentIdx - 10, 0);

            final InlineKeyboardButton previousButton = new InlineKeyboardButton();
            previousButton.setText("previous");
            previousButton.setCallbackData("navigation-" + newPreviousIdx);

            navigation.add(previousButton);
        }

        if (currentIdx + 10 <= totalLength)
        {
            final long newNextIdx = Math.min(currentIdx + 10, totalLength);

            final InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("next");
            nextButton.setCallbackData("navigation-" + newNextIdx);

            navigation.add(nextButton);
        }

        return navigation;
    }

    /**
     * Порезать mp3 файл на части,
     * @param fileToSplit mp3 файл, который нужно порезать
     * @return коллекция порезанных mp3 файлов
     */
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
            e.printStackTrace();
            return List.of();
        }
        return newFiles;
    }

    /**
     * Записать N колличество байт из inputStream в outputStream
     * @param inputStream стрим для чтения байтов
     * @param outputStream стрим для записи байтов
     * @param numBytes колличество байтов, которое нужно считать
     * @throws IOException произошла ошибка i/o
     */
    private static void readWrite(BufferedInputStream inputStream, BufferedOutputStream outputStream, int numBytes) throws IOException
    {
        byte[] buf = new byte[numBytes];
        int val = inputStream.read(buf);
        if(val != -1)
        {
            outputStream.write(buf);
        }
    }
}