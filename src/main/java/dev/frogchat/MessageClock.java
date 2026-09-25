package dev.frogchat;

import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * When each chat line arrived, in wall-clock terms.
 *
 * <p>The problem this solves: a chat line remembers its arrival only as {@code addedTime}, a GUI tick
 * counter, and ticks are not clock time — they stall when the game pauses and stretch under lag, so
 * converting one to a time of day by multiplying by 50ms drifts, and drifts further the older the line is.
 * That is exactly backwards for a hover tooltip, whose whole use is reading old lines.
 *
 * <p>So the tick is not converted at all. {@link #stamp()} runs as a message is added and notes the tick
 * the game is on <i>beside</i> the real clock; a line's {@code addedTime} is set from that same tick in the
 * same frame, so the lookup later is an exact hit rather than an estimate. Two messages in one tick share
 * a stamp, which is correct — they did arrive together.
 */
public final class MessageClock {

    /** Comfortably more than the hundred messages chat keeps, so a live line is never off the end. */
    private static final int REMEMBERED = 512;

    private static final Map<Integer, Long> arrivals = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Long> eldest) {
            return size() > REMEMBERED;
        }
    };

    private MessageClock() {}

    /** Notes that a message is arriving on this GUI tick. Called from the message hook, nothing else. */
    public static void stamp() {
        Integer tick = currentTick();
        if (tick == null) return;
        arrivals.putIfAbsent(tick, System.currentTimeMillis());
    }

    /**
     * Wall-clock millis for a line added on {@code addedTime}.
     *
     * <p>Falls back to the 50ms-per-tick estimate for lines older than the table — vanilla's own
     * "sent by another client" placeholders, or anything already in chat when the mod loaded. Wrong by
     * however much the game stalled, which is acceptable for a line we never saw arrive and much better
     * than showing nothing.
     */
    public static long at(int addedTime) {
        Long exact = arrivals.get(addedTime);
        if (exact != null) return exact;

        Integer now = currentTick();
        if (now == null) return System.currentTimeMillis();
        return System.currentTimeMillis() - (long) (now - addedTime) * 50L;
    }

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

    /** 26.3 moved the GUI tick counter off {@code Gui} and onto the {@code Hud} it now owns. */
    private static Integer currentTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) return null;
        return mc.gui.hud.getGuiTicks();
    }
}
