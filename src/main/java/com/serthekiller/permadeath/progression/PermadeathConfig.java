package com.serthekiller.permadeath.progression;

import com.serthekiller.permadeath.BuildProfile;
import com.serthekiller.permadeath.core.ProgressionMode;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server options ({@code config/permadeath-server.toml}; a copy in {@code <world>/serverconfig/} overrides it for that
 * world only). The calendar profile itself (GAME60 / REAL30) is NOT an option: it is fixed by the jar.
 */
public final class PermadeathConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue STRICT_CAMPAIGN_DURATION;
    public static final ModConfigSpec.BooleanValue FREEZE_AFTER_CAMPAIGN;
    public static final ModConfigSpec.IntValue WITHER_ACCUMULATION_LIMIT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("campaign");
        STRICT_CAMPAIGN_DURATION = b
                .comment("Solo REAL30. false (por defecto): D60 llega a las 720 h reales y el desafío final dura 6 h efectivas",
                        "más (se pausa con el servidor apagado o sin supervivientes). true: D60 se adelanta a la hora 714 para que",
                        "el desafío final termine exactamente a las 720 h del calendario (los hitos D10-D50 no cambian).")
                .define("strictCampaignDuration", false);
        FREEZE_AFTER_CAMPAIGN = b
                .comment("true (por defecto): al terminar el desafío final se registra el resultado y se detienen los",
                        "temporizadores periódicos de la campaña (Withers del D60). false: supervivencia libre con todas las",
                        "mecánicas del D60, Withers periódicos incluidos. El mundo, los bloques y los inventarios no se tocan.")
                .define("freezeAfterCampaign", true);
        b.pop();
        b.push("wither");
        WITHER_ACCUMULATION_LIMIT = b
                .comment("Protección opcional para servidores: si hay al menos este número de Withers a 128 bloques del",
                        "jugador, su Wither periódico espera (se registra en el log). 0 (por defecto) = desactivada.")
                .defineInRange("witherAccumulationLimit", 0, 0, 64);
        b.pop();
        SPEC = b.build();
    }

    private PermadeathConfig() {
    }

    /** REAL30 strict campaign (always false in GAME60). */
    public static boolean strictCampaign() {
        return BuildProfile.mode() == ProgressionMode.REAL30 && value(STRICT_CAMPAIGN_DURATION, false);
    }

    public static boolean freezeAfterCampaign() {
        return value(FREEZE_AFTER_CAMPAIGN, true);
    }

    public static int witherAccumulationLimit() {
        return value(WITHER_ACCUMULATION_LIMIT, 0);
    }

    private static <T> T value(ModConfigSpec.ConfigValue<T> option, T fallback) {
        try {
            return SPEC.isLoaded() ? option.get() : fallback;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
