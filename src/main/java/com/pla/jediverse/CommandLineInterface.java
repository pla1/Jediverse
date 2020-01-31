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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notes on which type of console to use.
 * java.io.BufferedReader thread safe, fast 👍 throws Exceptions 👎
 * java.util.Scanner not threadsafe 👎
 * java.io.Console doesn't work in IDE, doesn't recognize Ctrl-d. 👎
 */


public class CommandLineInterface {

    private static BufferedReader console;
    private static boolean debug = false;
    // private static Scanner console;
    //  private static Console console;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final int DEFAULT_QUANTITY = 20;
    private final String DEFAULT_BROWSER_COMMAND = "/usr/bin/xdg-open";
    private final String DEFAULT_AUDIO_FILE_NAME_NOTIFICATIONS = "ding.wav";
    private final String DEFAULT_AUDIO_FILE_NAME_FAILS = "fail.wav";
    private final ArrayList<String> streams = new ArrayList<>();
    private JsonObject settingsJsonObject;
    private JsonArray jsonArrayAll = new JsonArray();
    private Logger logger;
    private ArrayList<WebSocket> webSockets = new ArrayList<>();
    private JsonArray jsonArrayFollowing = new JsonArray();
    private JsonArray jsonArrayFollowers = new JsonArray();
    private ArrayList<JsonElement> mediaArrayList = new ArrayList<>();
    private JsonArray jsonArrayAccounts = new JsonArray();
    private JsonArray jsonArrayAccountSearchResults = new JsonArray();
    private File jsonLoggerFile;
    private long pongTime = System.currentTimeMillis();

    private CommandLineInterface() {
        setup();
        try {
            mainRoutine();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length == 1 && Literals.debug.name().equalsIgnoreCase(args[0])) {
            debug = true;
        }
        new CommandLineInterface();
        System.exit(0);
    }

    private static HttpRequest.BodyPublisher ofMimeMultipartData(Map<Object, Object> data, String boundary) throws IOException {
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
        return HttpRequest.BodyPublishers.ofByteArrays(byteArrays);
    }

    private void clearGlobalVariables() {
        jsonArrayAccounts = new JsonArray();
        mediaArrayList = new ArrayList<>();
        jsonArrayFollowing = new JsonArray();
        jsonArrayFollowers = new JsonArray();
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
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, Literals.ok.name());
                quantity++;
            }
        }
        webSockets.clear();
        streams.clear();
        System.out.format("Closed %d WebSockets.\n", quantity);
    }

    private void playAudioNotification() {
        String audioFileName = getAudioFileNameNotifications();
        if (Literals.none.name().equalsIgnoreCase(audioFileName)) {
            return;
        }
        playAudio(audioFileName);
    }

    private void playAudioFail() {
        String audioFileName = getAudioFileNameFails();
        if (Literals.none.name().equalsIgnoreCase(audioFileName)) {
            return;
        }
        playAudio(audioFileName);
    }

    private void playAudio(String audioFileName) {
        AudioInputStream audioInputStream;
        Clip clip;
        try {
            File file = new File(audioFileName).getAbsoluteFile();
            audioInputStream = AudioSystem.getAudioInputStream(file);
            clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();

        } catch (Exception e) {
            //   e.printStackTrace();
        }
        //finally {
        //      Utils.close(clip, audioInputStream);
        //   }
    }

    private void setup() {
        console = new BufferedReader(new InputStreamReader(System.in));
        //   console = new Scanner(System.in);
        //    console = System.console();
        //    console.readLine("This is a test");
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
        System.out.format("Using instance: %s\n", settingsJsonObject.get(Literals.instance.name()));
        System.out.format("Type 'help' and press 'Enter' for a list of things you can do.\n");
    }

    private String getAudioFileNameNotifications() {
        String audioFileName = Utils.getProperty(settingsJsonObject, Literals.audioFileNotifications.name());
        if (Utils.isBlank(audioFileName)) {
            return DEFAULT_AUDIO_FILE_NAME_NOTIFICATIONS;
        } else {
            return audioFileName;
        }
    }

    private String getAudioFileNameFails() {
        String audioFileName = Utils.getProperty(settingsJsonObject, Literals.audioFileFails.name());
        if (Utils.isBlank(audioFileName)) {
            return DEFAULT_AUDIO_FILE_NAME_FAILS;
        } else {
            return audioFileName;
        }
    }

    private int getQuantity() {
        int quantity = Utils.getInt(Utils.getProperty(settingsJsonObject, Literals.quantity.name()));
        if (quantity == 0) {
            quantity = DEFAULT_QUANTITY;
        }
        return quantity;
    }

    private String getBrowserCommand() {
        String browserCommand = Utils.getProperty(settingsJsonObject, Literals.browserCommand.name());
        if (Utils.isBlank(browserCommand)) {
            browserCommand = DEFAULT_BROWSER_COMMAND;
        }
        return browserCommand;
    }

    private void mainRoutine() {
        String line;
        boolean done = false;
        boolean firstTime = true;
        while (!done) {
            if (firstTime) {
                firstTime = false;
                String startupCommand = Utils.getProperty(settingsJsonObject, Literals.onstart.name());
                if (Utils.isNotBlank(startupCommand)) {
                    line = startupCommand;
                } else {
                    line = readConsole();
                }
            } else {
                line = readConsole();
            }
            if (line == null) {
                System.out.format("Line is null.\n");
                done = true;
                continue;
            }
            if (line.trim().length() == 0) {
                System.out.format("Line is blank. %s\n", line);
                continue;
            }
            if (System.currentTimeMillis() - pongTime > Utils.MILLISECONDS_ONE_MINUTE) {
                System.out.format("PONG not received since %s. Exiting.\n", new Date(pongTime));
                System.exit(-1);
            }
            String[] words = line.split("\\s+");
            if (Literals.search.name().equals(words[0]) && words.length > 1) {
                search(line);
            }
            if (Literals.clear.name().equals(words[0])) {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
            if ("account-search".equals(words[0]) && words.length > 1) {
                accountSearch(line);
            }
            if ("instance-info".equals(words[0]) && words.length > 1) {
                instanceInfo(line, false);
            }
            if ("instance-info-full".equals(words[0]) && words.length > 1) {
                instanceInfo(line, true);
            }
            if (words.length == 2 && "account-follow".equals(words[0]) && Utils.isNumeric(words[1])) {
                accountFollowUnfollow(Utils.getInt(words[1]), true);
            }
            if (words.length == 2 && "account-unfollow".equals(words[0]) && Utils.isNumeric(words[1])) {
                accountFollowUnfollow(Utils.getInt(words[1]), false);
            }
            if (line.startsWith(Literals.upload.name()) && words.length > 1) {
                try {
                    upload(line);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (line.startsWith("upload-browse")) {
                try {
                    uploadWithFileBrowser();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (Literals.about.name().equals(words[0])) {
                about();
            }
            if (Literals.blocks.name().equals(words[0])) {
                blocks();
            }
            if (Literals.properties.name().equals(words[0])) {
                Utils.printProperties();
            }
            if (Literals.gc.name().equals(words[0])) {
                System.out.format("\nGarbage collection suggested\n\nBefore:\n");
                Utils.printResourceUtilization();
                System.gc();
                System.out.format("\nAfter\n");
                Utils.printResourceUtilization();
            }
            if ("upload-clear".equals(words[0])) {
                clearMediaArrayList();
            }
            if (Literals.following.name().equals(line)) {
                followingFollowers(line);
            }
            if (Literals.followers.name().equals(line)) {
                followingFollowers(line);
            }
            if (Literals.lists.name().equals(line)) {
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
                System.out.format("List id: %s title: %s created.\n", Utils.getProperty(jsonElement, Literals.id.name()), Utils.getProperty(jsonElement, Literals.title.name()));
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
            if ("list-timeline".equals(words[0]) && words.length == 2 && Utils.isNumeric(words[1])) {
                timeline(String.format("list/%s", words[1]), "");
            }
            if ("fed".equals(line)) {
                timeline("public", "&local=false");
            }
            if (Literals.quantity.name().equals(words[0]) && words.length == 2 && Utils.isNumeric(words[1])) {
                int qty = Utils.getInt(words[1]);
                updateQuantitySettings(qty);
            }
            if ("browser-command".equals(words[0]) && words.length == 2) {
                updateBrowserCommandSettings(words[1]);
            }
            if (Literals.onstart.name().equals(words[0])) {
                updateOnstartCommandSettings(line);

            }
            if ("audio-notifications".equals(words[0]) && words.length == 2) {
                updateAudioFileNameSetting(words[1], Literals.audioFileNotifications);
            }
            if ("audio-fails".equals(words[0]) && words.length == 2) {
                updateAudioFileNameSetting(words[1], Literals.audioFileFails);
            }
            if ("accounts-search".equals(words[0]) && words.length == 2) {
                accountsSearch(words[1]);
            }
            if (Literals.aa.name().equals(line)) {
                createApp();
            }
            if (Literals.sa.name().equals(line)) {
                settingsJsonObject = chooseInstance(getSettingsJsonArray());
                System.out.format("Using instance: %s\n", settingsJsonObject.get(Literals.instance.name()));
            }
            if (Literals.da.name().equals(line)) {
                deleteInstance(getSettingsJsonArray());
                settingsJsonObject = chooseInstance(getSettingsJsonArray());
            }
            if (Literals.local.name().equals(line)) {
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
                    stream = Literals.direct.name();
                }
                if (words[0].equals("stream-list") && words.length == 2 && Utils.isNumeric(words[1])) {
                    stream = String.format("list&list=%s", words[1]);
                }
                if (words[0].equals("stream-hashtag") && words.length == 2) {
                    stream = String.format("hashtag&tag=%s", Utils.urlEncodeComponent(words[1]));
                }
                if (Utils.isNotBlank(stream)) {
                    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                    String urlString = String.format("wss://%s/api/v1/streaming/?stream=%s&access_token=%s",
                            Utils.getProperty(settingsJsonObject, Literals.instance.name()), stream, Utils.getProperty(settingsJsonObject, Literals.access_token.name()));
                    WebSocketListener webSocketListener = new WebSocketListener(stream);
                    WebSocket webSocket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(URI.create(urlString), webSocketListener).join();
                    webSockets.add(webSocket);
                    streams.add(stream);
                }
            }
            if (Literals.stop.name().equals(line)) {
                closeWebSockets();
            }
            if (Literals.timeline.name().equals(line) || Literals.tl.name().equals(line)) {
                timeline(Literals.home.name(), "");
            }
            if (Literals.notifications.name().equals(line) || Literals.note.name().equals(line)) {
                notifications();
            }
            if (words.length > 1 && "post-direct".equals(words[0])) {
                String text = line.substring(12);
                postStatus(text, null, Literals.direct.name());
            }
            if (words.length > 1 && Literals.post.name().equals(words[0])) {
                String text = line.substring(5);
                postStatus(text, null, "public");
            }
            if (words.length > 1 && "post-followers".equals(words[0])) {
                String text = line.substring(15);
                postStatus(text, null, "private");
            }
            if (words.length > 1 && "post-unlisted".equals(words[0])) {
                String text = line.substring(5);
                postStatus(text, null, Literals.unlisted.name());
            }
            if (words.length > 2 && (Literals.rep.name().equals(words[0]) || Literals.reply.name().equals(words[0]))) {
                if (Utils.isNumeric(words[1])) {
                    int index = Utils.getInt(words[1]);
                    if (index > jsonArrayAll.size()) {
                        System.out.format("Item at index: %d not found.\n", index);
                    } else {
                        JsonElement jsonElement = jsonArrayAll.get(index);
                        String type = Utils.getProperty(jsonElement, Literals.type.name());
                        if (Literals.favourite.name().equals(type) || Literals.reblog.name().equals(type) || Literals.follow.name().equals(type)) {
                            System.out.format("You can't reply to a %s.\n", type);
                        } else {
                            String text = line.substring(line.indexOf(words[1]) + words[1].length());
                            if (Literals.mention.name().equals(type)) {
                                JsonElement statusJe = jsonElement.getAsJsonObject().get(Literals.status.name());
                                String visibility = Utils.getProperty(statusJe, Literals.visibility.name());
                                postStatus(text, Utils.getProperty(statusJe, Literals.id.name()), visibility);
                            } else {
                                String visibility = Utils.getProperty(jsonElement, Literals.visibility.name());
                                postStatus(text, Utils.getProperty(jsonElement, Literals.id.name()), visibility);
                            }
                        }
                    }
                }

            }
            if (words.length == 2 && Literals.fav.name().equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                String id = Utils.getProperty(jsonElement, Literals.id.name());
                //     System.out.format("Fav this: %s\n", jsonElement.toString());
                if (Literals.mention.name().equals(Utils.getProperty(jsonElement, Literals.type.name()))) {
                    JsonElement statusJe = jsonElement.getAsJsonObject().get(Literals.status.name());
                    id = Utils.getProperty(statusJe, Literals.id.name());
                }
                favourite(id);
            }
            if (words.length == 2 && Literals.url.name().equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                System.out.format("%d %s\n", index, Utils.getProperty(jsonElement, Literals.url.name()));
            }
            if (words.length == 2 && Literals.go.name().equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                // System.out.format("\n\nDEBUG: %s\n\n", jsonElement.toString());
                String urlString = Utils.getProperty(jsonElement, Literals.url.name());
                if (Utils.isBlank(urlString)) {
                    JsonElement status = jsonElement.getAsJsonObject().get(Literals.status.name());
                    urlString = Utils.getProperty(status, Literals.url.name());
                }

                // TODO testing notice ID on current instance
                urlString = String.format("https://%s/notice/%s", Utils.getProperty(settingsJsonObject, Literals.instance.name()), Utils.getProperty(jsonElement, Literals.id.name()));
                if (Utils.isNotBlank(urlString)) {
                    String browserCommand = Utils.getProperty(settingsJsonObject, Literals.browserCommand.name());
                    if (Utils.isBlank(browserCommand)) {
                        browserCommand = DEFAULT_BROWSER_COMMAND;
                        updateBrowserCommandSettings(browserCommand);
                    }
                    Utils.run(new String[]{browserCommand, urlString});
                }
            }
            if (words.length == 2 && Literals.context.name().equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                System.out.format("\n\nStart of context for ");
                printJsonElement(jsonElement, "", "");
                context(jsonElement);
            }
            if (words.length == 2 && Literals.unfav.name().equals(words[0])) {
                int index = Utils.getInt(words[1]);
                if (index > jsonArrayAll.size()) {
                    System.out.format("Item %d not found.", index);
                    continue;
                }
                JsonElement jsonElement = jsonArrayAll.get(index);
                unfavourite(Utils.getProperty(jsonElement, Literals.id.name()));
            }
            if (Literals.quit.name().equals(line) || Literals.exit.name().equals(line)) {
                done = true;

            }
            if (Literals.help.name().equals(line) || "?".equals(line)) {
                System.out.println(Utils.readFileToString("help.txt"));
            }
            if (Literals.whoami.name().equals(line)) {
                printWhoAmI();
            }
        }
        System.out.format("Bye bye\n");
        Utils.close(console);
        System.exit(0);
    }

    private void printWhoAmI() {
        JsonElement jsonElement = whoami();
        System.out.format("%s %s %s\n\t%s\n",
                Utils.getProperty(jsonElement, Literals.display_name.name()),
                Utils.getProperty(jsonElement, Literals.username.name()),
                Utils.getProperty(jsonElement, Literals.url.name()),
                Jsoup.parse(Utils.getProperty(jsonElement, Literals.note.name())).text());
    }

    private void listDeleteAccount(String listId, String accountIndex) {
        JsonArray accountsJsonArray = listAccounts(listId);
        JsonElement accountJsonElement = accountsJsonArray.get(Utils.getInt(accountIndex));
        if (accountJsonElement == null) {
            System.out.format("Account index %s not found for list ID %s.\n", accountIndex, listId);
            return;
        }
        JsonArray arrayOfIds = new JsonArray();
        arrayOfIds.add(Utils.getProperty(accountJsonElement, Literals.id.name()));
        JsonObject params = new JsonObject();
        params.add(Literals.account_ids.name(), arrayOfIds);
        String urlString = String.format("https://%s/api/v1/lists/%s/accounts", Utils.getProperty(settingsJsonObject, Literals.instance.name()), listId);
        int statusCode = deleteAsJson(Utils.getUrl(urlString), params.toString());
        System.out.format("Status code %d from removing account ID %s index %s from list id %s.\n",
                statusCode, Utils.getProperty(accountJsonElement, Literals.id.name()), accountIndex, listId);
        listAccounts(listId);
    }

    private void listDelete(String id) {
        String urlString = String.format("https://%s/api/v1/lists/%s", Utils.getProperty(settingsJsonObject, Literals.instance.name()), id);
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
                        i++,
                        Utils.getProperty(account, Literals.acct.name()),
                        Utils.getProperty(account, Literals.username.name()),
                        Utils.getProperty(account, Literals.display_name.name()),
                        Utils.getProperty(account, Literals.url.name()),
                        Jsoup.parse(Utils.getProperty(account, Literals.note.name())).text());
            }
            return;
        }
        JsonElement accountJsonElement = jsonArrayAccounts.get(index);
        System.out.format("%s %s %s\n",
                Utils.getProperty(accountJsonElement, Literals.id.name()), Utils.getProperty(accountJsonElement, Literals.acct.name()), Utils.getProperty(accountJsonElement, Literals.username.name()));
        String verb = Literals.unfollow.name();
        if (follow) {
            verb = Literals.follow.name();
        }
        String urlString = String.format("https://%s/api/v1/accounts/%s/%s", Utils.getProperty(settingsJsonObject, Literals.instance.name()), Utils.getProperty(accountJsonElement, Literals.id.name()), verb);
        JsonObject jsonObjectResult = postAsJson(Utils.getUrl(urlString), null);
        boolean following = Utils.isYes(Utils.getProperty(jsonObjectResult, Literals.following.name()));
        boolean followedBy = Utils.isYes(Utils.getProperty(jsonObjectResult, Literals.followed_by.name()));
        System.out.format("%s %s %s\n", Utils.getProperty(accountJsonElement, Literals.acct.name()), Utils.getProperty(accountJsonElement, Literals.username.name()), Utils.getProperty(accountJsonElement, Literals.display_name.name()));
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
            followingFollowers(Literals.following.name());
        }
        JsonArray foundJsonArray = new JsonArray();
        for (JsonElement accountJsonElement : jsonArrayFollowing) {
            String acct = Utils.getProperty(accountJsonElement, Literals.acct.name());
            String username = Utils.getProperty(accountJsonElement, Literals.username.name());
            if (Utils.contains(acct, searchString) || Utils.contains(username, searchString)) {
                foundJsonArray.add(accountJsonElement);
            }
        }
        System.out.format("\n%d accounts contain \"%s\" and added to list ID %s.\n", foundJsonArray.size(), searchString, id);
        JsonArray arrayOfIds = new JsonArray();
        for (JsonElement accountJsonElement : foundJsonArray) {
            System.out.format("%s %s\n", Utils.getProperty(accountJsonElement, Literals.acct.name()), Utils.getProperty(accountJsonElement, Literals.username.name()));
            arrayOfIds.add(Utils.getProperty(accountJsonElement, Literals.id.name()));
        }
        String urlString = String.format("https://%s/api/v1/lists/%s/accounts", Utils.getProperty(settingsJsonObject, Literals.instance.name()), id);
        JsonObject params = new JsonObject();
        params.add(Literals.account_ids.name(), arrayOfIds);
        JsonElement jsonElement = postAsJson(Utils.getUrl(urlString), params.toString());
        //      System.out.format("RESPONSE: %s\n", jsonElement.toString());
    }

    // TODO refactor this so it could be re-used for search(). Search doesn't handle multiple pages currently.
    private void followingFollowers(String action) {

        JsonElement jsonElementMe = whoami();
        if (Literals.following.name().equals(action)) {
            jsonArrayFollowing = new JsonArray();
            System.out.format("Gathering accounts that %s follows.\n", Utils.getProperty(jsonElementMe, Literals.acct.name()));
        }
        if (Literals.followers.name().equals(action)) {
            jsonArrayFollowers = new JsonArray();
            System.out.format("Gathering accounts that are following %s.\n", Utils.getProperty(jsonElementMe, Literals.acct.name()));
        }
        String id = Utils.getProperty(jsonElementMe, Literals.id.name());
        String urlString = String.format("https://%s/api/v1/accounts/%s/%s?limit=40", Utils.getProperty(settingsJsonObject, Literals.instance.name()), id, action);
        URL url = Utils.getUrl(urlString);
        while (url != null) {
            HttpsURLConnection urlConnection;
            try {
                urlConnection = (HttpsURLConnection) url.openConnection();
                String authorization = String.format("Bearer %s", settingsJsonObject.get(Literals.access_token.name()).getAsString());
                urlConnection.setRequestProperty("Authorization", authorization);
                String linkHeader = urlConnection.getHeaderField("link");
                InputStream is = urlConnection.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                JsonArray jsonArray = gson.fromJson(isr, JsonArray.class);
                if (jsonArray.size() > 0) {
                    if (Literals.following.name().equals(action)) {
                        jsonArrayFollowing.addAll(jsonArray);
                        System.out.format("Gathered %d accounts. Total so far %d.\n", jsonArray.size(), jsonArrayFollowing.size());
                    }
                    if (Literals.followers.name().equals(action)) {
                        jsonArrayFollowers.addAll(jsonArray);
                        System.out.format("Gathered %d accounts. Total so far %d.\n", jsonArray.size(), jsonArrayFollowers.size());
                    }
                }
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

        ArrayList<Account> arrayListAccounts = new ArrayList<>();
        if (Literals.following.name().equals(action)) {
            for (JsonElement jsonElement : jsonArrayFollowing) {
                arrayListAccounts.add(transfer(jsonElement));
            }
        }
        if (Literals.followers.name().equals(action)) {
            for (JsonElement jsonElement : jsonArrayFollowers) {
                arrayListAccounts.add(transfer(jsonElement));
            }
        }
        Collections.sort(arrayListAccounts);
        for (Account account : arrayListAccounts) {
            System.out.format("%s %s\n", green(account.getDisplayNameAndAccount()), account.getUrl());
        }
        if (Literals.following.name().equals(action)) {
            System.out.format("\nFollowing %d accounts.\n", jsonArrayFollowing.size());
        }
        if (Literals.followers.name().equals(action)) {
            System.out.format("\n%d followers.\n", jsonArrayFollowers.size());
        }
    }

    private Account transfer(JsonElement jsonElement) {
        return new Account(Utils.getProperty(jsonElement, Literals.id.name()),
                Utils.getProperty(jsonElement, Literals.acct.name()),
                Utils.getProperty(jsonElement, Literals.display_name.name()),
                Utils.getProperty(jsonElement, Literals.url.name()));
    }

    private JsonElement listCreate(String title) {
        String urlString = String.format("https://%s/api/v1/lists", Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        JsonObject params = new JsonObject();
        params.addProperty(Literals.title.name(), title);
        return postAsJson(Utils.getUrl(urlString), params.toString());
    }

    private void updateQuantitySettings(int quantity) {
        if (quantity > 1) {
            settingsJsonObject.addProperty(Literals.quantity.name(), quantity);
            JsonArray settingsJsonArray = getSettingsJsonArray();
            for (JsonElement jsonElement : settingsJsonArray) {
                if (Utils.getProperty(jsonElement, Literals.id.name()).equals(Utils.getProperty(settingsJsonObject, Literals.id.name()))) {
                    jsonElement.getAsJsonObject().addProperty(Literals.quantity.name(), quantity);
                }
            }
            String pretty = gson.toJson(settingsJsonArray);
            Utils.write(getSettingsFileName(), pretty);
            System.out.format("Quantity now set to %d and settings saved for instance: %s\n",
                    quantity, Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        } else {
            System.out.format("Quantity must be greater than zero. %d is not.", quantity);
        }
    }

    private void updateBrowserCommandSettings(String browserCommand) {
        settingsJsonObject.addProperty(Literals.browserCommand.name(), browserCommand);
        JsonArray settingsJsonArray = getSettingsJsonArray();
        for (JsonElement jsonElement : settingsJsonArray) {
            if (Utils.getProperty(jsonElement, Literals.id.name()).equals(Utils.getProperty(settingsJsonObject, Literals.id.name()))) {
                jsonElement.getAsJsonObject().addProperty(Literals.browserCommand.name(), browserCommand);
            }
        }
        String pretty = gson.toJson(settingsJsonArray);
        Utils.write(getSettingsFileName(), pretty);
        System.out.format("Browser command now set to %s and settings saved for instance: %s\n",
                browserCommand, Utils.getProperty(settingsJsonObject, Literals.instance.name()));
    }

    private void updateAudioFileNameSetting(String audioFileName, Literals propertyName) {
        File file = new File(audioFileName);
        if (!Literals.none.name().equalsIgnoreCase(audioFileName) && !file.exists()) {
            System.out.format("Audio file %s not found.\n", audioFileName);
            return;
        }
        settingsJsonObject.addProperty(propertyName.name(), audioFileName);
        JsonArray settingsJsonArray = getSettingsJsonArray();
        for (JsonElement jsonElement : settingsJsonArray) {
            if (Utils.getProperty(jsonElement, Literals.id.name()).equals(Utils.getProperty(settingsJsonObject, Literals.id.name()))) {
                jsonElement.getAsJsonObject().addProperty(propertyName.name(), audioFileName);
            }
        }
        String pretty = gson.toJson(settingsJsonArray);
        Utils.write(getSettingsFileName(), pretty);
        System.out.format("Audio file name now set to %s and settings saved for instance: %s\n",
                audioFileName, Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        if (audioFileName.equals(Literals.none.name())) {
            return;
        }
        if (propertyName == Literals.audioFileFails) {
            playAudioFail();
        }
        if (propertyName == Literals.audioFileNotifications) {
            playAudioNotification();
        }
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
                System.out.format("%d Instance: %s added: %s\n", i++, jsonObject.get(Literals.instance.name()), new Date(milliseconds));
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
            System.out.format("%d Instance: %s added: %s\n", i++, jsonObject.get(Literals.instance.name()), new Date(Utils.getLong(Utils.getProperty(jsonObject, "milliseconds"))));
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
        String urlString = String.format("https://%s/api/v1/lists", Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        JsonArray jsonArray = getJsonArray(urlString);
        if (jsonArray == null) {
            System.out.format("Failed to retrieve lists.\n");
            return;
        }
        for (JsonElement jsonElement : jsonArray) {
            logger.info(gson.toJson(jsonElement));
            System.out.format("%s %s\n", cyan(Utils.getProperty(jsonElement, Literals.id.name())), Utils.getProperty(jsonElement, Literals.title.name()));
        }
        System.out.format("%d lists.\n", jsonArray.size());
    }

    private JsonArray listAccounts(String listId) {
        String urlString = String.format("https://%s/api/v1/lists/%s/accounts", Utils.getProperty(settingsJsonObject, Literals.instance.name()), listId);
        JsonArray jsonArray = getJsonArray(urlString);
        if (jsonArray == null) {
            System.out.format("List id %s not found.\n", listId);
        } else {
            int i = 0;
            for (JsonElement jsonElement : jsonArray) {
                logger.info(gson.toJson(jsonElement));
                System.out.format("%d %s %s %s\n",
                        i++,
                        green(Utils.getProperty(jsonElement, Literals.acct.name())),
                        Utils.getProperty(jsonElement, Literals.display_name.name()),
                        Utils.getProperty(jsonElement, Literals.url.name()));
            }
            System.out.format("%d accounts on list %s.\n", jsonArray.size(), listId);
        }
        return jsonArray;
    }

    // // TODO: 5/26/19 Reply from notifications is picking up the wrong ID.
    private void postStatus(String text, String inReplyToId, String visibility) {
        String urlString = String.format("https://%s/api/v1/statuses", Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        JsonObject params = new JsonObject();
        params.addProperty(Literals.status.name(), text);
        params.addProperty(Literals.visibility.name(), visibility);
        if (!mediaArrayList.isEmpty()) {
            JsonArray jsonArray = new JsonArray();
            for (JsonElement jsonElement : mediaArrayList) {
                jsonArray.add(Utils.getProperty(jsonElement, Literals.id.name()));
            }
            params.add(Literals.media_ids.name(), jsonArray);
        }
        if (Utils.isNotBlank(inReplyToId)) {
            params.addProperty("in_reply_to_id", inReplyToId);
        }
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        System.out.format("Status posted: %s\n", jsonObject.get(Literals.url.name()).getAsString());
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
        params.addProperty(Literals.client_name.name(), "Jediverse CLI");
        params.addProperty(Literals.redirect_uris.name(), "urn:ietf:wg:oauth:2.0:oob");
        params.addProperty(Literals.scopes.name(), "read write follow push");
        params.addProperty(Literals.website.name(), "https://jediverse.com");
        params.addProperty(Literals.browserCommand.name(), DEFAULT_BROWSER_COMMAND);
        String urlString = String.format("https://%s/api/v1/apps", instance);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        if (!Utils.isJsonObject(jsonObject)) {
            System.out.format("Something went wrong while creating app on instance \"%s\". Try again.\n", instance);
            return;
        }
        //   System.out.format("%s\n", jsonObject.toString());
        String urlOauthDance = String.format("https://%s/oauth/authorize?scope=%s&response_type=code&redirect_uri=%s&client_id=%s\n",
                instance, Utils.urlEncodeComponent("write read follow push"), Utils.urlEncodeComponent(jsonObject.get("redirect_uri").getAsString()), jsonObject.get("client_id").getAsString());
        System.out.format("Go to %s", urlOauthDance);
        if (GraphicsEnvironment.isHeadless()) {

        } else {

        }
        String token = ask("Paste the token and press ENTER.");
        if (token == null || token.trim().length() < 20) {
            System.out.format("Token \"%s\" doesn't look valid. Try again.\n", token);
            return;
        }
        urlString = String.format("https://%s/oauth/token", instance);
        params = new JsonObject();
        params.addProperty(Literals.client_id.name(), jsonObject.get(Literals.client_id.name()).getAsString());
        params.addProperty(Literals.client_secret.name(), jsonObject.get(Literals.client_secret.name()).getAsString());
        params.addProperty(Literals.grant_type.name(), Literals.authorization_code.name());
        params.addProperty(Literals.code.name(), token);
        params.addProperty(Literals.redirect_uri.name(), jsonObject.get(Literals.redirect_uri.name()).getAsString());
        JsonObject outputJsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        jsonObject.addProperty(Literals.access_token.name(), outputJsonObject.get(Literals.access_token.name()).getAsString());
        jsonObject.addProperty(Literals.refresh_token.name(), Utils.getProperty(outputJsonObject, Literals.refresh_token.name()));
        jsonObject.addProperty(Literals.me.name(), Utils.getProperty(outputJsonObject, Literals.me.name()));
        jsonObject.addProperty(Literals.expires_in.name(), Utils.getProperty(outputJsonObject, Literals.expires_in.name()));
        jsonObject.addProperty(Literals.created_at.name(), Utils.getProperty(outputJsonObject, Literals.created_at.name()));
        jsonObject.addProperty(Literals.instance.name(), instance);
        jsonObject.addProperty(Literals.milliseconds.name(), System.currentTimeMillis());
        jsonObject.addProperty(Literals.quantity.name(), DEFAULT_QUANTITY);
        jsonObject.addProperty(Literals.audioFileNotifications.name(), DEFAULT_AUDIO_FILE_NAME_NOTIFICATIONS);
        jsonObject.addProperty(Literals.audioFileFails.name(), DEFAULT_AUDIO_FILE_NAME_FAILS);
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
        System.out.format("%s %s\n", Utils.getProperty(jsonElementWhoAmI, Literals.username.name()), Utils.getProperty(jsonElementWhoAmI, Literals.url.name()));
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
        System.out.format("%s\n", prompt);
        return readConsole();
    }

    private String readConsole() {
        try {
            return console.readLine();
        } catch (IOException e) {
            System.out.format("%s\n", e.getLocalizedMessage());
            return "";
        }
    }

    private void accountSearch(String line) {
        String searchString = line.substring(15);
        String encodedQuery = Utils.urlEncodeComponent(searchString);
        String urlString = String.format("https://%s/api/v1/accounts/search?q=%s", Utils.getProperty(settingsJsonObject, Literals.instance.name()), encodedQuery);
        System.out.format("Searching for account \"%s\". This may take some time.\n", searchString);
        try {
            jsonArrayAccounts = getJsonArray(urlString);
        } catch (Exception e) {
            System.out.format("Account search failed for \"%s\" with exception %s.\n", searchString, e.getLocalizedMessage());
            return;
        }
        if (jsonArrayAccounts == null) {
            System.out.format("Account search failed for \"%s\".\n", searchString);
            return;
        }
        System.out.format("%d results for \"%s\".\n", jsonArrayAccounts.size(), line);
        int i = 0;
        for (JsonElement account : jsonArrayAccounts) {
            System.out.format("%d %s %s %s %s %s\n",
                    i++,
                    Utils.getProperty(account, Literals.acct.name()),
                    Utils.getProperty(account, Literals.username.name()),
                    Utils.getProperty(account, Literals.display_name.name()),
                    Utils.getProperty(account, Literals.url.name()),
                    Jsoup.parse(Utils.getProperty(account, Literals.note.name())).text());
        }
        if (jsonArrayAccounts.size() == 1) {
            System.out.format("Use account-follow 0 or account-unfollow 0.\n");
        }
        if (jsonArrayAccounts.size() > 1) {
            System.out.format("Use account-follow 0 through %d or account-unfollow 0 through %d.\n",
                    jsonArrayAccounts.size() - 1, jsonArrayAccounts.size() - 1);
        }
    }

    private void printHashtags(JsonArray hashtags) {
        if (hashtags.size() == 0) {
            return;
        }
        System.out.format("\n\nHashtags\n");
        for (JsonElement je : hashtags) {
            JsonObject hashtag = je.getAsJsonObject();
            JsonArray history = hashtag.getAsJsonArray(Literals.history.name());
            if (history == null) {
                System.out.format("No results.\n");
                return;
            }
            for (JsonElement historyElement : history) {
                long day = Utils.getLong(Utils.getProperty(historyElement, Literals.day.name()));
                Date date = new Date(day * 1000);
                System.out.format("%s %s\t", Utils.getProperty(historyElement, Literals.uses.name()), Utils.getFullDate(date));
            }
            System.out.format("%s\t%s\n", Utils.getProperty(hashtag, Literals.name.name()), Utils.getProperty(hashtag, Literals.url.name()));
        }
    }

    private void printAccounts(JsonArray accounts) {
        if (accounts.size() == 0) {
            return;
        }
        System.out.format("\n\nAccounts\n");
        for (JsonElement je : accounts) {
            JsonObject account = je.getAsJsonObject();
            String displayName = getAccountDisplayName(account);
            String note = Jsoup.parse(Utils.getProperty(account, Literals.note.name())).text();
            String id = Utils.getProperty(account, "id");
            System.out.format("%s %s %s %s\n", green(displayName), Utils.getProperty(account, Literals.url.name()), note, id);
        }
    }

    private void search(String line) {
        String searchString = line.substring(7);
        System.out.format("Searching for \"%s\"...\n", searchString);
        String encodedQuery = Utils.urlEncodeComponent(searchString);
        int offset = 0;
        // TODO implement offset for searches.
        String urlString = String.format("https://%s/api/v2/search?q=%s&limit=%d&offset=%d",
                Utils.getProperty(settingsJsonObject, Literals.instance.name()), encodedQuery, getQuantity(), offset);
        JsonElement jsonElement = getJsonElement(urlString);
        if (jsonElement != null) {
            JsonArray statuses = jsonElement.getAsJsonObject().getAsJsonArray(Literals.statuses.name());
            JsonArray hashtags = jsonElement.getAsJsonObject().getAsJsonArray(Literals.hashtags.name());
            JsonArray accounts = jsonElement.getAsJsonObject().getAsJsonArray(Literals.accounts.name());
            System.out.format("%d statuses %d hasttags and %d accounts when searching for \"%s\".\n", statuses.size(), hashtags.size(), accounts.size(), searchString);
            printHashtags(hashtags);
            printAccounts(accounts);
            System.out.format("\n\nStatuses\n");
            printJsonElements(statuses, searchString);
        } else {
            System.out.format("Search failed for: %s.\n", searchString);
        }
    }

    private void timeline(String timeline, String extra) {
        String sinceId;
        String sinceIdFragment = "";
        if (jsonArrayAll.size() > 0) {
            JsonElement last = jsonArrayAll.get(jsonArrayAll.size() - 1);
            sinceId = Utils.getProperty(last, Literals.id.name());
            sinceIdFragment = String.format("&since_id=%s", sinceId);
        }
        String urlString = String.format("https://%s/api/v1/timelines/%s?limit=%d%s%s",
                settingsJsonObject.get(Literals.instance.name()).getAsString(), timeline, getQuantity(), extra, sinceIdFragment);
        JsonArray jsonArray = getJsonArray(urlString);
        if (jsonArray == null) {
            System.out.format("Failed to get timeline %s.\n", timeline);
            return;
        }
        printJsonElements(jsonArray, null);
    }

    private String getAccountDisplayName(JsonElement account) {
        String acct = Utils.getProperty(account, Literals.acct.name());
        String displayName = Utils.getProperty(account, Literals.display_name.name());
        if (Utils.isNotBlank(displayName)) {
            displayName = String.format("%s <%s>", displayName, acct);
        } else {
            displayName = acct;
        }
        return displayName;
    }

    private synchronized void printJsonElement(JsonElement jsonElement, String searchString, String event) {
        jsonArrayAll.add(jsonElement);
        logger.info(gson.toJson(jsonElement));
        String symbol = Utils.SYMBOL_PENCIL;
        JsonElement reblogJe = jsonElement.getAsJsonObject().get(Literals.reblog.name());
        String reblogLabel = "";
        if (Utils.isJsonObject(reblogJe)) {
            symbol = Utils.SYMBOL_REPEAT;
            JsonElement reblogAccountJe = reblogJe.getAsJsonObject().get(Literals.account.name());
            String reblogAccount = Utils.getProperty(reblogAccountJe, Literals.acct.name());
            String displayName = Utils.getProperty(reblogAccountJe, Literals.display_name.name());
            if (Utils.isNotBlank(displayName)) {
                displayName = String.format("%s <%s>", displayName, reblogAccount);
            } else {
                displayName = reblogAccount;
            }
            reblogLabel = yellow(displayName);
        }
        JsonElement accountJe = jsonElement.getAsJsonObject().get(Literals.account.name());
        if (!Utils.isJsonObject(accountJe)) {
            return;
        }
        String displayName = getAccountDisplayName(accountJe);
        String createdAt = Utils.getProperty(jsonElement, Literals.created_at.name());
        String content = Utils.getProperty(jsonElement, Literals.content.name());
        String text = "";
        if (Utils.isNotBlank(content)) {
            text = Jsoup.parse(content).text();
        }
        if (Utils.isNotBlank(searchString)) {
            String searchStringHighlighted = reverseVideo(searchString);
            text = text.replaceAll(searchString, searchStringHighlighted);
        }
        String type = Utils.getProperty(jsonElement, Literals.type.name());
        if (Literals.favourite.name().equals(type)) {
            symbol = Utils.SYMBOL_HEART;
            if (Utils.isBlank(text)) {
                JsonElement statusJe = jsonElement.getAsJsonObject().get(Literals.status.name());
                if (Utils.isJsonObject(statusJe)) {
                    content = Utils.getProperty(statusJe, Literals.content.name());
                    if (Utils.isNotBlank(content)) {
                        text = Jsoup.parse(content).text();
                    }
                }
            }
        }
        if (Literals.follow.name().equals(type) && Utils.isBlank(text)) {
            text = String.format("followed you. %s", Utils.getProperty(accountJe, Literals.url.name()));
        }
        if (Literals.reblog.name().equals(type) && Utils.isBlank(text)) {
            JsonElement statusJe = jsonElement.getAsJsonObject().get(Literals.status.name());
            if (Utils.isJsonObject(statusJe)) {
                text = String.format("repeated your status %s.", Utils.getProperty(statusJe, Literals.url.name()));
            }
        }
        String dateDisplay = Utils.getDateDisplay(Utils.toDate(createdAt));
        //    System.out.format("DEBUG: Reblog label \"%s\" event: \"%s\" Type \"%s\"\n", reblogLabel, event, type);
        System.out.format("%s %s%s %s %s %s", cyan(jsonArrayAll.size() - 1), symbol, reblogLabel, dateDisplay, green(displayName), text);
        JsonArray attachments = jsonElement.getAsJsonObject().getAsJsonArray(Literals.media_attachments.name());
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

    private void notifications() {
        String urlString = String.format("https://%s/api/v1/notifications?limit=%d", settingsJsonObject.get(Literals.instance.name()).getAsString(), getQuantity());
        JsonArray jsonArray = getJsonArray(urlString);
        if (jsonArray == null) {
            System.out.format("Listing notifications failed.\n");
            return;
        }
        int i = jsonArray.size() - 1;
        for (; i > -1; i--) {
            JsonElement jsonElement = jsonArray.get(i);
            jsonArrayAll.add(jsonElement);
            logger.info(gson.toJson(jsonElement));
            String symbol = Utils.SYMBOL_PENCIL;
            String text = "";
            String type = Utils.getProperty(jsonElement, Literals.type.name());
            String createdAt = Utils.getProperty(jsonElement, Literals.created_at.name());
            String dateDisplay = Utils.getDateDisplay(Utils.toDate(createdAt));
            if (Literals.favourite.name().equals(type)) {
                symbol = Utils.SYMBOL_HEART;
                JsonElement statusJe = jsonElement.getAsJsonObject().get(Literals.status.name());
                text = Jsoup.parse(Utils.getProperty(statusJe, Literals.content.name())).text();
            }
            if (Literals.follow.name().equals(type)) {
                symbol = Utils.SYMBOL_MAILBOX;
            }
            if (Literals.reblog.name().equals(type)) {
                symbol = Utils.SYMBOL_REPEAT;
                JsonElement statusJe = jsonElement.getAsJsonObject().get(Literals.status.name());
                text = Jsoup.parse(Utils.getProperty(statusJe, Literals.content.name())).text();
            }
            if (Literals.mention.name().equals(type)) {
                symbol = Utils.SYMBOL_SPEAKER;
                JsonElement statusJe = jsonElement.getAsJsonObject().get(Literals.status.name());
                text = Jsoup.parse(Utils.getProperty(statusJe, Literals.content.name())).text();
            }
            JsonElement accountJe = jsonElement.getAsJsonObject().get(Literals.account.name());
            String acct = Utils.getProperty(accountJe, Literals.acct.name());
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
            String authorization = String.format("Bearer %s", settingsJsonObject.get(Literals.access_token.name()).getAsString());
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
        return getJsonElement(urlString, false);
    }

    private JsonElement getJsonElement(String urlString, boolean ignoreExceptions) {
        URL url = Utils.getUrl(urlString);
        HttpsURLConnection urlConnection;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            String authorization = String.format("Bearer %s", settingsJsonObject.get(Literals.access_token.name()).getAsString());
            urlConnection.setRequestProperty("Authorization", authorization);
            InputStream is = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            return gson.fromJson(isr, JsonElement.class);
        } catch (IOException e) {
            if (!ignoreExceptions) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void favourite(String id) {
        String urlString = String.format("https://%s/api/v1/statuses/%s/favourite", settingsJsonObject.get(Literals.instance.name()).getAsString(), id);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), null);
        System.out.format("Favourited: %s\n", Utils.getProperty(jsonObject, Literals.url.name()));
    }

    private void unfavourite(String id) {
        String urlString = String.format("https://%s/api/v1/statuses/%s/unfavourite", settingsJsonObject.get(Literals.instance.name()).getAsString(), id);
        System.out.println(urlString);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), null);
        System.out.format("Unfavorited: %s\n", Utils.getProperty(jsonObject, Literals.url.name()));
    }

    private void accountsSearch(String q) {
        String urlString = String.format("https://%s/api/v1/accounts/search?q=%s", settingsJsonObject.get(Literals.instance.name()).getAsString(), Utils.urlEncodeComponent(q));
        System.out.println(urlString);
        jsonArrayAccountSearchResults = getJsonArray(urlString);
        if (jsonArrayAccountSearchResults.size() == 0) {
            System.out.format("No accounts found with query \"%s\"\n", q);
            return;
        } else {
            System.out.format("%d accounts found for search \"%s\"\n", jsonArrayAccountSearchResults.size(), q);
        }
        int index = -1;
        if (jsonArrayAccountSearchResults.size() != 1) {
            if (q.contains("@")) {
                System.out.format("Search contains the at symbol. Assuming it is a full user name search. Will look for exact match.\n");
                for (int i = 0; i < jsonArrayAccountSearchResults.size(); i++) {
                    JsonObject account = jsonArrayAccountSearchResults.get(i).getAsJsonObject();
                    String displayName = getAccountDisplayName(account).toLowerCase();
                    if (displayName.contains(q.toLowerCase())) {
                        index = i;
                        System.out.format("Found exact match %s\n", green(displayName));
                    }
                }

            }
            if (index == -1) {
                for (int i = 0; i < jsonArrayAccountSearchResults.size(); i++) {
                    JsonObject account = jsonArrayAccountSearchResults.get(i).getAsJsonObject();
                    String displayName = getAccountDisplayName(account);
                    System.out.format("%d %s %s %s\n", i, green(displayName), Utils.getProperty(account, Literals.username.name()), Utils.getProperty(account, Literals.url.name()));
                }
                System.out.format("Choose one of the accounts above\n");
                index = Utils.getInt(ask("Which account to list their statuses?"));
            }
            if (index > jsonArrayAccountSearchResults.size()) {
                System.out.format("Invalid selection. Index %d is greater than the account quantity of %d\n", jsonArrayAccountSearchResults.size());
                return;
            }
        } else {
            index = 0;
        }
        if (index == -1) {
            System.out.format("\"%s\" not found.\n", q);
            return;
        }
        JsonElement accountJe = jsonArrayAccountSearchResults.get(index);
        System.out.format("Retrieving statuses by %s.\n", green(getAccountDisplayName(accountJe)));
        urlString = String.format("https://%s/api/v1/accounts/%s/statuses", settingsJsonObject.get(Literals.instance.name()).getAsString(), Utils.getProperty(accountJe, Literals.id.name()));
        JsonArray statuses = getJsonArray(urlString);
        printJsonElements(statuses, null);
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
            urlConnection.setRequestProperty("User-Agent", "Jediverse CLI");
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod(Literals.POST.name());
            if (settingsJsonObject != null) {
                String authorization = String.format("Bearer %s", Utils.getProperty(settingsJsonObject, Literals.access_token.name()));
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
            urlConnection.setRequestMethod(Literals.DELETE.name());
            if (settingsJsonObject != null) {
                String authorization = String.format("Bearer %s", Utils.getProperty(settingsJsonObject, Literals.access_token.name()));
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
        String urlString = String.format("https://%s/api/v1/accounts/verify_credentials", Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        return getJsonElement(urlString);
    }

    private void upload(String line) throws Exception {
        String urlString = String.format("https://%s/api/v1/media", Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        System.out.println(urlString);
        String fileName = line.substring(7);
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.format("File: \"%s\" does not exist.\n", fileName);
            return;
        }
        var client = HttpClient.newBuilder().build();
        Map<Object, Object> data = new LinkedHashMap<>();
        data.put(Literals.access_token.name(), Utils.getProperty(settingsJsonObject, Literals.access_token.name()));
        data.put(Literals.description.name(), String.format("%s uploaded by %s CLI.", fileName, this.getClass().getSimpleName()));
        data.put(Literals.file.name(), Paths.get(fileName));
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
                fileName, Utils.getProperty(jsonElement, Literals.id.name()), mediaArrayList.size());
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

    private void about() {
        System.out.format("Jediverse is a command line interface for the Fediverse. Jediverse was created by https://pla.social/pla. YMMV ☮\n");
        System.out.format("Source %s\n", "https://github.com/pla1/Jediverse");
        System.out.format("Website https://jediverse.com/\n");
        if (debug) {
            System.out.format("JSON logging file file://%s\n", jsonLoggerFile.getAbsolutePath());
        }
        System.out.format("Settings file file://%s\n", getSettingsFileName());
        System.out.format("Java runtime: %s %s\n", System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"));
        System.out.format("User ");
        printWhoAmI();
        if (!webSockets.isEmpty()) {
            System.out.format("Streams ");
            for (String stream : streams) {
                System.out.format("%s ", stream);
            }
            System.out.format("\n");
        }
        Utils.printResourceUtilization();
        //   Utils.printProperties();
    }

    private Logger getLogger() {
        Logger logger = Logger.getLogger("JediverseJsonLog");
        if (!debug) {
            LogManager.getLogManager().reset();
            return logger;
        }
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
        String urlString = String.format("https://%s/api/v1/blocks", Utils.getProperty(settingsJsonObject, Literals.instance.name()));
        JsonArray jsonArray = getJsonArray(urlString);
        if (jsonArray == null) {
            System.out.format("List blocked accounts failed.\n");
            return;
        }
        logger.info(jsonArray.toString());
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonElement jsonElement = jsonArray.get(i);
            String displayName = Utils.getProperty(jsonElement, Literals.display_name.name());
            String acct = Utils.getProperty(jsonElement, Literals.acct.name());
            if (Utils.isBlank(displayName)) {
                displayName = acct;
            } else {
                displayName = String.format("%s <%s> %s", displayName, acct, Utils.getProperty(jsonElement, Literals.url.name()));
            }
            System.out.format("%s\n", displayName);
        }
        System.out.format("%d blocked accounts.\n", jsonArray.size());

    }

    private void uploadWithFileBrowser() throws Exception {
        final JFileChooser fc = new JFileChooser();
        Action action = fc.getActionMap().get("viewTypeDetails");
        action.actionPerformed(null);
        int returnVal = fc.showOpenDialog(new Frame());
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            upload(String.format("upload %s", file.getAbsolutePath()));
        } else {
            System.out.format("No files uploaded.\n");
        }
    }

    private void context(JsonElement jsonElement) {
        String urlString = String.format("https://%s/api/v1/statuses/%s/context",
                Utils.getProperty(settingsJsonObject, Literals.instance.name()),
                Utils.getProperty(jsonElement, Literals.id.name()));
        JsonElement contextJsonElement = getJsonElement(urlString);
        // System.out.format("%s\n", contextJsonElement.toString());
        JsonArray descendants = new JsonArray();
        JsonArray ancestors = new JsonArray();
        if (Utils.isJsonObject(contextJsonElement)) {
            JsonObject contextJsonObject = contextJsonElement.getAsJsonObject();
            if (contextJsonObject != null) {
                descendants.addAll(contextJsonObject.getAsJsonArray(Literals.descendants.name()));
                ancestors.addAll(contextJsonObject.getAsJsonArray(Literals.ancestors.name()));
            }
        }
        JsonArray jsonArray = new JsonArray();
        for (JsonElement je : descendants) {
            jsonArray.add(je);
        }
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            jsonArray.add(ancestors.get(i));
        }
        printJsonElements(jsonArray, null);
        System.out.format("\nEnd of context\n\n");
    }

    private void instanceInfo(String line, boolean full) {
        String instance = line.split("\\s+")[1].trim();
        String url = String.format("https://%s/api/v1/instance", instance);
        JsonElement jsonElement = getJsonElement(url);
        if (jsonElement == null) {
            System.out.format("Failed to get instance information for %s.\n", instance);
            return;
        }
        System.out.format("\nInstance: https://%s\nDescription: %s\n", instance, Jsoup.parse(Utils.getProperty(jsonElement, Literals.description.name())).text());
        System.out.format("Version: %s\n", Utils.getProperty(jsonElement, Literals.version.name()));
        JsonElement stats = jsonElement.getAsJsonObject().get(Literals.stats.name());
        System.out.format("Users: %s\n", Utils.getIntegerDisplay(Utils.getProperty(stats, Literals.user_count.name())));
        System.out.format("Statuses: %s\n", Utils.getIntegerDisplay(Utils.getProperty(stats, Literals.status_count.name())));
        System.out.format("Domains: %s\n", Utils.getIntegerDisplay(Utils.getProperty(stats, Literals.domain_count.name())));
        System.out.format("Registration open: %s\n", Utils.isYes(Utils.getProperty(stats, Literals.registrations.name())));
        if (!full) {
            return;
        }
        url = String.format("https://%s/nodeinfo/2.1.json", instance);
        jsonElement = getJsonElement(url, true);
        if (jsonElement == null) {
            return;
        }
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        System.out.format("Protocols: %s\n", Utils.toString(jsonObject.getAsJsonArray(Literals.protocols.name())));
        JsonObject jsonObjectMetadata = jsonObject.getAsJsonObject(Literals.metadata.name());
        System.out.format("Staff accounts: %s\n", Utils.toString(jsonObjectMetadata.getAsJsonArray(Literals.staffAccounts.name()), true, true));
        System.out.format("Post formats: %s\n", Utils.toString(jsonObjectMetadata.getAsJsonArray(Literals.postFormats.name()), true, true));
        JsonObject jsonObjectFederation = jsonObjectMetadata.getAsJsonObject(Literals.federation.name());
        JsonObject jsonObjectMrfSimple = jsonObjectFederation.getAsJsonObject(Literals.mrf_simple.name());
        System.out.format("Quarantined instances: %s\n", Utils.toString(jsonObjectFederation.getAsJsonArray(Literals.quarantined_instances.name()), true, true));
        System.out.format("MRF policies: %s\n", Utils.toString(jsonObjectFederation.getAsJsonArray(Literals.mrf_policies.name()), true, true));
        System.out.format("MRF simple - reject: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.reject.name()), true, true));
        System.out.format("MRF simple - report removal: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.report_removal.name()), true, true));
        System.out.format("MRF simple - media removal: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.media_removal.name()), true, true));
        System.out.format("MRF simple - media NSFW: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.media_nsfw.name()), true, true));
        System.out.format("MRF simple - federated timeline removal: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.federated_timeline_removal.name()), true, true));
        System.out.format("MRF simple - banner removal: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.banner_removal.name()), true, true));
        System.out.format("MRF simple - avatar removal: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.avatar_removal.name()), true, true));
        System.out.format("MRF simple - accept: %s\n", Utils.toString(jsonObjectMrfSimple.getAsJsonArray(Literals.accept.name()), true, true));
    }

    private void updateOnstartCommandSettings(String line) {
        String startupCommand = line.substring(Literals.onstart.name().length() + 1);
        settingsJsonObject.addProperty(Literals.onstart.name(), startupCommand);
        JsonArray settingsJsonArray = getSettingsJsonArray();
        for (JsonElement jsonElement : settingsJsonArray) {
            if (Utils.getProperty(jsonElement, Literals.id.name()).equals(Utils.getProperty(settingsJsonObject, Literals.id.name()))) {
                jsonElement.getAsJsonObject().addProperty(Literals.onstart.name(), startupCommand);
            }
        }
        String pretty = gson.toJson(settingsJsonArray);
        Utils.write(getSettingsFileName(), pretty);
        System.out.format("Startup command now set to \"%s\" and settings saved for instance: %s\n",
                startupCommand, Utils.getProperty(settingsJsonObject, Literals.instance.name()));

    }

    private enum Literals {
        audioFileNotifications, audioFileFails, id, instance, me, milliseconds, quantity,
        browserCommand, client_name, scopes, website, grant_type, access_token, refresh_token,
        redirect_uri, redirect_uris, client_id, client_secret, code, expires_in, created_at, content, type, status,
        none, search, clear, about, blocks, context, debug, ok, url, go, notification, event, payload, acct, display_name,
        properties, local, notifications, timeline, note, tl, following, followers, lists, gc, stop, home, post, POST, DELETE, unlisted,
        follow, reblog, favourite, mention, direct, fav, reply, rep, help, quit, exit, whoami, unfav, account_ids, username,
        visibility, upload, unfollow, title, media_ids, file, description, authorization_code, followed_by, history, day, uses, name,
        ancestors, descendants, account, accounts, hashtags, statuses, media_attachments, aa, sa, da, user_count, status_count,
        domain_count, stats, registrations, version, protocols, staffAccounts, metadata, postFormats, quarantined_instances, mrf_policies, mrf_simple,
        federation, reject, report_removal, media_removal, federated_timeline_removal, banner_removal, avatar_removal, accept,
        media_nsfw, onstart
    }

    private class WebSocketListener implements Listener {
        private final String stream;
        private final JsonParser jsonParser = new JsonParser();
        private StringBuilder sb = new StringBuilder();

        class Pinger extends Thread {
            WebSocket webSocket;

            Pinger(WebSocket webSocket) {
                this.webSocket = webSocket;
            }

            @Override
            public void run() {
                while (true) {
                    webSocket.sendPing(ByteBuffer.wrap("PING".getBytes()));
                    Utils.sleep(30);
                }
            }
        }

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
                    String event = Utils.getProperty(messageJsonElement, Literals.event.name());
                    String payloadString = messageJsonElement.getAsJsonObject().get(Literals.payload.name()).getAsString();
                    if (Literals.notification.name().equals(event)) {
                        playAudioNotification();
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
                }
                sb = new StringBuilder();
            } else {
                sb.append(data);
            }
            return Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            return Listener.super.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            pongTime = System.currentTimeMillis();
            return Listener.super.onPong(webSocket, message);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.format("WebSocket opened for %s stream.\n", stream);
            Pinger pinger = new Pinger(webSocket);
            pinger.start();
            Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.format("WebSocket closed. Status code: %d Reason: %s Stream: %s\n.", statusCode, reason, stream);
            webSocket.abort();
            playAudioFail();
            return Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.out.format("WebSocket error: %s.\n", error.getLocalizedMessage());
            error.printStackTrace();
            playAudioFail();
            Listener.super.onError(webSocket, error);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            System.out.format("Binary data received on WebSocket.\n");
            return Listener.super.onBinary(webSocket, data, last);
        }
    }
}
