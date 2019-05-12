package com.pla.jediverse;

import com.google.gson.*;
import org.jsoup.Jsoup;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.Date;
import java.util.logging.Logger;

public class CommandLineInterface {
    private static BufferedReader console;
    private JsonObject settingsJsonObject;
    private JsonArray jsonArrayAll = new JsonArray();
    private Logger logger;

    public static void main(String[] args) {
        CommandLineInterface cli = new CommandLineInterface();
        System.exit(0);
    }

    private void setup() {
        logger = Utils.getLogger();
        JsonArray settingsJsonArray = getSettings();
        while (settingsJsonArray == null) {
            createApp();
            settingsJsonArray = getSettings();
        }
        // Utils.print(settingsJsonArray);
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

    private void mainRoutine() throws Exception {
        String line;
        while ((line = console.readLine()) != null) {
            System.out.println(line);
            int quantity = 20;
            String[] words = line.split("\\s+");
            if ("fed".equals(line)) {
                timeline("public", quantity, "&local=false");
            }
            if ("local".equals(line)) {
                timeline("public", quantity, "&local=true");
            }
            if ("timeline".equals(line)) {
                timeline("home", quantity, "");
            }
            if ("notifications".equals(line) || "note".equals(line)) {
                notifications(quantity, "");
            }
            if (words.length > 1 && "toot".equals(words[0])) {
                String text = line.substring(5);
                toot(text);
            }
            if (words.length == 2 && "fav".equals(words[0])) {
                JsonElement jsonElement = jsonArrayAll.get(Utils.getInt(words[1]));
                System.out.format("Fav this: %s\n", jsonElement.toString());
                favourite(Utils.getProperty(jsonElement, "id"));
            }
            if (words.length == 2 && "url".equals(words[0])) {
                int index = Utils.getInt(words[1]);
                JsonElement jsonElement = jsonArrayAll.get(index);
                System.out.format("%d %s\n", index, Utils.getProperty(jsonElement,"url"));
            }
            if (words.length == 2 && "go".equals(words[0])) {
                int index = Utils.getInt(words[1]);
                JsonElement jsonElement = jsonArrayAll.get(index);
                String urlString = Utils.getProperty(jsonElement,"url");
                if (Utils.isNotBlank(urlString)) {
                   Utils.run(new String[] {"xdg-open",urlString});
                }
            }
            if (words.length == 2 && "unfav".equals(words[0])) {
                JsonElement jsonElement = jsonArrayAll.get(Utils.getInt(words[1]));
                System.out.format("Fav this: %s\n", jsonElement.toString());
                unfavourite(Utils.getProperty(jsonElement, "id"));
            }
            if ("quit".equals(line)) {
                System.exit(0);
            }
        }


    }

    public CommandLineInterface() {
        console = new BufferedReader(new InputStreamReader(System.in));
        setup();
        try {
            mainRoutine();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JsonObject chooseInstance(JsonArray settingsJsonArray) {
        System.out.format("Choose one of these %d instances.\n", settingsJsonArray.size());
        int i = 0;
        for (JsonElement jsonElement : settingsJsonArray) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            System.out.format("%d Instance: %s added: %s\n", i++, jsonObject.get("instance"), new Date(jsonObject.get("milliseconds").getAsLong()));
        }
        int selection = Utils.getInt(ask(""));
        return settingsJsonArray.get(selection).getAsJsonObject();
    }

    private void toot(String text) {
        String urlString = String.format("https://%s/api/v1/statuses", settingsJsonObject.get("instance").getAsString());
        JsonObject params = new JsonObject();
        params.addProperty("status", text);
        params.addProperty("visibility", "direct");
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
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), params.toString());
        System.out.format("%s\n", jsonObject.toString());
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
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(jsonObject);
        String pretty = gson.toJson(jsonArray);
        Utils.write(getSettingsFileName(), pretty);
        System.out.println(jsonObject);
    }

    private String getSettingsFileName() {
        return String.format("%s%s.jediverse.json", System.getProperty("user.home"), File.separator);
    }

    private JsonArray getSettings() {
        JsonArray jsonArray = null;
        String propertyFileName = getSettingsFileName();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
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

    private void timeline(String timeline, int quantity, String extra) {
        String urlString = String.format("https://%s/api/v1/timelines/%s?limit=%d%s", settingsJsonObject.get("instance").getAsString(), timeline, quantity, extra);
        JsonArray jsonArray = getJsonArray(urlString);
        for (JsonElement jsonElement : jsonArray) {
            jsonArrayAll.add(jsonElement);
            logger.info(jsonElement.toString());
            String symbol = Utils.SYMBOL_PENCIL;
            JsonElement accountJe = jsonElement.getAsJsonObject().get("account");
            String acct = Utils.getProperty(accountJe, "acct");
            String createdAt = Utils.getProperty(jsonElement, "created_at");
            String text = Jsoup.parse(Utils.getProperty(jsonElement, "content")).text();
            //   System.out.format("%s %s %s %s\tJSON: %s\n", symbol, createdAt, acct, text, jsonElement);
            System.out.format("%d %s %s %s %s\n", jsonArrayAll.size() - 1, symbol, createdAt, acct, text);
        }

        System.out.format("%d items.\n", jsonArray.size());
    }


    private void notifications(int quantity, String extra) {
        String urlString = String.format("https://%s/api/v1/notifications?limit=%d%s", settingsJsonObject.get("instance").getAsString(), quantity, extra);
        System.out.println(urlString);
        JsonArray jsonArray = getJsonArray(urlString);
        for (JsonElement jsonElement : jsonArray) {
            logger.info(jsonElement.toString());
            String symbol = Utils.SYMBOL_PENCIL;
            String text = "";
            String type = Utils.getProperty(jsonElement, "type");
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
            System.out.format("%s %s %s\n", symbol, acct, text);
        }
        System.out.format("%d items.\n", jsonArray.size());
    }

    private JsonArray getJsonArray(String urlString) {
        URL url = Utils.getUrl(urlString);
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
            String authorization = String.format("Bearer %s", settingsJsonObject.get("access_token").getAsString());
            urlConnection.setRequestProperty("Authorization", authorization);
            InputStream is = urlConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.fromJson(isr, JsonArray.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void favourite(String id) {
        String urlString = String.format("https://%s/api/v1/statuses/%s/favourite", settingsJsonObject.get("instance").getAsString(), id);
        System.out.println(urlString);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), null);
        System.out.format("Favorited: %s\n", Utils.getProperty(jsonObject, "url"));
    }

    private void unfavourite(String id) {
        String urlString = String.format("https://%s/api/v1/statuses/%s/unfavourite", settingsJsonObject.get("instance").getAsString(), id);
        System.out.println(urlString);
        JsonObject jsonObject = postAsJson(Utils.getUrl(urlString), null);
        System.out.format("Unfavorited: %s\n", Utils.getProperty(jsonObject, "url"));
    }

    private JsonObject postAsJson(URL url, String json) {
        System.out.format("postAsJson URL: %s JSON: \n%s\n", url.toString(), json);
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
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            jsonObject = gson.fromJson(isr, JsonObject.class);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Utils.close(inputStream, outputStream);
        }
        return jsonObject;
    }

}
