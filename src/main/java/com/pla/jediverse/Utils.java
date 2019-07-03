package com.pla.jediverse;


import com.google.gson.*;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.Clip;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Utils {
    public static final String OS = System.getProperty("os.name").toLowerCase();
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
    public static final String SYMBOL_PICTURE_FRAME = "\uD83D\uDDBC";

    public static int alphaComparison(String s1, String s2) {
        if (s1 == null) {
            return 1;
        }
        if (s2 == null) {
            return -1;
        }
        return Utils.lettersAndSpaces(s1.toLowerCase()).trim().compareTo(Utils.lettersAndSpaces(s2.toLowerCase()).trim());
    }

    public static String lettersAndSpaces(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[^A-Za-z ]+","");
    }


    public static boolean isBlank(String s) {
        return (s == null || s.trim().length() == 0);
    }

    public static String urlEncodeComponent(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElement = jsonParser.parse("STRING");
        System.out.format("JSON Object: %s\n", isJsonObject(jsonElement));
        jsonElement = jsonParser.parse("{\"field1\":\"value1\"}");
        System.out.format("JSON Object: %s\n", isJsonObject(jsonElement));
        System.exit(0);
    }

    public static void write(String outputFileName, String text) {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(outputFileName, StandardCharsets.UTF_8);
            pw.write(text);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(pw);
        }
        close(pw);
    }

    public static boolean isNumeric(String s) {
        if (s == null || s.trim().length() == 0) {
            return false;
        }
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
                    if (object instanceof AudioInputStream) {
                        AudioInputStream audioInputStream = (AudioInputStream) object;
                        audioInputStream.close();
                        closed = true;
                    }
                    if (object instanceof Clip) {
                        Clip clip = (Clip) object;
                        clip.close();
                        closed = true;
                    }
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
                System.out.format("%d:%s", i++, string);
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
            String lineRead;
            while ((lineRead = reader.readLine()) != null) {
                output.append(lineRead);
                output.append("\n");
            }
            int exitValue = process.waitFor();
        } catch (Exception e) {
            output.append("Exception: ");
            output.append(e.getLocalizedMessage());
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
        StringBuilder buf = new StringBuilder();
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
        return (calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR));

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

    public static boolean contains(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return false;
        }
        return (s1.toLowerCase().contains(s2.toLowerCase()));
    }

    public static boolean isJsonObject(JsonElement jsonElement) {
        if (jsonElement == null) {
            return false;
        }
        try {
            jsonElement.getAsJsonObject();
            return true;
        } catch (java.lang.IllegalStateException e) {
            return false;
        }
    }

    public static String humanReadableByteCount(long bytes) {
        return humanReadableByteCount(bytes, true);

    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static boolean isYes(String s) {
        if (s == null) {
            return false;
        }
        if ("true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("yes".equalsIgnoreCase(s)) {
            return true;
        }
        if ("y".equalsIgnoreCase(s)) {
            return true;
        }
        return "1".equalsIgnoreCase(s);
    }


    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMac() {
        return OS.contains("mac");
    }

    public static boolean isUnix() {
        return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
    }

    public static boolean isSolaris() {
        return OS.contains("sunos");
    }
    public static void printResourceUtilization() {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
        long bytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.format("Memory used %s B\n", numberFormat.format(bytes));
        if (Utils.isUnix()) {
            long pid = ProcessHandle.current().pid();
            String[] commandParts = {"ps", "--pid", Long.toString(pid), "-o", "%cpu,%mem"};
            String output = Utils.run(commandParts);
            System.out.format("PID %d\n%s\n", pid, output);
        }
    }
    public static String getFullDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd yyyy");
        return sdf.format(date);
    }
    public static void printProperties() {
        Properties p = System.getProperties();
        p.list(System.out);
    }

}
