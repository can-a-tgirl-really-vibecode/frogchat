package dev.frogchat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Wall-clock phrasing for the hover tooltip. Nothing is tracked here any longer: the moment a line
 * arrived is recorded directly beside it at intake (see {@link ChatMeta}), so all that remains is
 * turning millis into words.
 */
public final class MessageClock {

    private MessageClock() {}

    /** "14:32:07" in the player's own zone. */
    public static String clockText(long millis) {
        LocalTime time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime();
        return String.format("%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
    }

    /** "just now", "12s ago", "4m ago", "2h 10m ago" — how long the line has been sitting there. */
    public static String ageText(long millis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000L);
        if (seconds < 5) return "just now";
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3600) return (seconds / 60) + "m ago";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return minutes == 0 ? hours + "h ago" : hours + "h " + minutes + "m ago";
    }
}
