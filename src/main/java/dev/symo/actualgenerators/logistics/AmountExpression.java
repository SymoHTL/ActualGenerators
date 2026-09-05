package dev.symo.actualgenerators.logistics;

import dev.symo.actualgenerators.machine.TransferKind;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * What a player types into one of the pad's numbers: {@code 400k}, {@code 1.5M}, {@code 2B},
 * {@code 64*3}, {@code (2k+500)/2}, {@code 2s}.
 *
 * <p>The four arithmetic operators, brackets and a unary minus, on numbers with a suffix:
 * {@code k} a thousand, {@code m} a million, {@code b} a billion (a bucket, on a fluid),
 * {@code s} seconds in ticks, {@code t} ticks. Unit words ({@code FE}, {@code mB}) and spaces are
 * ignored. Anything else is not a number, and the field says so rather than guessing.
 */
public final class AmountExpression {
    private final String text;
    private final @Nullable TransferKind kind;
    private int at;

    private AmountExpression(String text, @Nullable TransferKind kind) {
        this.text = text;
        this.kind = kind;
    }

    /** The value the text works out to, or empty when it does not work out at all. */
    public static OptionalLong parse(String raw, @Nullable TransferKind kind) {
        String text = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replace("fe", "").replace("mb", "");
        if (text.isEmpty()) {
            return OptionalLong.empty();
        }
        AmountExpression parser = new AmountExpression(text, kind);
        try {
            double value = parser.expression();
            if (parser.at != text.length() || !Double.isFinite(value)) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.round(value));
        } catch (IllegalStateException malformed) {
            return OptionalLong.empty();
        }
    }

    private double expression() {
        double value = term();
        while (at < text.length() && (peek() == '+' || peek() == '-')) {
            char operator = text.charAt(at++);
            double right = term();
            value = operator == '+' ? value + right : value - right;
        }
        return value;
    }

    private double term() {
        double value = factor();
        while (at < text.length() && (peek() == '*' || peek() == '/')) {
            char operator = text.charAt(at++);
            double right = factor();
            if (operator == '/' && right == 0) {
                throw new IllegalStateException("divided by zero");
            }
            value = operator == '*' ? value * right : value / right;
        }
        return value;
    }

    private double factor() {
        if (at >= text.length()) {
            throw new IllegalStateException("ran out at " + at);
        }
        char first = peek();
        if (first == '-') {
            at++;
            return -factor();
        }
        if (first == '(') {
            at++;
            double inner = expression();
            if (at >= text.length() || text.charAt(at) != ')') {
                throw new IllegalStateException("no closing bracket");
            }
            at++;
            return inner;
        }
        return number();
    }

    private double number() {
        int start = at;
        while (at < text.length() && (Character.isDigit(peek()) || peek() == '.')) {
            at++;
        }
        if (start == at) {
            throw new IllegalStateException("expected a number at " + at);
        }
        double value;
        try {
            value = Double.parseDouble(text.substring(start, at));
        } catch (NumberFormatException malformed) {
            throw new IllegalStateException("not a number: " + text.substring(start, at));
        }
        if (at < text.length()) {
            double scale = switch (peek()) {
                case 'k' -> 1_000;
                case 'm' -> 1_000_000;
                case 'b' -> kind == TransferKind.FLUID ? 1_000 : 1_000_000_000;
                case 's' -> 20;
                case 't' -> 1;
                default -> 0;
            };
            if (scale > 0) {
                at++;
                value *= scale;
            }
        }
        return value;
    }

    private char peek() {
        return text.charAt(at);
    }
}
