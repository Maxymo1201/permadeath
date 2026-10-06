package com.serthekiller.permadeath.core.rules;

/**
 * Totem of Undying rules per Permadeath day (single source of truth, no magic numbers elsewhere).
 *
 * <pre>
 * D0-29  : 0 % failure, 100 % success, 1 totem
 * D30-39 : 1 % failure,  99 % success, 1 totem
 * D40-49 : 3 % failure,  97 % success, 2 totems
 * D50-59 : 5 % failure,  95 % success, 2 totems
 * D60+   : 7 % failure,  93 % success, 3 totems
 * </pre>
 *
 * Historical source: PermaDeathCore config ({@code TotemFail.FailProbs}: 30-39 → 1, 40-49 → 3, 50-59 → 5,
 * 60-69 → 7; no entry before D30 = never fails) and TotemConsumeEvent ({@code neededTotems = day < 60 ? 2 : 3}
 * from D40, not enough totems ⇒ the totem does not work).
 */
public final class TotemRules {
    /** First day on which the Permadeath totem system (RNG + messages) is active. */
    public static final int SYSTEM_START_DAY = 30;

    private TotemRules() {
    }

    public record TotemRule(int failurePercent, int requiredTotems) {
        public int successPercent() {
            return 100 - failurePercent;
        }

        public double failureChance() {
            return failurePercent / 100.0;
        }

        public double successChance() {
            return successPercent() / 100.0;
        }

        /**
         * @param roll uniform integer in [0, 100)
         * @return true if this roll makes the totem fail
         */
        public boolean fails(int roll) {
            if (roll < 0 || roll >= 100) {
                throw new IllegalArgumentException("roll must be in [0,100): " + roll);
            }
            return roll < failurePercent;
        }
    }

    public static TotemRule forDay(int day) {
        if (day >= 60) {
            return new TotemRule(7, 3);
        }
        if (day >= 50) {
            return new TotemRule(5, 2);
        }
        if (day >= 40) {
            return new TotemRule(3, 2);
        }
        if (day >= 30) {
            return new TotemRule(1, 1);
        }
        return new TotemRule(0, 1);
    }

    public static int getFailureChance(int day) {
        return forDay(day).failurePercent();
    }

    public static int getRequiredTotems(int day) {
        return forDay(day).requiredTotems();
    }

    /** Spanish word used by the broadcast ("un", "dos", "tres"). */
    public static String amountWord(int totems) {
        return switch (totems) {
            case 1 -> "un";
            case 2 -> "dos";
            case 3 -> "tres";
            default -> Integer.toString(totems);
        };
    }
}
