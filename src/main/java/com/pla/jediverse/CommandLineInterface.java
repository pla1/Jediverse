package com.pla.jediverse;

import com.google.gson.*;
import org.jsoup.Jsoup;

import javax.net.ssl.HttpsURLConnection;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CommandLineInterface {
    // private static BufferedReader console;
    private static Scanner console;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final int DEFAULT_QUANTITY = 20;
    private final String DEFAULT_AUDIO_FILE_NAME = "ding.wav";
    private JsonObject settingsJsonObject;
    private JsonArray jsonArrayAll = new JsonArray();
    private Logger logger;
    private ArrayList<WebSocket> webSockets = new ArrayList<>();
    private JsonArray jsonArrayFollowing = new JsonArray();
    private ArrayList<JsonElement> mediaArrayList = new ArrayList<>();
    private JsonArray jsonArrayAccounts = new JsonArray();
    private File jsonLoggerFile;
    private ArrayList<String> streams = new ArrayList<String>();

    private void clearGlobalVariables() {
        jsonArrayAccounts = new JsonArray();
        mediaArrayList = new ArrayList<>();
        jsonArrayFollowing = new JsonArray();
        webSockets = new ArrayList<>();
        jsonArrayAll = new JsonArray();
    }

    private void closeWebSockets() {
        if (webSockets.isEmpty()) {
            return;
        }
        int quantity = 0;
        for (WebSocket webSocket : webSockets) {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
                quantity++;
            }
        }
        webSockets.clear();
        streams.clear();
        System.out.format("Closed %d WebSockets.\n", quantity);
    }

    private CommandLineInterface() {
        setup();
        try {
            mainRoutine();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new CommandLineInterface();
        System.exit(0);
    }

    private static HttpRequest.BodyPublisher ofMimeMultipartData(Map<Object, Object> data,
                                                                 String boundary) throws IOException {
        var byteArrays = new ArrayList<byte[]>();
        byte[] separator = ("--" + boundary + "\r\nContent-Disposition: form-data; name=")
                .getBytes(StandardCharsets.UTF_8);
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            byteArrays.add(separator);
            if (entry.getValue() instanceof Path) {
                var path = (Path) entry.getValue();
                String mimeType = Files.probeContentType(path);
                byteArrays.add(("\"" + entry.getKey() + "\"; filename=\"" + path.getFileName()
                        + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byte[] fileBytes = Files.readAllBytes(path);
                //      System.out.format("Files.readAllBytes %d length.\n%s", fileBytes.length, new String(fileBytes));
                byteArrays.add(fileBytes);
                byteArrays.add("\r\n".getBytes(StandardCharsets.UTF_8));
            } else {
                byteArrays.add(("\"" + entry.getKey() + "\"\r\n\r\n" + entry.getValue() + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
        byteArrays.add(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
        //      for (byte[] bytes:byteArrays) {
        //          System.out.format("%s\n", new String(bytes));
        //      }
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }

    private void playAudio() {
        String audioFileName = getAudioFileName();
        if ("none".equalsIgnoreCase(audioFileName)) {
            return;
        }
        AudioInputStream audioInputStream;
        Clip clip;
        try {
            File file = new File(audioFileName).getAbsoluteFile();
            //    System.out.format("Audio file: %s\n", file.getAbsolutePath());
            audioInputStream = AudioSystem.getAudioInputStream(file);
            clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
        //finally {
        //      Utils.close(clip, audioInputStream);
        //   }
    }

    private void setup() {
        //   console = new BufferedReader(new InputStreamReader(System.in));
        console = new Scanner(System.in);
        logger = getLogger();
        JsonArray settingsJsonArray = getSettings();
        while (settingsJsonArray == null || settingsJsonArray.size() == 0) {
            createApp();
            settingsJsonArray = getSettings();
        }
        while (settingsJsonObject == null) {
            if (settingsJsonArray.size() == 1) {
                JsonElement jsonElement = settingsJsonArray.get(0);
                settingsJsonObject = jsonElement.getAsJsonObject();
            } else {
                settingsJsonObject = chooseInstance(settingsJsonArray);
            }
        }
        System.out.format("Using instance: %s\n", settingsJsonObject.get("instance"));
    }

    private String getAudioFileName() {
        String audioFileName = Utils.getProperty(settingsJsonObject, "audioFileName");
        if (Utils.isBlank(audioFileName)) {
            return "ding.wav";
        } else {
            return audioFileName;
        }
    }

    private int getQuantity() {
        int quantity = Utils.getInt(Utils.getProperty(settingsJsonObject, "quantity"));
        if (quantity == 0) {
            quantity = DEFAULT_QUANTITY;
        }
        return quantity;
    }

    private void mainRoutine() throws Exception {
        String line;
        while (console.hasNext()) {
            line = console.nextLine();
            if (Utils.isBlank(line)) {
                System.out.format("Line is blank. %s\n", line);
                continue;
            }
            String[] words = line.split("\\s+");
            if (line.startsWith("search") && words.length > 1) {
                search(line);
            }
            if ("account-search".equals(words[0]) && words.length > 1) {
                accountSearch(line);
            }
            if (words.length == 2 && "account-follow".equals(words[0]) && Utils.isNumeric(words[1])) {
                accountFollowUnfollow(Utils.getInt(words[1]), true);
            }
            if (words.length == 2 && "account-unfollow".equals(words[0]) && Utils.isNumeric(words[1])) {
                accountFollowUnfollow(Utils.getInt(words[1]), false);
            }
            if (line.startsWith("upload") && words.length > 1) {
                try {
                    upload(line);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (line.equals("upload-browser")) {
                try {
                    uploadWithFileBrowser();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if ("about".equals(words[0])) {
                about();
            }
            if ("blocks".equals(words[0])) {
                blocks();
            }
            if ("upload-clear".equals(words[0])) {
                clearMediaArrayList();
            }
            if ("following".equals(line)) {
                following();
            }
            if ("lists".equals(line)) {
                lists();
            }
            if ("list-accounts".equals(words[0]) && Utils.isNumeric(words[1])) {
                listAccounts(words[1]);
            }
            if ("list-create".equals(words[0]) && words.length > 1) {
                String title = line.substring("list-create".length()).trim();
                JsonElement jsonElement = listCreate(title);
                if (jsonElement == null) {
                    System.out.format("List %s not created.\n", title);
                }
                System.out.format("List id: %s title: %s created.\n", Utils.getProperty(jsonElement, "id"), Utils.getProperty(jsonElement, "title"));
            }
            if ("list-delete".equals(words[0]) && words.length == 2 && Utils.isNumeric(words[1])) {
                String id = words[1];
                listDelete(id);
            }
            if ("list-delete-account".equals(words[0]) && words.length == 3 && Utils.isNumeric(words[1]) && Utils.isNumeric(words[2])) {
                String listId = words[1];
                String accountIndex = words[2];
                listDeleteAccount(listId, accountIndex);
            }
            if ("list-add-accounts".equals(words[0]) && words.length == 3 && Utils.isNumeric(words[1])) {
                String id = words[1];
                listAddAccount(id, words[2]);
            }
            if ("fed".equals(line)) {
                timeline("public", "&local=false");
            }
            if ("quantity".equals(words[0]) && words.length == 2 && Utils.isNumeric(words[1])) {
                int qty = Utils.getInt(words[1]);
                updateQuantitySettings(qty);
            }
            if ("audio".equals(words[0]) && words.length == 2) {
                updateAudioFileNameSettings(words[1]);
            }
            if ("aa".equals(line)) {
                createApp();
            }
            if ("sa".equals(line)) {
                settingsJsonObject = chooseInstance(getSettingsJsonArray());
                System.out.format("Using instance: %s\n", settingsJsonObject.get("instance"));
            }
            if ("da".equals(line)) {
                deleteInstance(getSettingsJsonArray());
                settingsJsonObject = chooseInstance(getSettingsJsonArray());
            }
            if ("local".equals(line)) {
                timeline("public", "&local=true");
            }
            if ("stream-public".equals(line) ||
                    "stream-public-local".equals(line) ||
                    (words[0].equals("stream-list") && words.length == 2 && Utils.isNumeric(words[1])) ||
                    (words[0].equals("stream-hashtag") && words.length == 2) ||
                    "stream-user".equals(line) ||
                    "stream-direct".equals(line)) {

                String stream = null;
                if ("stream-public".equals(line)) {
                    stream = "public";
                }
                if ("stream-public-local".equals(line)) {
                    stream = "public:local";
                }
                if ("stream-user".equals(line)) {
                    stream = "user";
                }
                if ("stream-direct".equals(line)) {
                    stream = "direct";
                }
                if (words[0].equals("stream-list") && words.length == 2 && Utils.isNumeric(words[1])) {
                    stream = String.format("list&list=%s", words[1]);
                }
                if (words[0].equals("stream-hashtag") && words.length == 2) {
                    stream = String.format("hashtag&tag=%s", Utils.urlEncodeComponent(words[1]));
                }
                if (Utils.isNotBlank(stream)) {
                    //       var client = HttpClient.newHttpClient();
                    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                    String urlString = String.format("wss://%s/api/v1/streaming/?stream=%s&access_token=%s",
                            Utils.getProperty(settingsJsonObject, "instance"), stream, Utils.getProperty(settingsJsonObject, "access_token"));
                    WebSocketListener webSocketListener = new WebSocketListener(stream);
                    WebSocket webSocket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create(urlString), webSocketListener).join();
                    webSockets.add(webSocket);
                    streams.add(stream);
                }
            }
            if ("stop".equals(line)) {
                closeWebSockets();
            }
            if ("timeline".equals(line) || "tl".equals(line)) {
                timeline("home", "");
            }
            if ("notifications".equals(line) || "note".equals(line)) {
                notifications("");
            }
            if (words.length > 1 && "post-direct".equals(words[0])) {
                String text = line.substring(12);
                postStatus(text, null, "direct");
            }
            if (words.length > 1 && "post".equals(words[0])) {
                String text = line.substring(5);
                postStatus(text, null, "public");
            }
            if (words.length > 1 && "post-private".equals(words[0])) {
                String text = line.substring(5);
                postStatus(text, null, "private");
            }
            if (words.length > 1 && "post-unlisted".equals(words[0])) {
                String text = line.substring(5);
                postStatus(text, null, "unlisted");
            }
            if (words.length > 2 && ("rep".equals(words[0]) || "reply".equals(words[0]))) {
                if (Utils.isNumeric(words[1])) {
                    int index = Utils.getInt(words[1]);
                    if (index > jsonArrayAll.size()) {
                        System.out.format("Item at index: %d not found.\n", index);
                    } else {
                        JsonElement jsonElement = jsonArrayAll.get(index);
                        String type = Utils.getProperty(jsonElement, "type");
                        if ("favourite".equals(type) || "reblog".equals(type) || "follow".equals(type)) {
                            System.out.format("You can't reply to a %s.\n", type);
                        } else {
                            String text = line.substring(line.indexOf(words[1]) + words[1].length());
                            if ("mention".equals(type)) {
                                JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
                                String visibility = Utils.getProperty(statusJe, "visibility");
                                postStatus(text, Utils.getProperty(statusJe, "id"), visibility);
                            } else {
                                String visibility = Utils.getProperty(jsonElement, "visibility");
                                postStatus(text, Utils.getProperty(jsonElement, "id"), visibility);
                            }
                        }
                    }
                }

            }
            if (words.length == 2 && "fav".equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                String id = Utils.getProperty(jsonElement, "id");
                //     System.out.format("Fav this: %s\n", jsonElement.toString());
                if ("mention".equals(Utils.getProperty(jsonElement, "type"))) {
                    JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
                    id = Utils.getProperty(statusJe, "id");
                }
                favourite(id);
            }
            if (words.length == 2 && "url".equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                System.out.format("%d %s\n", index, Utils.getProperty(jsonElement, "url"));
            }
            if (words.length == 2 && "go".equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                String urlString = Utils.getProperty(jsonElement, "url");
                if (Utils.isBlank(urlString)) {
                    JsonElement status = jsonElement.getAsJsonObject().get("status");
                    urlString = Utils.getProperty(status, "url");
                }
                if (Utils.isNotBlank(urlString)) {
                    Utils.run(new String[]{"xdg-open", urlString});
                }
            }
            if (words.length == 2 && "unfav".equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                System.out.format("Fav this: %s\n", jsonElement.toString());
                unfavourite(Utils.getProperty(jsonElement, "id"));
            }
            if ("quit".equals(line) || "exit".equals(line)) {
                System.exit(0);
            }
            if ("help".equals(line) || "?".equals(line)) {
                System.out.println(Utils.readFileToString("help.txt"));
            }
            if ("whoami".equals(line)) {
                printWhoAmI();
            }
        }


    }

    private void printWhoAmI() {
        JsonElement jsonElement = whoami();
        System.out.format("%s %s\n", Utils.getProperty(jsonElement, "username"), Utils.getProperty(jsonElement, "url"));
    }

    private void listDeleteAccount(String listId, String accountIndex) {
        JsonArray accountsJsonArray = listAccounts(listId);
        JsonElement accountJsonElement = accountsJsonArray.get(Utils.getInt(accountIndex));
        if (accountJsonElement == null) {
            System.out.format("Account index %s not found for list ID %s.\n", accountIndex, listId);
            return;
        }
        JsonArray arrayOfIds = new JsonArray();
        arrayOfIds.add(Utils.getProperty(accountJsonElement, "id"));
        JsonObject params = new JsonObject();
        params.add("account_ids", arrayOfIds);
        String urlString = String.format("https://%s/api/v1/lists/%s/accounts", Utils.getProperty(settingsJsonObject, "instance"), listId);
        int statusCode = deleteAsJson(Utils.getUrl(urlString), params.toString());
        System.out.format("Status code %d from removing account ID %s index %s from list id %s.\n",
                statusCode, Utils.getProperty(accountJsonElement, "id"), accountIndex, listId);
        listAccounts(listId);
    }

    private void listDelete(String id) {
        String urlString = String.format("https://%s/api/v1/lists/%s", Utils.getProperty(settingsJsonObject, "instance"), id);
        int statusCode = deleteAsJson(Utils.getUrl(urlString), null);
        System.out.format("Delete list id: %s returned status code: %d.\n", id, statusCode);
    }

    private void accountFollowUnfollow(int index, boolean follow) {
        if (jsonArrayAccounts.size() == 0) {
            System.out.println("No accounts to follow. Search for account first using command account-search.");
            return;
        }
        if (index < 0 || index > jsonArrayAccounts.size()) {
            System.out.format("Index %d not found. Here is the list of accounts from the latest account-search.\n", index);
            int i = 0;
            for (JsonElement account : jsonArrayAccounts) {
                System.out.format("%d %s %s %s %s %s\n",
                        i++, Utils.getProperty(account, "acct"), Utils.getProperty(account, "username"), Utils.getProperty(account, "display_name"), Utils.getProperty(account, "url"), Jsoup.parse(Utils.getProperty(account, "note")).text());
            }
            return;
        }
        JsonElement accountJsonElement = jsonArrayAccounts.get(index);
        System.out.format("%s %s %s\n",
                Utils.getProperty(accountJsonElement, "id"), Utils.getProperty(accountJsonElement, "acct"), Utils.getProperty(accountJsonElement, "username"));
        String verb = "unfollow";
        if (follow) {
            verb = "follow";
        }
        String urlString = String.format("https://%s/api/v1/accounts/%s/%s", Utils.getProperty(settingsJsonObject, "instance"), Utils.getProperty(accountJsonElement, "id"), verb);
        JsonObject jsonObjectResult = postAsJson(Utils.getUrl(urlString), null);
        boolean following = Utils.isYes(Utils.getProperty(jsonObjectResult, "following"));
        boolean followedBy = Utils.isYes(Utils.getProperty(jsonObjectResult, "followed_by"));
        System.out.format("%s %s %s\n", Utils.getProperty(accountJsonElement, "acct"), Utils.getProperty(accountJsonElement, "username"), Utils.getProperty(accountJsonElement, "display_name"));
        if (following && followedBy) {
            System.out.format("You are following each other.\n");
        } else {
            if (following) {
                System.out.format("You are following. ");
            } else {
                System.out.format("You are not following. ");
            }
            if (followedBy) {
                System.out.format("Is following you. ");
            } else {
                System.out.format("Is not following you. ");
            }
            System.out.format("\n");

        }
    }


    private void listAddAccount(String id, String searchString) {
        if (jsonArrayFollowing.size() == 0) {
            following();
        }
        JsonArray foundJsonArray = new JsonArray();
        for (JsonElement accountJsonElement : jsonArrayFollowing) {
            String acct = Utils.getProperty(accountJsonElement, "acct");
            String username = Utils.getProperty(accountJsonElement, "username");
            if (Utils.contains(acct, searchString) || Utils.contains(username, searchString)) {
                foundJsonArray.add(accountJsonElement);
            }
        }
        System.out.format("\n%d accounts contain \"%s\" and added to list ID %s.\n", foundJsonArray.size(), searchString, id);
        JsonArray arrayOfIds = new JsonArray();
        for (JsonElement accountJsonElement : foundJsonArray) {
            System.out.format("%s %s\n", Utils.getProperty(accountJsonElement, "acct"), Utils.getProperty(accountJsonElement, "username"));
            arrayOfIds.add(Utils.getProperty(accountJsonElement, "id"));
        }
        String urlString = String.format("https://%s/api/v1/lists/%s/accounts", Utils.getProperty(settingsJsonObject, "instance"), id);
        JsonObject params = new JsonObject();
        params.add("account_ids", arrayOfIds);
        JsonElement jsonElement = postAsJson(Utils.getUrl(urlString), params.toString());
        //      System.out.format("RESPONSE: %s\n", jsonElement.toString());
    }

    private void following() {
        jsonArrayFollowing = new JsonArray();
        JsonElement jsonElementMe = whoami();
        System.out.format("Gathering accounts %s is following.\n", Utils.getProperty(jsonElementMe, "acct"));
        String id = Utils.getProperty(jsonElementMe, "id");
        String urlString = String.format("https://%s/api/v1/accounts/%s/following?limit=40", Utils.getProperty(settingsJsonObject, "instance"), id);
        URL url = Utils.getUrl(urlString);
        while (url != null) {
            HttpsURLConnection urlConnection;
            try {
                urlConnection = (HttpsURLConnection) url.openConnection();
                String authorization = String.format("Bearer %s", settingsJsonObject.get("access_token").getAsString());
                urlConnection.setRequestProperty("Authorization", authorization);
                String linkHeader = urlConnection.getHeaderField("link");
                InputStream is = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                JsonArray jsonArray = gson.fromJson(isr, JsonArray.class);
                jsonArrayFollowing.addAll(jsonArray);
                System.out.format("Gathered %d accounts. Total so far %d.\n", jsonArray.size(), jsonArrayFollowing.size());
                url = null;
                if (Utils.isNotBlank(linkHeader)) {
                    //      System.out.format("Link header: %s\n", linkHeader);
                    Pattern pattern = Pattern.compile("<([^>]+)>;\\s+rel=\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(linkHeader);
                    while (matcher.find()) {
                        urlString = matcher.group(1);
                        String rel = matcher.group(2);
                        //     System.out.format("URL: %s REL: %s\n", urlString, rel);
                        if ("next".equals(rel)) {
                            url = Utils.getUrl(urlString);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                url = null;
            }
        }

        ArrayList<Account> arrayListFollowing = new ArrayList<>();
        for (JsonElement jsonElement : jsonArrayFollowing) {
            arrayListFollowing.add(new Account(Utils.getProperty(jsonElement, "id"), Utils.getProperty(jsonElement, "acct"), Utils.getProperty(jsonElement, "display_name"), Utils.getProperty(jsonElement, "url")));
        }
        Collections.sort(arrayListFollowing);
        for (Account account : arrayListFollowing) {
            System.out.format("%s %s\n", green(account.getDisplayNameAndAccount()), account.getUrl());
        }
        System.out.format("\nFollowing %d accounts.\n", jsonArrayFollowing.size());
    }

    private JsonElement listCreate(String title) {
        String urlString = String.format("https://%s/api/v1/lists", Utils.getProperty(settingsJsonObject, "instance"));
        JsonObject params = new JsonObject();
        params.addProperty("title", title);
        return postAsJson(Utils.getUrl(urlString), params.toString());
    }

    private void updateQuantitySettings(int quantity) {
        if (quantity > 1) {
            settingsJsonObject.addProperty("quantity", quantity);
            JsonArray settingsJsonArray = getSettingsJsonArray();
            for (JsonElement jsonElement : settingsJsonArray) {
                if (Utils.getProperty(jsonElement, "id").equals(Utils.getProperty(settingsJsonObject, "id"))) {
                    jsonElement.getAsJsonObject().addProperty("quantity", quantity);
                }
            }
            String pretty = gson.toJson(settingsJsonArray);
            Utils.write(getSettingsFileName(), pretty);
            System.out.format("Quantity now set to %d and settings saved for instance: %s\n",
                    quantity, Utils.getProperty(settingsJsonObject, "instance"));
        } else {
            System.out.format("Quantity must be greater than zero. %d is not.", quantity);
        }
    }

    private void updateAudioFileNameSettings(String audioFileName) {
        File file = new File(audioFileName);
        if (!"none".equalsIgnoreCase(audioFileName) && !file.exists()) {
            System.out.format("Audio file %s not found.\n", audioFileName);
            return;
        }
        settingsJsonObject.addProperty("audioFileName", audioFileName);
        JsonArray settingsJsonArray = getSettingsJsonArray();
        for (JsonElement jsonElement : settingsJsonArray) {
            if (Utils.getProperty(jsonElement, "id").equals(Utils.getProperty(settingsJsonObject, "id"))) {
                jsonElement.getAsJsonObject().addProperty("audioFileName", audioFileName);
            }
        }
        String pretty = gson.toJson(settingsJsonArray);
        Utils.write(getSettingsFileName(), pretty);
        System.out.format("Audio file name now set to %s and settings saved for instance: %s\n",
                audioFileName, Utils.getProperty(settingsJsonObject, "instance"));
        playAudio();
    }

    private JsonObject chooseInstance(JsonArray settingsJsonArray) {
        if (settingsJsonArray.size() == 1) {
            return settingsJsonArray.get(0).getAsJsonObject();
        }
        JsonObject settingsJsonObject = null;
        while (settingsJsonObject == null) {
            System.out.format("Select one of these %d instances to use.\n", settingsJsonArray.size());
            int i = 0;
            for (JsonElement jsonElement : settingsJsonArray) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                String millisecondsString = Utils.getProperty(jsonObject, "milliseconds");
                long milliseconds = Utils.getLong(millisecondsString);
                System.out.format("%d Instance: %s added: %s\n", i++, jsonObject.get("instance"), new Date(milliseconds));
            }
            String answer = ask("");
            if (answer == null) {
                System.exit(0);
            }
            if (Utils.isNumeric(answer)) {
                int index = Utils.getInt(answer);
                if (index >= settingsJsonArray.size() || settingsJsonArray.size() == 0) {
                    System.out.format("Instance number %d not found.\n", index);
                } else {
                    settingsJsonObject = settingsJsonArray.get(index).getAsJsonObject();
                    closeWebSockets();
                    clearGlobalVariables();
                }
            } else {
                System.out.format("Choose the index for the instance you want to use. Must be between 0 and %d.\n", settingsJsonArray.size() - 1);
            }
        }
        return settingsJsonObject;
    }

    private void deleteInstance(JsonArray settingsJsonArray) {
        System.out.format("Select one of these %d instances to delete.\n", settingsJsonArray.size());
        int i = 0;
        for (JsonElement jsonElement : settingsJsonArray) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            System.out.format("%d Instance: %s added: %s\n", i++, jsonObject.get("instance"), new Date(Utils.getLong(Utils.getProperty(jsonObject, "milliseconds"))));
        }
        String answer = ask("");
        if (Utils.isNotBlank(answer)) {
            int index = Utils.getInt(answer);
            if (index > settingsJsonArray.size()) {
                System.out.format("Instance number %d not found.\n", index);
            } else {
                settingsJsonArray.remove(index);
                String pretty = gson.toJson(settingsJsonArray);
                Utils.write(getSettingsFileName(), pretty);
            }
        } else {
            System.out.println("Account not deleted.");
        }
    }

    private void lists() {
        String urlString = String.format("https://%s/api/v1/lists", Utils.getProperty(settingsJsonObject, "instance"));
        JsonArray jsonArray = getJsonArray(urlString);
        for (JsonElement jsonElement : jsonArray) {
            logger.info(gson.toJson(jsonElement));
            System.out.format("%s %s\n", cyan(Utils.getProperty(jsonElement, "id")), Utils.getProperty(jsonElement, "title"));
        }
    }

    private JsonArray listAccounts(String listId) {
        String urlString = String.format("https://%s/api/v1/lists/%s/accounts", Utils.getProperty(settingsJsonObject, "instance"), listId);
        JsonArray jsonArray = getJsonArray(urlString);
        if (jsonArray == null) {
            System.out.format("List id %s not found.\n", listId);
        } else {
            int i = 0;
            for (JsonElement jsonElement : jsonArray) {
                logger.info(gson.toJson(jsonElement));
                System.out.format("%d %s %s %s\n", i++, green(Utils.getProperty(jsonElement, "acct")), Utils.getProperty(jsonElement, "display_name"), Utils.getProperty(jsonElement, "url"));
            }
        }
        return jsonArray;
    }

    // // TODO: 5/26/19 Reply from noteifications is picking up the wrong ID.
    private void postStatus(String text, String inReplyToId, String visibility) {
        String urlString = String.format("https://%s/api/v1/statuses", Utils.getProperty(settingsJsonObject, "instance"));
        JsonObject params = new JsonObject();
        params.addProperty("status", text);
        params.addProperty("visibility", visibility);
        if (!mediaArrayList.isEmpty()) {
            JsonArray jsonArray = new JsonArray();
            for (JsonElement jsonElement : mediaArrayList) {
                jsonArray.add(Utils.getProperty(jsonElement, "id"));
            }
            params.add("media_ids", jsonArray);
        }
        if (Utils.isNotBlank(inReplyToId)) {
            params.addProperty("in_reply_to_id", inReplyToId);
        }
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        System.out.format("Status posted: %s\n", jsonObject.get("url").getAsString());
        mediaArrayList.clear();
    }

    private void createApp() {
        String prompt = "Type your instance name and press ENTER. For example: pleroma.site";
        String instance = ask(prompt);
        while (Utils.isBlank(instance)) {
            System.out.format("Instance can not be blank.\n");
            instance = ask(prompt);
        }
        JsonObject params = new JsonObject();
        params.addProperty("client_name", "Jediverse CLI");
        params.addProperty("redirect_uris", "urn:ietf:wg:oauth:2.0:oob");
        params.addProperty("scopes", "read write follow push");
        params.addProperty("website", "https://jediverse.com");
        String urlString = String.format("https://%s/api/v1/apps", instance);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        if (!Utils.isJsonObject(jsonObject)) {
            System.out.format("Something went wrong while creating app on instance \"%s\". Try again.\n", instance);
            return;
        }
        //   System.out.format("%s\n", jsonObject.toString());
        System.out.format("Go to https://%s/oauth/authorize?scope=%s&response_type=code&redirect_uri=%s&client_id=%s\n",
                instance, Utils.urlEncodeComponent("write read follow push"), Utils.urlEncodeComponent(jsonObject.get("redirect_uri").getAsString()), jsonObject.get("client_id").getAsString());
        String token = ask("Paste the token and press ENTER.");
        if (token == null || token.trim().length() < 20) {
            System.out.format("Token \"%s\" doesn't look valid. Try again.\n", token);
            return;
        }
        urlString = String.format("https://%s/oauth/token", instance);
        params = new JsonObject();
        params.addProperty("client_id", jsonObject.get("client_id").getAsString());
        params.addProperty("client_secret", jsonObject.get("client_secret").getAsString());
        params.addProperty("grant_type", "authorization_code");
        params.addProperty("code", token);
        params.addProperty("redirect_uri", jsonObject.get("redirect_uri").getAsString());
        JsonObject outputJsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        jsonObject.addProperty("access_token", outputJsonObject.get("access_token").getAsString());
        jsonObject.addProperty("refresh_token", Utils.getProperty(outputJsonObject, "refresh_token"));
        jsonObject.addProperty("me", Utils.getProperty(outputJsonObject, "me"));
        jsonObject.addProperty("expires_in", Utils.getProperty(outputJsonObject, "expires_in"));
        jsonObject.addProperty("created_at", Utils.getProperty(outputJsonObject, "created_at"));
        jsonObject.addProperty("instance", instance);
        jsonObject.addProperty("milliseconds", System.currentTimeMillis());
        jsonObject.addProperty("quantity", DEFAULT_QUANTITY);
        jsonObject.addProperty("audioFileName", DEFAULT_AUDIO_FILE_NAME);
        JsonArray jsonArray = new JsonArray();
        if (new File(getSettingsFileName()).exists()) {
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(Utils.readFileToString(getSettingsFileName()));
            jsonArray = jsonElement.getAsJsonArray();
        }
        jsonArray.add(jsonObject);
        String pretty = gson.toJson(jsonArray);
        Utils.write(getSettingsFileName(), pretty);
        settingsJsonObject = jsonObject;
        System.out.format("Added. You are now ");
        JsonElement jsonElementWhoAmI = whoami();
        System.out.format("%s %s\n", Utils.getProperty(jsonElementWhoAmI, "username"), Utils.getProperty(jsonElementWhoAmI, "url"));
    }

    private String getSettingsFileName() {
        return String.format("%s%s.jediverse.json", System.getProperty("user.home"), File.separator);
    }

    private JsonArray getSettingsJsonArray() {
        if (new File(getSettingsFileName()).exists()) {
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(Utils.readFileToString(getSettingsFileName()));
            return jsonElement.getAsJsonArray();
        }
        return new JsonArray();
    }

    private JsonArray getSettings() {
        JsonArray jsonArray = null;
        String propertyFileName = getSettingsFileName();
        FileInputStream fis = null;
        File propertyFile = new File(propertyFileName);
        if (!propertyFile.exists()) {
            return null;
        }
        try {
            fis = new FileInputStream(propertyFile);
            InputStreamReader isr = new InputStreamReader(fis);
            jsonArray = gson.fromJson(isr, JsonArray.class);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.close(fis);
        }
        return jsonArray;
    }

    private String ask(String prompt) {
        String line;
        System.out.format("%s\n", prompt);
        while (console.hasNext()) {
            line = console.nextLine();
            System.out.println(line);
            return line;
        }
        return null;
    }

    private void accountSearch(String line) {
        String searchString = line.substring(15);
        String encodedQuery = Utils.urlEncodeComponent(searchString);
        String urlString = String.format("https://%s/api/v1/accounts/search?q=%s", Utils.getProperty(settingsJsonObject, "instance"), encodedQuery);
        System.out.format("Searching for account \"%s\". This may take some time.\n", line);
        jsonArrayAccounts = getJsonArray(urlString);
        int i = 0;
        for (JsonElement account : jsonArrayAccounts) {
            System.out.format("%d %s %s %s %s %s\n",
                    i++, Utils.getProperty(account, "acct"), Utils.getProperty(account, "username"), Utils.getProperty(account, "display_name"), Utils.getProperty(account, "url"), Jsoup.parse(Utils.getProperty(account, "note")).text());
        }
        if (jsonArrayAccounts.size() == 1) {
            System.out.format("Use account-follow 0 or account-unfollow 0.\n");
        } else {
            if (jsonArrayAccounts.size() > 1) {
                System.out.format("Use account-follow 0 through %d or account-unfollow 0 through %d.\n",
                        jsonArrayAccounts.size() - 1, jsonArrayAccounts.size() - 1);
            } else {
                System.out.format("No accounts found with \"%s\"\n.", searchString);
            }
        }
    }

    private void search(String line) {
        String searchString = line.substring(7);
        String encodedQuery = Utils.urlEncodeComponent(searchString);
        String urlString = String.format("https://%s/api/v2/search?q=%s",
                Utils.getProperty(settingsJsonObject, "instance"), encodedQuery);
        //  System.out.println(urlString);
        JsonElement jsonElement = getJsonElement(urlString);
        //     System.out.format("%s\n", jsonElement);
        if (jsonElement != null) {
            printJsonElements(jsonElement.getAsJsonObject().getAsJsonArray("statuses"), searchString);
        } else {
            System.out.format("Search failed for: %s.\n", searchString);
        }
    }

    private void timeline(String timeline, String extra) {
        String sinceId;
        String sinceIdFragment = "";
        if (jsonArrayAll.size() > 0) {
            JsonElement last = jsonArrayAll.get(jsonArrayAll.size() - 1);
            sinceId = Utils.getProperty(last, "id");
            sinceIdFragment = String.format("&since_id=%s", sinceId);
        }
        String urlString = String.format("https://%s/api/v1/timelines/%s?limit=%d%s%s",
                settingsJsonObject.get("instance").getAsString(), timeline, getQuantity(), extra, sinceIdFragment);
        JsonArray jsonArray = getJsonArray(urlString);
        //    System.out.format("%s items: %d\n%s\n", urlString, jsonArray.size(), jsonArray.toString());
        printJsonElements(jsonArray, null);
    }

    private synchronized void printJsonElement(JsonElement jsonElement, String searchString, String event) {
        jsonArrayAll.add(jsonElement);
        logger.info(gson.toJson(jsonElement));
        String symbol = Utils.SYMBOL_PENCIL;
        JsonElement reblogJe = jsonElement.getAsJsonObject().get("reblog");
        String reblogLabel = "";
        if (Utils.isJsonObject(reblogJe)) {
            symbol = Utils.SYMBOL_REPEAT;
            JsonElement reblogAccountJe = reblogJe.getAsJsonObject().get("account");
            String reblogAccount = Utils.getProperty(reblogAccountJe, "acct");
            String displayName = Utils.getProperty(reblogAccountJe, "display_name");
            if (Utils.isNotBlank(displayName)) {
                displayName = String.format("%s <%s>", displayName, reblogAccount);
            } else {
                displayName = reblogAccount;
            }
            reblogLabel = yellow(displayName);
        }
        JsonElement accountJe = jsonElement.getAsJsonObject().get("account");
        if (!Utils.isJsonObject(accountJe)) {
            return;
        }
        String acct = Utils.getProperty(accountJe, "acct");
        String displayName = Utils.getProperty(accountJe, "display_name");
        if (Utils.isNotBlank(displayName)) {
            displayName = String.format("%s <%s>", displayName, acct);
        } else {
            displayName = acct;
        }
        String createdAt = Utils.getProperty(jsonElement, "created_at");
        String content = Utils.getProperty(jsonElement, "content");
        String text = "";
        if (Utils.isNotBlank(content)) {
            text = Jsoup.parse(content).text();
        }
        if (Utils.isNotBlank(searchString)) {
            String searchStringHighlighted = reverseVideo(searchString);
            text = text.replaceAll(searchString, searchStringHighlighted);
        }
        String type = Utils.getProperty(jsonElement, "type");
        if ("favourite".equals(type)) {
            symbol = Utils.SYMBOL_HEART;
            if (Utils.isBlank(text)) {
                JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
                if (Utils.isJsonObject(statusJe)) {
                    content = Utils.getProperty(statusJe, "content");
                    if (Utils.isNotBlank(content)) {
                        text = Jsoup.parse(content).text();
                    }
                }
            }
        }
        if ("follow".equals(type) && Utils.isBlank(text)) {
            text = String.format("followed you. %s", Utils.getProperty(accountJe, "url"));
        }
        if ("reblog".equals(type) && Utils.isBlank(text)) {
            JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
            if (Utils.isJsonObject(statusJe)) {
                text = String.format("repeated your status %s.", Utils.getProperty(statusJe, "url"));
            }
        }
        String dateDisplay = Utils.getDateDisplay(Utils.toDate(createdAt));
        //    System.out.format("DEBUG: Reblog label \"%s\" event: \"%s\" Type \"%s\"\n", reblogLabel, event, type);
        System.out.format("%s %s%s %s %s %s", cyan(jsonArrayAll.size() - 1), symbol, reblogLabel, dateDisplay, green(displayName), text);
        JsonArray attachments = jsonElement.getAsJsonObject().getAsJsonArray("media_attachments");
        if (attachments != null) {
            for (JsonElement a : attachments) {
                System.out.format(" %s", Utils.SYMBOL_PICTURE_FRAME);
            }
        }
        System.out.format("\n");
    }

    private void printJsonElements(JsonArray jsonArray, String searchString) {
        int i = jsonArray.size() - 1;
        for (; i > -1; i--) {
            JsonElement jsonElement = jsonArray.get(i);
            printJsonElement(jsonElement, searchString, null);
        }
    }

    private String yellow(String s) {
        return String.format("%s%s%s%s", Utils.ANSI_BOLD, Utils.ANSI_YELLOW, s, Utils.ANSI_RESET);
    }

    private String green(String s) {
        return String.format("%s%s%s%s", Utils.ANSI_BOLD, Utils.ANSI_GREEN, s, Utils.ANSI_RESET);
    }

    private String cyan(int i) {
        return cyan(Integer.toString(i));
    }

    private String cyan(String s) {
        return String.format("%s%s%s%s", Utils.ANSI_BOLD, Utils.ANSI_CYAN, s, Utils.ANSI_RESET);
    }

    private String reverseVideo(String s) {
        return String.format("%s%s%s", Utils.ANSI_REVERSE_VIDEO, s, Utils.ANSI_RESET);
    }

    private void notifications(String extra) {
        String urlString = String.format("https://%s/api/v1/notifications?limit=%d%s", settingsJsonObject.get("instance").getAsString(), getQuantity(), extra);
        //  System.out.println(urlString);
        JsonArray jsonArray = getJsonArray(urlString);
        int i = jsonArray.size() - 1;
        for (; i > -1; i--) {
            JsonElement jsonElement = jsonArray.get(i);
            jsonArrayAll.add(jsonElement);
            logger.info(gson.toJson(jsonElement));
            String symbol = Utils.SYMBOL_PENCIL;
            String text = "";
            String type = Utils.getProperty(jsonElement, "type");
            String createdAt = Utils.getProperty(jsonElement, "created_at");
            String dateDisplay = Utils.getDateDisplay(Utils.toDate(createdAt));
            if ("favourite".equals(type)) {
                symbol = Utils.SYMBOL_HEART;
                JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
                text = Jsoup.parse(Utils.getProperty(statusJe, "content")).text();
            }
            if ("follow".equals(type)) {
                symbol = Utils.SYMBOL_MAILBOX;
            }
            if ("reblog".equals(type)) {
                symbol = Utils.SYMBOL_REPEAT;
                JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
                text = Jsoup.parse(Utils.getProperty(statusJe, "content")).text();
            }
            if ("mention".equals(type)) {
                symbol = Utils.SYMBOL_SPEAKER;
                JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
                text = Jsoup.parse(Utils.getProperty(statusJe, "content")).text();
            }
            JsonElement accountJe = jsonElement.getAsJsonObject().get("account");
            String acct = Utils.getProperty(accountJe, "acct");
            //   System.out.format("\n\n%s %s %s\n%s\n", symbol, acct, text, jsonElement);
            System.out.format("%s %s %s %s %s\n", cyan(jsonArrayAll.size() - 1), symbol, dateDisplay, green(acct), text);
        }
        System.out.format("%d items.\n", jsonArray.size());
    }

    private JsonArray getJsonArray(String urlString) {
        URL url = Utils.getUrl(urlString);
        HttpsURLConnection urlConnection;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            String authorization = String.format("Bearer %s", settingsJsonObject.get("access_token").getAsString());
            urlConnection.setRequestProperty("Authorization", authorization);
            InputStream is = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            return gson.fromJson(isr, JsonArray.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JsonElement getJsonElement(String urlString) {
        URL url = Utils.getUrl(urlString);
        HttpsURLConnection urlConnection;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            String authorization = String.format("Bearer %s", settingsJsonObject.get("access_token").getAsString());
            urlConnection.setRequestProperty("Authorization", authorization);
            InputStream is = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            return gson.fromJson(isr, JsonElement.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void favourite(String id) {
        String urlString = String.format("https://%s/api/v1/statuses/%s/favourite", settingsJsonObject.get("instance").getAsString(), id);
        //    System.out.println(urlString);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), null);
        System.out.format("Favourited: %s\n", Utils.getProperty(jsonObject, "url"));
    }

    private void unfavourite(String id) {
        String urlString = String.format("https://%s/api/v1/statuses/%s/unfavourite", settingsJsonObject.get("instance").getAsString(), id);
        System.out.println(urlString);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), null);
        System.out.format("Unfavorited: %s\n", Utils.getProperty(jsonObject, "url"));
    }

    //// TODO: 5/24/19 Handle 500 exception on repeated unfollow request.
    private JsonObject postAsJson(URL url, String json) {
        //    System.out.format("postAsJson URL: %s JSON: \n%s\n", url.toString(), json);
        HttpsURLConnection urlConnection;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        JsonObject jsonObject = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Cache-Control", "no-cache");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod("POST");
            if (settingsJsonObject != null) {
                String authorization = String.format("Bearer %s", Utils.getProperty(settingsJsonObject, "access_token"));
                urlConnection.setRequestProperty("Authorization", authorization);
                //         System.out.format("Setting authorization header: %s\n", authorization);
            }
            if (json != null) {
                urlConnection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-length", Integer.toString(json.length()));
                outputStream = urlConnection.getOutputStream();
                outputStream.write(json.getBytes());
                outputStream.flush();
                int responseCode = urlConnection.getResponseCode();
                System.out.format("Response code: %d\n", responseCode);
            }
            urlConnection.setInstanceFollowRedirects(true);
            inputStream = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream);
            jsonObject = gson.fromJson(isr, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.close(inputStream, outputStream);
        }
        return jsonObject;
    }

    private int deleteAsJson(URL url, String json) {
        HttpsURLConnection urlConnection;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        int responseCode = 0;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Cache-Control", "no-cache");
            urlConnection.setRequestProperty("Accept", "application/json");
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod("DELETE");
            if (settingsJsonObject != null) {
                String authorization = String.format("Bearer %s", Utils.getProperty(settingsJsonObject, "access_token"));
                urlConnection.setRequestProperty("Authorization", authorization);
            }
            if (json != null) {
                urlConnection.setRequestProperty("Content-type", "application/json; charset=UTF-8");
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-length", Integer.toString(json.length()));
                outputStream = urlConnection.getOutputStream();
                outputStream.write(json.getBytes());
                outputStream.flush();
                responseCode = urlConnection.getResponseCode();
                //         System.out.format("Response code: %d\n", responseCode);
            }
            urlConnection.setInstanceFollowRedirects(true);
            inputStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = br.readLine()) != null) {
                logger.info(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.close(inputStream, outputStream);
        }
        return responseCode;
    }

    private JsonElement whoami() {
        String urlString = String.format("https://%s/api/v1/accounts/verify_credentials", Utils.getProperty(settingsJsonObject, "instance"));
        return getJsonElement(urlString);
    }

    private void upload(String line) throws Exception {
        String urlString = String.format("https://%s/api/v1/media", Utils.getProperty(settingsJsonObject, "instance"));
        System.out.println(urlString);
        String fileName = line.substring(7);
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.format("File: \"%s\" does not exist.\n", fileName);
            return;
        }
        var client = HttpClient.newBuilder().build();
        Map<Object, Object> data = new LinkedHashMap<>();
        data.put("access_token", Utils.getProperty(settingsJsonObject, "access_token"));
        data.put("description", String.format("%s uploaded by %s CLI.", fileName, this.getClass().getSimpleName()));
        data.put("file", Paths.get(fileName));
        String boundary = new BigInteger(256, new Random()).toString();
        var request = HttpRequest.newBuilder()
                .header("Content-Type", "multipart/form-data;boundary=" + boundary)
                .POST(ofMimeMultipartData(data, boundary))
                .uri(URI.create(urlString))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        //   System.out.format("%s\n", response.body());
        JsonParser jsonParser = new JsonParser();
        int responseStatusCode = response.statusCode();
        if (responseStatusCode == 413) {
            System.out.format("File %s is too large. Size is %s.\n", fileName, Utils.humanReadableByteCount(file.length()));
            return;
        }
        JsonElement jsonElement = jsonParser.parse(response.body());
        mediaArrayList.add(jsonElement);
        System.out.format("File %s uploaded as ID %s. You have %d file(s) that will be attached to your next postStatus.\n",
                fileName, Utils.getProperty(jsonElement, "id"), mediaArrayList.size());
    }

    private void clearMediaArrayList() {
        if (mediaArrayList.isEmpty()) {
            System.out.format("There aren't any uploaded media files in the queue. ");
        } else {
            System.out.format("%d files removed from the media queue. ", mediaArrayList.size());
            mediaArrayList.clear();
        }
        System.out.println("Your next postStatus will not include any media attachments.");
    }

    private class WebSocketListener implements Listener {
        private String stream;
        private StringBuilder sb = new StringBuilder();
        private JsonParser jsonParser = new JsonParser();

        WebSocketListener(String stream) {
            this.stream = stream;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                sb.append(data);
                JsonElement messageJsonElement = jsonParser.parse(sb.toString());
                logger.info(gson.toJson(messageJsonElement));
                if (Utils.isJsonObject(messageJsonElement)) {
                    String event = Utils.getProperty(messageJsonElement, "event");
                    String payloadString = messageJsonElement.getAsJsonObject().get("payload").getAsString();
                    if ("notification".equals(event)) {
                        playAudio();
                        System.out.format("%s", Utils.SYMBOL_SPEAKER);
                    }
                    if (Utils.isNotBlank(payloadString)) {
                        JsonElement payloadJsonElement = jsonParser.parse(payloadString);
                        if (Utils.isJsonObject(payloadJsonElement)) {
                            printJsonElement(payloadJsonElement, null, event);
                        }
                    } else {
                        System.out.format("Payload is blank.\n");
                    }
                } else {
                    //   System.out.format("JSON element is null.\n");
                }
                sb = new StringBuilder();
            } else {
                sb.append(data);
            }
            return Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            System.out.format("onPing Message: %s\n", message);
            return Listener.super.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            System.out.format("onPong Message: %s\n", message);
            return Listener.super.onPong(webSocket, message);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.format("WebSocket opened for %s stream.\n", stream);
            Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.format("WebSocket closed. Status code: %d Reason: %s Stream: %s\n.", statusCode, reason, stream);
            webSocket.abort();
            webSocket = null;
            return Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.out.format("WebSocket error: %s.\n", error.getLocalizedMessage());
            error.printStackTrace();
            Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            System.out.format("Binary data received on WebSocket.\n");
            return Listener.super.onBinary(webSocket, data, last);
        }
    }

    private void about() {
        System.out.format("Jediverse is a command line interface for the Fediverse. Jediverse was created by https://pla.social/pla. YMMV ☮\n");
        System.out.format("Source %s\n", "https://github.com/pla1/Jediverse");
        System.out.format("Website https://jediverse.com/\n");
        System.out.format("JSON logging file file://%s\n", jsonLoggerFile.getAbsolutePath());
        System.out.format("Settings file file://%s\n", getSettingsFileName());
        System.out.format("User ");
        printWhoAmI();
        if (!webSockets.isEmpty()) {
            System.out.format("Streams ");
            for (String stream : streams) {
                System.out.format("%s ", stream);
            }
            System.out.format("\n");
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        long bytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.format("Memory used %s B\n", numberFormat.format(bytes));
        if (Utils.isUnix()) {
            long pid = ProcessHandle.current().pid();
            String[] commandParts = {"ps", "--pid", Long.toString(pid), "-o", "%cpu,%mem"};
            String output = Utils.run(commandParts);
            System.out.format("%s\n", output);
        }
    }

    public Logger getLogger() {
        Logger logger = Logger.getLogger("JediverseJsonLog");
        FileHandler fh;
        try {
            jsonLoggerFile = File.createTempFile("jediverse_json_log_", ".log");
            System.out.format("JSON log file: %s\n", jsonLoggerFile.getAbsolutePath());
            fh = new FileHandler(jsonLoggerFile.getAbsolutePath());
            logger.setUseParentHandlers(false);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
        logger.info(String.format("This file: %s", jsonLoggerFile.getAbsolutePath()));
        return logger;
    }

    private void blocks() {
        String urlString = String.format("https://%s/api/v1/blocks", Utils.getProperty(settingsJsonObject, "instance"));
        JsonArray jsonArray = getJsonArray(urlString);
        logger.info(jsonArray.toString());
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement jsonElement = jsonArray.get(i);
            String displayName = Utils.getProperty(jsonElement, "display_name");
            String acct = Utils.getProperty(jsonElement, "acct");
            if (Utils.isBlank(displayName)) {
                displayName = acct;
            } else {
                displayName = String.format("%s <%s> %s", displayName, acct, Utils.getProperty(jsonElement, "url"));
            }
            System.out.format("%s\n", displayName);
        }
        System.out.format("%d blocked accounts.\n", jsonArray.size());

    }
    private void uploadWithFileBrowser() throws Exception {
        final JFileChooser fc = new JFileChooser();
        int returnVal = fc.showOpenDialog(new Frame());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            upload(String.format("upload %s", file.getAbsolutePath()));
        } else {
            System.out.format("No files uploaded.\n");
        }
    }
}
