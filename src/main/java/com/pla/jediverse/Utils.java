package com.pla.jediverse;


import com.google.common.io.BaseEncoding;
import com.google.gson.*;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Utils {
    public static final String ANSI_REVERSE_VIDEO = "\u001B[7m";
    public static final String ANSI_BOLD = "\u001B[1m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_RED_BACKGROUND = "\u001B[41m";
    public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
    public static final String ANSI_YELLOW_BACKGROUND = "\u001B[43m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_PURPLE_BACKGROUND = "\u001B[45m";
    public static final String ANSI_CYAN_BACKGROUND = "\u001B[46m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";
    public static final String SYMBOL_HEART = "❤️";
    public static final String SYMBOL_SPEAKER = "\uD83D\uDD0A";
    public static final String SYMBOL_PENCIL = "\uD83D\uDD89";
    public static final String SYMBOL_PADLOCK_LOCKED = "\uD83D\uDD12";
    public static final String SYMBOL_PADLOCK_UNLOCKED = "\uD83D\uDD13";
    public static final String SYMBOL_KEY = "\uD83D\uDD11";
    public static final String SYMBOL_MAILBOX = "\uD83D\uDCEA ";
    public static final String SYMBOL_REPEAT = "♻ ";

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

    public static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    public static boolean isNotNumberic(String s) {
        return !isNumeric(s);
    }

    public static int getInt(String s) {
        if (s == null) {
            return 0;
        }
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
        if (property != null && !property.isJsonNull()) {
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
        if (s == null) {
            return 0;
        }
        try {
            return Long.parseLong(s);
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
    public static URI getUri(String urlString) {
        URI uri = null;
        try {
            uri = new URL(urlString).toURI();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return uri;
    }

    public static Logger getLogger() {
        Logger logger = Logger.getLogger("JediverseJsonLog");
        FileHandler fh;
        try {
            File file = File.createTempFile("jediverse_json_log_", ".log");
            System.out.format("JSON log file: %s\n", file.getAbsolutePath());
            fh = new FileHandler(file.getAbsolutePath());
            logger.setUseParentHandlers(false);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return logger;
    }

    public static boolean isSameDay(Date date1, Date date2) {
        Calendar calendar1 = Calendar.getInstance();
        Calendar calendar2 = Calendar.getInstance();
        calendar1.setTime(date1);
        calendar2.setTime(date2);
        return (calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR)
                && calendar1.get(Calendar.DAY_OF_YEAR) == calendar2.get(Calendar.DAY_OF_YEAR));
    }

    public static String run(String[] commandParts) {
        BufferedReader reader = null;
        StringBuilder output = new StringBuilder();
        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(commandParts);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String lineRead = null;
            while ((lineRead = reader.readLine()) != null) {
                output.append(lineRead);
                output.append("\n");
            }
            int exitValue = process.waitFor();
        } catch (Exception e) {
            output.append("Exception: " + e.getLocalizedMessage());
        } finally {
            close(reader);
        }
        return output.toString();
    }

    public static String readFileToString(String fileName) {
        StringBuilder sb = new StringBuilder();
        File file = new File(fileName);
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new FileReader(file));
            String line = bufferedReader.readLine();
            String lineSeparator = System.getProperty("line.separator");
            while (line != null) {
                sb.append(line);
                sb.append(lineSeparator);
                line = bufferedReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(bufferedReader);
        }

        return sb.toString();
    }

    public static String getDateDisplay(Date date) {
        if (date == null) {
            return "";
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        StringBuffer buf = new StringBuffer();
        buf.append(" a");
        if (calendar.get(Calendar.SECOND) != 0) {
            buf.insert(0, ":ss");
        }
        if (calendar.get(Calendar.MINUTE) != 0 || calendar.get(Calendar.SECOND) != 0) {
            buf.insert(0, ":mm");
        }
        buf.insert(0, " h");
        if (!isYearEqual(calendar.getTime(), new Date())) {
            buf.insert(0, ", yyyy");
        }
        if (!isToday(calendar.getTime())) {
            buf.insert(0, "EEE MMM dd");
        }
        SimpleDateFormat sdf = new SimpleDateFormat(buf.toString());
        String string = sdf.format(date);
        if (!date.after(getMidnight()) && !date.before(getMidnight())) {
            return "Midnight";
        }
        return string.trim();
    }

    public static boolean isYearEqual(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar calendar1 = Calendar.getInstance();
        Calendar calendar2 = Calendar.getInstance();
        calendar1.setTime(date1);
        calendar2.setTime(date2);
        if (calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR)) {
            return true;
        } else {
            return false;
        }
    }

    public static Date getMidnight() {
        return setTimeToMidnight(new Date());
    }

    public static Date setTimeToMidnight(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Calendar setTimeToMidnight(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    public static boolean isToday(java.util.Date date) {
        if (date == null) {
            return false;
        }
        Calendar calendarToday = Calendar.getInstance();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return (calendar.get(Calendar.DAY_OF_YEAR) == calendarToday.get(Calendar.DAY_OF_YEAR)
                && calendar.get(Calendar.YEAR) == calendarToday.get(Calendar.YEAR));
    }

    public static Date toDate(String timestampString) {
        if (timestampString == null) {
            return null;
        }
        SimpleDateFormat dateFormatTimestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'");
        dateFormatTimestamp.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            return dateFormatTimestamp.parse(timestampString);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

}
