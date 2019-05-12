package com.pla.jediverse;


import com.google.common.io.BaseEncoding;
import com.google.gson.*;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

public class Utils {
    public static final String SYMBOL_HEART = "❤️";
    public static final String SYMBOL_SPEAKER = "\uD83D\uDD0A";
    public static final String SYMBOL_PENCIL = "\uD83D\uDD89";
    public static final String SYMBOL_PADLOCK_LOCKED = "\uD83D\uDD12";
    public static final String SYMBOL_PADLOCK_UNLOCKED = "\uD83D\uDD13";
    public static final String SYMBOL_KEY = "\uD83D\uDD11";
    public static final String SYMBOL_MAILBOX = "\uD83D\uDCEA";
    public static final String SYMBOL_REPEAT = "♻";

    public static String getAuthorizationString(String userProfile, String password) {
        StringBuilder buf = new StringBuilder();
        buf.append("Basic ");
        String userProfileAndPassword = String.format("%s:%s", userProfile, password);
        String base64Encoded = BaseEncoding.base64().encode(userProfileAndPassword.getBytes());
        buf.append(base64Encoded.replaceAll("\n", ""));
        return buf.toString();
    }

    public static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        if (s.trim().length() == 0) {
            return true;
        }
        return false;
    }

    public static String urlEncodeComponent(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

    }

    public static void write(String outputFileName, String text) {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(outputFileName, "UTF-8");
            pw.write(text);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(pw);
        }
        close(pw);
    }

    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    public static int getInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    public static String getProperty(JsonElement jsonElement, String propertyName) {
        if (jsonElement == null) {
            return null;
        }
        JsonElement property = jsonElement.getAsJsonObject().get(propertyName);
        if (property != null) {
            return property.getAsString();
        }
        return null;
    }

    public static void sleep(long seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void close(Object... objects) {
        for (Object object : objects) {
            if (object != null) {
                try {
                    boolean closed = false;
                    if (object instanceof java.io.BufferedOutputStream) {
                        BufferedOutputStream bufferedOutputStream = (BufferedOutputStream) object;
                        bufferedOutputStream.close();
                        closed = true;
                    }
                    if (object instanceof java.io.StringWriter) {
                        StringWriter stringWriter = (StringWriter) object;
                        stringWriter.close();
                        closed = true;
                    }
                    if (object instanceof java.sql.Statement) {
                        Statement statement = (Statement) object;
                        statement.close();
                        closed = true;
                    }
                    if (object instanceof java.io.FileReader) {
                        FileReader fileReader = (FileReader) object;
                        fileReader.close();
                        closed = true;
                    }
                    if (object instanceof java.sql.ResultSet) {
                        ResultSet rs = (ResultSet) object;
                        rs.close();
                        closed = true;
                    }
                    if (object instanceof java.sql.PreparedStatement) {
                        PreparedStatement ps = (PreparedStatement) object;
                        ps.close();
                        closed = true;
                    }
                    if (object instanceof java.sql.Connection) {
                        Connection connection = (Connection) object;
                        connection.close();
                        closed = true;
                    }
                    if (object instanceof java.io.BufferedReader) {
                        BufferedReader br = (BufferedReader) object;
                        br.close();
                        closed = true;
                    }
                    if (object instanceof Socket) {
                        Socket socket = (Socket) object;
                        socket.close();
                        closed = true;
                    }
                    if (object instanceof PrintStream) {
                        PrintStream printStream = (PrintStream) object;
                        printStream.close();
                        closed = true;
                    }
                    if (object instanceof ServerSocket) {
                        ServerSocket serverSocket = (ServerSocket) object;
                        serverSocket.close();
                        closed = true;
                    }
                    if (object instanceof Scanner) {
                        Scanner scanner = (Scanner) object;
                        scanner.close();
                        closed = true;
                    }
                    if (object instanceof InputStream) {
                        InputStream inputStream = (InputStream) object;
                        inputStream.close();
                        closed = true;
                    }
                    if (object instanceof OutputStream) {
                        OutputStream outputStream = (OutputStream) object;
                        outputStream.close();
                        closed = true;
                    }
                    if (object instanceof Socket) {
                        Socket socket = (Socket) object;
                        socket.close();
                        closed = true;
                    }
                    if (object instanceof PrintWriter) {
                        PrintWriter pw = (PrintWriter) object;
                        pw.close();
                        closed = true;
                    }
                    if (!closed) {
                        String msg = "Object not closed. Object type not defined in this close method. " + object.getClass().getName()
                                + " class stack: "
                                + getClassNames();
                        System.out.format("Object not closed. Object type not defined in this close method. Name: %s Stack: %s\n", object.getClass().getName(), getClassNames());
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    public static String getClassNames() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StringBuilder classNames = new StringBuilder();
        for (StackTraceElement e : stackTraceElements) {
            classNames.append(e.getClassName()).append(", ");
        }
        if (classNames.toString().endsWith(", ")) {
            classNames.delete(classNames.length() - 2, classNames.length());
        }
        return classNames.toString();
    }

    public static long getLong(String s) {
        try {
            return Long.getLong(s);
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    public static void print(Object object) {
        if (object == null) {
           return;
        }
        if (object instanceof JsonObject) {
            JsonObject jsonObject = (JsonObject) object;
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(jsonObject));
        }
        if (object instanceof JsonArray) {
            JsonArray jsonArray = (JsonArray) object;
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(jsonArray));
        }
        if (object instanceof String[]) {
            String[] strings = (String[]) object;
            int i = 0;
            for (String string : strings) {
                System.out.format("%d:%d", i++, string);
            }
        }
    }

    public static URL getUrl(String urlString) {
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return url;
    }


}
