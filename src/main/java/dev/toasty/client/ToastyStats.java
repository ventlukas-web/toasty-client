package dev.toasty.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Session statistics from the house's point of view. */
public class ToastyStats {
    public static long income, payouts;
    public static int rounds, wins, losses;
    public static final Map<String, Long> pending = new LinkedHashMap<>();
    public static String lastPayer;

    public static long profit() { return income - payouts; }

    public static double winrate() { return rounds == 0 ? 0 : 100.0 * wins / rounds; }

    public static void reset() {
        income = payouts = 0; rounds = wins = losses = 0;
        pending.clear(); lastPayer = null;
    }
}
