package com.github.mattthey.mathelperbot;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 * Утилитарный класс для взаимодействия с сайтом
 */
public class DownloaderAudioBookFromKnigavuhe
{
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Pattern MP3_URL_PATTERN = Pattern.compile("https.+?\\.mp3");
    private static final Pattern BACKSLASH = Pattern.compile("\\\\");

    private static List<String> getTrackUrls(Document document)
    {
        final String dataNodeWithMp3 = document.getElementsByTag("script")
                .stream()
                .flatMap(e -> e.dataNodes().stream())
                .map(DataNode::getWholeData)
                .filter(data -> data.contains("mp3"))
                .findAny().orElseThrow();
        final Matcher matcher = MP3_URL_PATTERN.matcher(dataNodeWithMp3);

        return matcher.results()
                .map(matchResult -> matcher.group(0))
                .map(uri -> BACKSLASH.matcher(uri).replaceAll(""))
                .toList();
    }

    public static String getTrackUrlById(final String urlBook, final int id) throws IOException
    {
        final Document document = Jsoup.connect(urlBook).get();
        final List<String> trackUrls = getTrackUrls(document);
        return trackUrls.get(id);
    }

    public static File downloadPart(final String uri)
    {
        final Path file = Main.OUTPUT_DIR.resolve(Long.toString(System.nanoTime()));
        final HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).build();
        try
        {
            final long start = System.currentTimeMillis();
            System.out.println("Start send request");
            final HttpResponse<Path> response = HTTP_CLIENT.send(request, BodyHandlers.ofFile(file));
            final long end = System.currentTimeMillis();
            System.out.printf("End send request status code %d. %d ms\n", response.statusCode(), end - start);
            if (response.statusCode() != 200)
            {
                System.out.printf("Error send request, status code %d.\n", response.statusCode());
                throw new RuntimeException(request.toString());
            }
            return response.body().toFile();
        }
        catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static int getTotalSizeParts(final String url) throws IOException
    {
        final Document document = Jsoup.connect(url).get();
        final Elements playerPlaylist = document.getElementById("player_playlist")
                .select("div.book_playlist_item");
        return playerPlaylist.size();
    }
}