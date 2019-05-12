package com.pla.jediverse;

import com.google.gson.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Main {
    private final Properties properties = new Properties();
    private static final Map<Integer, String> items = new HashMap<Integer, String>();
    private static BufferedReader console;


    public Main() {
        console = new BufferedReader(new InputStreamReader(System.in));
        try {
            String propertiesFileName = String.format("/home/htplainf/.config/Jediverse/%s.properties", this.getClass().getCanonicalName());
            System.out.println(propertiesFileName);
            properties.load(new FileInputStream(propertiesFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String ask(String prompt) {
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
    private static void createApp(File propertyFile) throws Exception {
        String instance = ask("Type your instance name and press ENTER. For example: pleroma.site");
        if (Utils.isBlank(instance)) {
            System.out.format("Instance can not be blank.\n");
            System.exit(-1);
        }
        String urlString = String.format("https://%s/api/v1/apps?client_name=Jediverse&redirect_uris=urn:ietf:wg:oauth:2.0:oob&scopes=write,read,follow,push&website=%s",
                instance, Utils.urlEncodeComponent("https://github.com/pla1/jediverse"));
        System.out.println(urlString);
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        //  conn.setDoOutput(true);
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject jsonObject = gson.fromJson(isr, JsonObject.class);
        System.out.format("%s\n", jsonObject.toString());
        System.out.format("Go to https://%s/oauth/authorize?scope=write,read,follow,push&response_type=code&redirect_uri=%s&client_id=%s\n", instance, jsonObject.get("redirect_uri").getAsString(), jsonObject.get("client_id").getAsString());
        String token = ask("Paste the token and press ENTER.");
        jsonObject.addProperty("access_token", token);
        String pretty = gson.toJson(jsonObject);
        Utils.write(propertyFile.getAbsolutePath(), pretty);
    }
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        String propertyFileName = String.format("%s/.jediverse.json", System.getProperty("user.home"));
        File propertyFile = new File(propertyFileName);
        if (!propertyFile.exists()) {
           createApp(propertyFile);
        }
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = console.readLine()) != null) {
            System.out.println(line);
            String timeline = "public";
            timeline = "friends";
            int quantity = 10;
            String[] words = line.split("\\s+");
            if ("fed".equals(line)) {
                timeline = "fed";
                main.timeline(timeline, quantity);
            }
            if ("timeline".equals(line)) {
                main.timeline(timeline, quantity);
            }
            if ("m".equals(line)) {
                main.timelineM(timeline, quantity);
            }
            if ("notifications".equals(line) || "note".equals(line)) {
                main.notifications(quantity);
            }
            if (words.length == 2 && "fav".equals(words[0])) {
                String statusId = items.get(Utils.getInt(words[1]));
                String action = "create";
                JsonElement jsonElement = main.favorite(statusId, action);
                System.out.format("%s\n", jsonElement);
            }
            if (words.length == 2 && "unfav".equals(words[0])) {
                String statusId = items.get(Utils.getInt(words[1]));
                String action = "destroy";
                JsonElement jsonElement = main.favorite(statusId, action);
                System.out.format("%s\n", jsonElement);
            }
            if ("quit".equals(line)) {
                System.exit(0);
            }
        }

        System.exit(0);
    }

    public JsonObject favorite(String statusId, String action) throws Exception {
        String instance = properties.getProperty("instance");
        String urlString = String.format("https://%s/api/favorites/%s/%s.json", instance, action, statusId);
        System.out.println(urlString);
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", Utils.getAuthorizationString(username, password));
        conn.setRequestMethod("POST");
        //  conn.setDoOutput(true);
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.fromJson(isr, JsonObject.class);
    }

    public void timeline(String timeline, int quantity) throws Exception {
        if ("fed".equals(timeline)) {
            timeline = "public_and_external";
        }
        String urlString = String.format("https://%s/api/statuses/%s_timeline.json?count=%d", properties.getProperty("instance"), timeline, quantity);
        JsonArray jsonArray = getJsonArray(urlString);
        for (JsonElement jsonElement : jsonArray) {
            String activityType = Utils.getProperty(jsonElement, "activity_type");
            String text = Utils.getProperty(jsonElement, "text");
            String id = Utils.getProperty(jsonElement, "id");
            items.put(items.size(), id);
            String createdAt = Utils.getProperty(jsonElement, "created_at").replace(" +0000", "");
            String screenName = Utils.getProperty(jsonElement.getAsJsonObject().get("user"), "screen_name");
            String activityTypeDisplay = activityType;
            if ("like".equals(activityType)) {
                activityTypeDisplay = Utils.SYMBOL_HEART;
                text += Utils.getProperty(jsonElement.getAsJsonObject().get("favorited_status").getAsJsonObject(), "text");
            }
            if ("post".equals(activityType)) {
                activityTypeDisplay = Utils.SYMBOL_PENCIL;
            }
            if ("repeat".equals(activityType)) {
                activityTypeDisplay = Utils.SYMBOL_REPEAT;
                text += Utils.getProperty(jsonElement.getAsJsonObject().get("retweeted_status").getAsJsonObject(), "text");
                //    System.out.println(jsonElement);
            }
            System.out.format("%s %d %s %s %s\n", activityTypeDisplay, items.size() - 1, createdAt, screenName, text);
        }
        System.out.format("%d items.\n", jsonArray.size());
    }

    public void notifications(int quantity) throws Exception {
        String instance = properties.getProperty("instance");
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        URL url = new URL(String.format("https://%s/api/qvitter/statuses/notifications.json?count=%d", instance, quantity));
        //  URL url = new URL(String.format("https://%s/api/v1/notifications?limit=%d", instance, quantity));
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", Utils.getAuthorizationString(username, password));
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonArray jsonArray = gson.fromJson(isr, JsonArray.class);
        for (JsonElement jsonElement : jsonArray) {
            String createdAt = jsonElement.getAsJsonObject().get("created_at").getAsString().replace(" +0000", "");
            // System.out.format("JSON: %s\n", jsonElement);
            String ntype = Utils.getProperty(jsonElement, "ntype");
            JsonElement noticeElement = jsonElement.getAsJsonObject().get("notice");
            String text = Utils.getProperty(noticeElement, "text");
            String ntypeDisplay = ntype;
            String favoritedStatusText = "";
            if ("mention".equals(ntype)) {
                ntypeDisplay = Utils.SYMBOL_SPEAKER;
            }
            if ("repeat".equals(ntype)) {
                ntypeDisplay = Utils.SYMBOL_REPEAT;
            }
            if ("like".equals(ntype)) {
                ntypeDisplay = Utils.SYMBOL_HEART;
                JsonElement favoritedStatusElement = jsonElement.getAsJsonObject().get("notice").getAsJsonObject().get("favorited_status");
                favoritedStatusText = favoritedStatusElement.getAsJsonObject().get("text").getAsString();

            }
            System.out.format("%s %s %s %s %s %s\n", ntypeDisplay, createdAt, text, favoritedStatusText, "", Utils.getProperty(noticeElement, "id"));
        }
        System.out.format("%d items.\n", jsonArray.size());

    }


    public void timelineM(String timeline, int quantity) throws Exception {
        String urlString = String.format("https://%s/api/v1/timelines/home", properties.getProperty("instance"));
        JsonArray jsonArray = getJsonArray(urlString);
        for (JsonElement jsonElement : jsonArray) {
            String symbol = Utils.SYMBOL_PENCIL;
            JsonElement accountJe = jsonElement.getAsJsonObject().get("account");
            String acct = Utils.getProperty(accountJe, "acct");
            String createdAt = Utils.getProperty(jsonElement, "created_at");
            JsonElement reblogJe = jsonElement.getAsJsonObject().get("reblog");
            String text = null;
            JsonElement pleromaJe = null;
            if (reblogJe != null) {
                pleromaJe = jsonElement.getAsJsonObject().get("pleroma");
                symbol = Utils.SYMBOL_REPEAT;
            }
            //   System.out.println(jsonElement);
            JsonElement contentJe = pleromaJe.getAsJsonObject().get("content");
            text = Utils.getProperty(contentJe, "text/plain");
            System.out.format("%s %s %s %s\tJSON: %s\n", symbol, createdAt, acct, text, jsonElement);
        }
        System.out.format("%d items.\n", jsonArray.size());
    }

    private JsonArray getJsonArray(String urlString) throws Exception {
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", Utils.getAuthorizationString(username, password));
        InputStream is = conn.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.fromJson(isr, JsonArray.class);
    }

}


