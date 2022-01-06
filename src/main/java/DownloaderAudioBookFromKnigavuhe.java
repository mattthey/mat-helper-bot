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
 *
 */
public class DownloaderAudioBookFromKnigavuhe
{
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Pattern MP3_URL_PATTERN = Pattern.compile("https.+?\\.mp3");
    private static final Pattern BACKSLASH = Pattern.compile("\\\\");

//    public static void downloadAudionBook(String uri) throws IOException, InterruptedException
//    {
//        final Document document = Jsoup.connect(uri).get();
//
//        final String title = getTitle(document);
//        final String author = getAuthor(document);
//        System.out.printf("Download %s - %s\n", author, title);
//
//        final List<String> trackUrls = getTrackUrls(document);
//
//        final int size = trackUrls.size();
//        for (int i = 0; i < size; i++)
//        {
//            final String newUrl = BACKSLASH.matcher(trackUrls.get(i)).replaceAll("");
//            final HttpRequest request = HttpRequest.newBuilder(URI.create(newUrl)).build();
//
//            final Path path = Main.OUTPUT_DIR.resolve(author + " - " + title + " - " + (i + 1) + ".mp3");
//            HTTP_CLIENT.send(request, BodyHandlers.ofFile(path));
//
//            System.out.printf("Download complete [%d/%d]\n", i + 1, size);
//        }
//    }

    public static String getTitle(Document document)
    {
        final Elements bookTitleElements = document.getElementsByClass("book_title_elem book_title_name");
        if (bookTitleElements.isEmpty())
        {
            return "No TITLE";
        }
        return bookTitleElements.get(0).ownText();
    }

    public static String getAuthor(Document document)
    {
        final Elements bookTitleElements = document.getElementsByClass("book_title_elem");
        final Elements author = bookTitleElements.select("[itemprop=author]");
        final Elements authorLink = author.get(0).select("> a");
        if (authorLink.isEmpty())
        {
            return "NO AUTHOR";
        }
        return authorLink.text();
    }

    public static List<String> getTrackUrls(Document document)
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

    public static File downloadPart(final String uri, final String bookTitle)
    {
        final Path file = Main.OUTPUT_DIR.resolve(bookTitle);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).build();
        try
        {
            final HttpResponse<Path> response = HTTP_CLIENT.send(request, BodyHandlers.ofFile(file));
            if (response.statusCode() != 200)
            {
                throw new RuntimeException(request.toString());
            }
            return response.body().toFile();
        }
        catch (IOException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }
}