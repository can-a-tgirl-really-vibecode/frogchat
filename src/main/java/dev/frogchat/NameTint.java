package dev.frogchat;

import java.awt.Color;

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
 */
public final class NameTint {

    private static final float SATURATION = 0.5f;
    private static final float VALUE = 0.95f;

    private NameTint() {}

    public static int of(String name) {
        Integer chosen = FrogChat.config().overrideFor(name);
        return chosen != null ? chosen : derived(name);
    }

    private static int derived(String name) {
        int bucket = Math.floorMod(name.hashCode(), 360);
        return Color.HSBtoRGB(bucket / 360f, SATURATION, VALUE) & 0xFFFFFF;
    }
}
