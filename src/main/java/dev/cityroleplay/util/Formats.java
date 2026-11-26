package dev.cityroleplay.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US);

    private Formats() {
    }

    public static String money(double amount, int precision, String symbol) {
        BigDecimal value = BigDecimal.valueOf(amount).setScale(Math.max(0, precision), RoundingMode.HALF_UP);
        return symbol + value.toPlainString();
    }

    public static String duration(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            out.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            out.append(minutes).append("m ");
        }
        out.append(secs).append("s");
        return out.toString().trim();
    }

    public static String date(long epochMillis, ZoneId zone) {
        if (epochMillis <= 0) {
            return "Never";
        }
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    public static String dateTime(long epochMillis, ZoneId zone) {
        return DATE.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    public static double round(double value, int precision) {
        return BigDecimal.valueOf(value).setScale(Math.max(0, precision), RoundingMode.HALF_UP).doubleValue();
    }

    public static boolean isFinitePositive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    public static boolean isFiniteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
