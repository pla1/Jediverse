package com.pla.jediverse;

import com.google.gson.*;
import org.jsoup.Jsoup;

import javax.net.ssl.HttpsURLConnection;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// TODO: 5/16/19 Need to clear objects and stop streaming when switching instances.


public class CommandLineInterface {
    private static BufferedReader console;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final int DEFAULT_QUANTITY = 20;
    private JsonObject settingsJsonObject;
    private JsonArray jsonArrayAll = new JsonArray();
    private Logger logger;
    private ThreadStreaming threadStreaming;
    private JsonArray jsonArrayFollowing = new JsonArray();

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

    private void playAudio() {
        String audioFileName = getAudioFileName();
        if ("none".equalsIgnoreCase(audioFileName)) {
            return;
        }
        AudioInputStream audioInputStream = null;
        Clip clip = null;
        try {
            File file = new File(audioFileName).getAbsoluteFile();
        //    System.out.format("Audio file: %s\n", file.getAbsolutePath());
            audioInputStream = AudioSystem.getAudioInputStream(file);
            clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.close(clip, audioInputStream);
        }
    }

    private void setup() {
        console = new BufferedReader(new InputStreamReader(System.in));
        logger = Utils.getLogger();
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
        while ((line = console.readLine()) != null) {
            //     System.out.println(line);
            line = line.trim();
            String[] words = line.split("\\s+");
            if (line.startsWith("search") && words.length > 1) {
                search(line);
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
                    "stream-user".equals(line) ||
                    "stream-direct".equals(line)) {
                if (threadStreaming == null) {
                    threadStreaming = new ThreadStreaming();
                    threadStreaming.start();
                }
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
                if (Utils.isNotBlank(stream)) {
                    threadStreaming.streamingStart(stream);
                }
            }
            if ("stop".equals(line)) {
                if (threadStreaming != null) {
                    threadStreaming.streamingStop();
                }
            }
            if ("timeline".equals(line) || "tl".equals(line)) {
                timeline("home", "");
            }
            if ("notifications".equals(line) || "note".equals(line)) {
                notifications("");
            }
            if (words.length > 1 && "toot-direct".equals(words[0])) {
                String text = line.substring(5);
                toot(text, null, "direct");
            }
            if (words.length > 1 && "toot".equals(words[0])) {
                String text = line.substring(5);
                toot(text, null, "public");
            }
            if (words.length > 1 && "toot-private".equals(words[0])) {
                String text = line.substring(5);
                toot(text, null, "private");
            }
            if (words.length > 1 && "toot-unlisted".equals(words[0])) {
                String text = line.substring(5);
                toot(text, null, "unlisted");
            }
            if (words.length > 2 && ("rep".equals(words[0]) || "reply".equals(words[0]))) {
                if (Utils.isNumeric(words[1])) {
                    int index = Utils.getInt(words[1]);
                    if (index > jsonArrayAll.size()) {
                        System.out.format("Item at index: %d not found.\n", index);
                    } else {
                        JsonElement jsonElement = jsonArrayAll.get(index);
                        String text = line.substring(line.indexOf(words[1]) + words[1].length());
                        // TODO: 5/17/19  Get visibility from jsonElement to set the proper visibility on the reply.
                        toot(text, Utils.getProperty(jsonElement, "id"), "unlisted");
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
            if ("quit".equals(line)) {
                System.exit(0);
            }
            if ("help".equals(line)) {
                System.out.println(Utils.readFileToString("help.txt"));
            }
            if ("whoami".equals(line)) {
                JsonElement jsonElement = whoami();
                System.out.format("%s %s\n", Utils.getProperty(jsonElement, "username"), Utils.getProperty(jsonElement, "url"));
            }
        }


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
        System.out.format("RESPONSE: %s\n", jsonElement.toString());
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
                System.out.format("Added %d accounts. Total: %d\n", jsonArray.size(), jsonArrayFollowing.size());
                url = null;
                if (Utils.isNotBlank(linkHeader)) {
                    System.out.format("Link header: %s\n", linkHeader);
                    Pattern pattern = Pattern.compile("<([^>]+)>;\\s+rel=\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(linkHeader);
                    while (matcher.find()) {
                        urlString = matcher.group(1);
                        String rel = matcher.group(2);
                        System.out.format("URL: %s REL: %s\n", urlString, rel);
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
        for (JsonElement jsonElement : jsonArrayFollowing) {
            logger.info(jsonElement.toString());
            System.out.format("%s %s\n", green(Utils.getProperty(jsonElement, "acct")), Utils.getProperty(jsonElement, "username"));
        }
        System.out.format("Following %d accounts.\n", jsonArrayFollowing.size());
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
        if (!file.exists()) {
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
                //         System.out.format("Milliseconds: \"%s\" %d\n", millisecondsString, milliseconds);
                System.out.format("%d Instance: %s added: %s\n", i++, jsonObject.get("instance"), new Date(milliseconds));
            }
            String answer = ask("");
            if (Utils.isNotBlank(answer)) {
                int index = Utils.getInt(answer);
                //  System.out.format("Instance index: %d\n",index);
                if (index > settingsJsonArray.size() || settingsJsonArray.size() == 0) {
                    System.out.format("Instance number %d not found.\n", index);
                } else {
                    settingsJsonObject = settingsJsonArray.get(index).getAsJsonObject();
                }
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
            logger.info(jsonElement.toString());
            System.out.format("%s %s\n", Utils.getProperty(jsonElement, "id"), Utils.getProperty(jsonElement, "title"));
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
                logger.info(jsonElement.toString());
                System.out.format("%d %s %s %s\n", i++, green(Utils.getProperty(jsonElement, "acct")), Utils.getProperty(jsonElement, "display_name"), Utils.getProperty(jsonElement, "url"));
            }
        }
        return jsonArray;
    }

    private void toot(String text, String inReplyToId, String visibility) {
        String urlString = String.format("https://%s/api/v1/statuses", Utils.getProperty(settingsJsonObject, "instance"));
        JsonObject params = new JsonObject();
        params.addProperty("status", text);
        params.addProperty("visibility", visibility);
        if (Utils.isNotBlank(inReplyToId)) {
            params.addProperty("in_reply_to_id", inReplyToId);
        }
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        System.out.format("Tooted: %s\n", jsonObject.get("url").getAsString());
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
        params.addProperty("website", "https://github.com/pla1/Jediverse");
        String urlString = String.format("https://%s/api/v1/apps", instance);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        //   System.out.format("%s\n", jsonObject.toString());
        System.out.format("Go to https://%s/oauth/authorize?scope=%s&response_type=code&redirect_uri=%s&client_id=%s\n",
                instance, Utils.urlEncodeComponent("write read follow push"), Utils.urlEncodeComponent(jsonObject.get("redirect_uri").getAsString()), jsonObject.get("client_id").getAsString());
        String token = ask("Paste the token and press ENTER.");
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
        whoami();
        //   System.out.println(jsonObject);
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
        try {
            String line;
            System.out.format("%s\n", prompt);
            while ((line = console.readLine()) != null) {
                System.out.println(line);
                return line;
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
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

    private void printJsonElement(JsonElement jsonElement, String searchString) {
        jsonArrayAll.add(jsonElement);
        logger.info(jsonElement.toString());
        String symbol = Utils.SYMBOL_PENCIL;
        JsonElement reblogJe = jsonElement.getAsJsonObject().get("reblog");
        String reblogLabel = "";
        if (Utils.isJsonObject(reblogJe)) {
            symbol = Utils.SYMBOL_REPEAT;
            JsonElement reblogAccountJe = reblogJe.getAsJsonObject().get("account");
            String reblogAccount = Utils.getProperty(reblogAccountJe, "acct");
            reblogLabel = yellow(reblogAccount);
        }
        JsonElement accountJe = jsonElement.getAsJsonObject().get("account");
        if (!Utils.isJsonObject(accountJe)) {
            return;
        }
        String acct = Utils.getProperty(accountJe, "acct");
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
        String dateDisplay = Utils.getDateDisplay(Utils.toDate(createdAt));
        System.out.format("%d %s%s %s %s %s\n", jsonArrayAll.size() - 1, symbol, reblogLabel, dateDisplay, green(acct), text);
    }

    private void printJsonElements(JsonArray jsonArray, String searchString) {
        int i = jsonArray.size() - 1;
        for (; i > -1; i--) {
            JsonElement jsonElement = jsonArray.get(i);
            printJsonElement(jsonElement, searchString);
        }
    }

    private String yellow(String s) {
        return String.format("%s%s%s%s", Utils.ANSI_BOLD, Utils.ANSI_YELLOW, s, Utils.ANSI_RESET);
    }

    private String green(String s) {
        return String.format("%s%s%s%s", Utils.ANSI_BOLD, Utils.ANSI_GREEN, s, Utils.ANSI_RESET);
    }

    private String reverseVideo(String s) {
        return String.format("%s%s%s", Utils.ANSI_REVERSE_VIDEO, s, Utils.ANSI_RESET);
    }

    private void notifications(String extra) {
        String urlString = String.format("https://%s/api/v1/notifications?limit=%d%s", settingsJsonObject.get("instance").getAsString(), DEFAULT_QUANTITY, extra);
        System.out.println(urlString);
        JsonArray jsonArray = getJsonArray(urlString);
        int i = jsonArray.size() - 1;
        for (; i > -1; i--) {
            JsonElement jsonElement = jsonArray.get(i);
            jsonArrayAll.add(jsonElement);
            logger.info(jsonElement.toString());
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
            }
            if ("mention".equals(type)) {
                symbol = Utils.SYMBOL_SPEAKER;
                JsonElement statusJe = jsonElement.getAsJsonObject().get("status");
                text = Jsoup.parse(Utils.getProperty(statusJe, "content")).text();
            }
            JsonElement accountJe = jsonElement.getAsJsonObject().get("account");
            String acct = Utils.getProperty(accountJe, "acct");
            //   System.out.format("\n\n%s %s %s\n%s\n", symbol, acct, text, jsonElement);
            System.out.format("%d %s %s %s %s\n", jsonArrayAll.size() - 1, symbol, dateDisplay, green(acct), text);
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
                //         System.out.format("Response code: %d\n", responseCode);
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

    private class ThreadStreaming extends Thread {
        WebSocket.Listener wsListener = new WebSocket.Listener() {
            private StringBuilder sb = new StringBuilder();
            private Gson gson = new GsonBuilder().setPrettyPrinting().create();
            private JsonParser jsonParser = new JsonParser();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                logger.info(String.format("onText Last: %s Data: %s", last, data));
                //      System.out.format("onText Last: %s Data: %s\n", last, data);
                if (last) {
                    sb.append(data);
                    JsonElement messageJsonElement = jsonParser.parse(sb.toString());
                    if (Utils.isJsonObject(messageJsonElement)) {
                        String payload = messageJsonElement.getAsJsonObject().get("payload").getAsString();
                        if (Utils.isNotBlank(payload)) {
                            JsonElement payloadJsonElement = jsonParser.parse(payload);
                            if (Utils.isJsonObject(payloadJsonElement)) {
                                playAudio();
                                printJsonElement(payloadJsonElement, null);
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
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                System.out.format("onPing Message: %s\n", message);
                return WebSocket.Listener.super.onPing(webSocket, message);
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                System.out.format("onPong Message: %s\n", message);
                return WebSocket.Listener.super.onPong(webSocket, message);
            }

            @Override
            public void onOpen(WebSocket webSocket) {
                System.out.println("Websocket opened.");
                WebSocket.Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                System.out.println("onClose: " + statusCode + " " + reason);
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                System.out.format("Websocket error: %s.\n", error.getLocalizedMessage());
                error.printStackTrace();
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                System.out.format("Binary data received on websocket.\n");
                return WebSocket.Listener.super.onBinary(webSocket, data, last);
            }
        };
        private WebSocket webSocket;

        private ThreadStreaming() {
            this.setDaemon(true);
        }

        private void streamingStart(String stream) {
            var client = HttpClient.newHttpClient();
            String urlString = String.format("wss://%s/api/v1/streaming/?stream=%s&access_token=%s",
                    Utils.getProperty(settingsJsonObject, "instance"), stream, Utils.getProperty(settingsJsonObject, "access_token"));
            webSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create(urlString), wsListener).join();
        }

        private void streamingStop() {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            }
        }

        @Override
        public void run() {
        }
    }
}
