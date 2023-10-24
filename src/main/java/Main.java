import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Main {

    static String basePath = "https://pl.wikipedia.org/wiki/";

    static Set<String> unvisitedArticles;
    static Map<String, Set<String>> visitedArticles;

    public static void main(String[] args) {
        try {
            unvisitedArticles = new HashSet<>();
            visitedArticles = new HashMap<>();
            loadArticlesUnvisited();
            loadArticlesVisitedAndNested();
            while (!unvisitedArticles.isEmpty()) {

                Thread.sleep(1000);
                Iterator<String> iterator = unvisitedArticles.iterator();

                if (iterator.hasNext()) {
                    System.out.println("Visited Articles: " + visitedArticles.size());
                    System.out.println("Unvisited Articles: " + unvisitedArticles.size());
                    String baseArticle = iterator.next();
                    Set<String> nestedArticles = downloadNestedArticles(baseArticle);
                    iterator.remove();
                    writeArticlesToFile(baseArticle, nestedArticles);
                    nestedArticles.forEach(nestedArticle -> {
                        if (!visitedArticles.containsKey(nestedArticle)) {
                            unvisitedArticles.add(nestedArticle);
                        }
                    });
                    visitedArticles.put(baseArticle, nestedArticles);
                }
            }
//            String url = "https://pl.wikipedia.org/wiki/Specjalna:Losowa_strona";
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeArticlesToFile(String baseArticle, Set<String> nestedArticles) {
        String filePath = "./articles.csv";
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                file.createNewFile();
            }
            if (!file.exists()) {
                return;
            }
            try (FileWriter fileWriter = new FileWriter(filePath, true)) {
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.append(baseArticle);
                StringBuilder builder = new StringBuilder();
                for (String nestedArticle : nestedArticles) {
                    builder.append(";").append(nestedArticle);
                }
                builder.append("\n");
                bufferedWriter.write(builder.toString());
                bufferedWriter.close();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadArticlesUnvisited() {
        String filePath = "./articles_unvisited.txt";
        try {
            File file = new File(filePath);
            if (file.exists()) {
                FileReader fileReader = new FileReader(filePath);
                try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        unvisitedArticles.add(line.trim());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadArticlesVisitedAndNested() {
        String filePath = "./articles.csv";
        try {
            File file = new File(filePath);
            boolean fileCreated = file.createNewFile();
            if (fileCreated) {
                return;
            }
            FileReader fileReader = new FileReader(filePath);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] tokens = line.split(";");
                if (tokens.length == 0) {
                    continue;
                } else {
                    String article = tokens[0];
                    Set<String> refArticles = new HashSet<String>(Arrays.asList(tokens).subList(1, tokens.length));
                    visitedArticles.put(article, refArticles);
                    unvisitedArticles.addAll(refArticles);
                }
            }
            visitedArticles.forEach((article, nestedArticles) -> {
                unvisitedArticles.remove(article);
            });
            bufferedReader.close();
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<String> downloadNestedArticles(String article) throws IOException {

        String urlStr = basePath + article;
        URL url = URI.create(urlStr).toURL();
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                String finalLocationEncoded = conn.getURL().toString();
                String finalLocation = URLDecoder.decode(finalLocationEncoded, StandardCharsets.UTF_8);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }

                Set<String> links = new HashSet<>();
                Document document = Jsoup.parse(content.toString());
                Elements linkElements = document.select("a[href]");

                for (Element linkElement : linkElements) {
                    String link = linkElement.attr("href");

                    if (link.contains("{")) {
                        System.out.println("Containing: '{'");
                        System.out.println("Final Location: " + finalLocation);
                        System.out.println("Link: " + link);
                        continue;
                    }
                    URI combinedURI = new URI(finalLocation).resolve(link);
                    if (combinedURI.getPath().contains(":")) {
                        continue;
                    }
                    URI upToPathURI = new URI(
                            combinedURI.getScheme(),
                            combinedURI.getAuthority(),
                            combinedURI.getPath(),
                            null,
                            null
                    );

                    String resolvedUrlStringEncoded = upToPathURI.toString();

                    String resolvedUrlString = URLDecoder.decode(resolvedUrlStringEncoded, StandardCharsets.UTF_8);
                    if (!resolvedUrlString.startsWith(basePath)) {
                        continue;
                    }
                    if (resolvedUrlString.startsWith(finalLocation)) {
                        continue;
                    }
                    links.add(resolvedUrlString.substring(basePath.length()));
                }
                reader.close();
                return links;

            } else {
                throw new IOException("HTTP Request failed with response code: " + responseCode);
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
