package dev.toasty.client;

import java.math.BigDecimal;
import java.util.Locale;

/** Kleiner Ausdrucks-Rechner: + - * / ^ ( ) % und Suffixe k m b t (z. B. 1.5k * 2m). Komma = Dezimaltrennzeichen. */
public final class ToastyCalc {
    private final String s;
    private int pos;

    private ToastyCalc(String s) { this.s = s; }

    /** @return Ergebnis oder null bei ungueltigem Ausdruck */
    public static Double eval(String in) {
        if (in == null) return null;
        String t = in.replace(" ", "").replace(',', '.').replace('x', '*').replace('×', '*').replace(':', '/');
        if (t.isEmpty()) return null;
        try {
            ToastyCalc c = new ToastyCalc(t);
            double v = c.expr();
            if (c.pos != c.s.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (Exception e) { return null; }
    }

    private boolean eat(char ch) {
        if (pos < s.length() && s.charAt(pos) == ch) { pos++; return true; }
        return false;
    }

    private double expr() {
        double v = term();
        while (true) {
            if (eat('+')) v += term();
            else if (eat('-')) v -= term();
            else return v;
        }
    }

    private double term() {
        double v = unary();
        while (true) {
            if (eat('*')) v *= unary();
            else if (eat('/')) v /= unary();
            else return v;
        }
    }

    private double unary() {
        if (eat('-')) return -unary();
        if (eat('+')) return unary();
        return power();
    }

    private double power() {
        double base = postfix();
        if (eat('^')) return Math.pow(base, unary());
        return base;
    }

    private double postfix() {
        double v = primary();
        while (eat('%')) v /= 100.0;
        return v;
    }

    private double primary() {
        if (eat('(')) {
            double v = expr();
            if (!eat(')')) throw new IllegalArgumentException("')' fehlt");
            return v;
        }
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
        if (start == pos) throw new IllegalArgumentException("Zahl erwartet");
        double v = Double.parseDouble(s.substring(start, pos));
        if (pos < s.length()) {
            char c = Character.toLowerCase(s.charAt(pos));
            double f = switch (c) { case 'k' -> 1e3; case 'm' -> 1e6; case 'b' -> 1e9; case 't' -> 1e12; default -> 0; };
            if (f != 0) { v *= f; pos++; }
        }
        return v;
    }

    // ---------- Formatierung ----------

    public static String plain(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 9e15) return Long.toString(Math.round(v));
        return new BigDecimal(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 1500000 -> "1.500.000" ; 2.5 -> "2,5" */
    public static String pretty(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 9e15) return String.format(Locale.GERMANY, "%,d", Math.round(v));
        String r = String.format(Locale.GERMANY, "%,.4f", v);
        return r.replaceAll("0+$", "").replaceAll(",$", "");
    }
}
