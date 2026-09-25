package dev.frogchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;

import java.awt.Color;
import java.util.UUID;

/**
 * A colour per sender.
 *
 * <p>Chosen by hand when the config names them, and otherwise derived from the name — which is what
 * makes the automatic case worth reading: the same player is the same colour tomorrow, on a different
 * server, and on everyone else's client running this. A rotating palette would recolour people as they
 * join and leave, and there would be nothing stable to override.
 *
 * <p>For the derived colour only the hue varies. Saturation and value are pinned at a pastel that stays
 * legible against chat, so no name can hash its way to unreadable. A hand-picked colour is used exactly
 * as given — if somebody wants their name in near-black, that is their business.
 *
 * <p>With {@link ChatConfig.NameColourSource#LOCATOR LOCATOR} as the source, the derivation instead
 * copies the locator bar: the tracked waypoint's colour when the server set one (team colours and
 * {@code /waypoint} both land there), and otherwise vanilla's own formula for an uncoloured dot, so
 * a name matches its dot whenever the dot exists and keeps the colour it would have when it does
 * not. {@code pastelColours} applies to both sources: on, the colour is hue-only, with saturation
 * and value pinned here, so a dark team colour or an unlucky hash can never make a name
 * unreadable; off, the source's colour is used at full strength.
 */
public final class NameTint {

    private static final float SATURATION = 0.5f;
    private static final float VALUE = 0.95f;

    private NameTint() {}

    public static int of(String name) {
        return of(name, FrogChat.config());
    }

    /** As {@link #of(String)}, under the given settings — the config screen passes a live view. */
    static int of(String name, ChatConfig config) {
        Integer chosen = config.overrideFor(name);
        if (chosen != null) return chosen;
        if (config.nameColourSource == ChatConfig.NameColourSource.LOCATOR) {
            Integer dot = locator(name);
            if (dot != null) return config.pastelColours ? pastel(dot) : dot;
        }
        return derived(name, config.pastelColours);
    }

    private static int derived(String name, boolean pastel) {
        float hue = Math.floorMod(name.hashCode(), 360) / 360f;
        return Color.HSBtoRGB(hue, pastel ? SATURATION : 1f, pastel ? VALUE : 1f) & 0xFFFFFF;
    }

    /** The hue of {@code rgb} at the usual pastel pins, so the source shows through but lightly. */
    private static int pastel(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return Color.HSBtoRGB(hsb[0], SATURATION, VALUE) & 0xFFFFFF;
    }

    /**
     * The colour the locator bar shows for this player, or null for anyone unknowable — a server
     * they are not on. While off any server entirely the only knowable player is the local one,
     * whose would-be dot follows from their own UUID: exactly the preview's case.
     */
    private static Integer locator(String name) {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid;
        if (mc.getConnection() == null) {
            if (!mc.getUser().getName().equalsIgnoreCase(name)) return null;
            uuid = mc.getUser().getProfileId();
        } else {
            PlayerInfo sender = mc.getConnection().getPlayerInfo(name);
            if (sender == null) return null;
            uuid = sender.getProfile().id();
            Integer sent = waypointColour(mc, uuid);
            if (sent != null) return sent;
        }
        // The exact formula LocatorBar#extractRenderState uses for a waypoint with no colour of its
        // own, so a far or hidden player still gets the colour their dot would have in range.
        return ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F) & 0xFFFFFF;
    }

    /** The colour the server put on this player's waypoint, or null when it has none of its own. */
    private static Integer waypointColour(Minecraft mc, UUID uuid) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) return null;

        Integer[] found = { null };
        mc.getConnection().getWaypointManager().forEachWaypoint(camera, waypoint -> {
            if (found[0] == null && waypoint.id().left().filter(uuid::equals).isPresent()) {
                found[0] = waypoint.icon().color.orElse(null);
            }
        });
        return found[0];
    }
}
