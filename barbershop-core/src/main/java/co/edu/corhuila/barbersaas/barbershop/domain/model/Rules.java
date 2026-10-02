package co.edu.corhuila.barbersaas.barbershop.domain.model;

import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;

/** The length and range checks every aggregate repeats; the same limits as the CHECKs of barbershop-db. */
final class Rules {

    private Rules() {
    }

    /** Required text, stripped, between 1 and {@code max} characters. */
    static String requiredText(String value, int max, String field) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty() || v.length() > max) {
            throw new BusinessRuleViolation("The " + field + " must have between 1 and " + max + " characters");
        }
        return v;
    }

    /** Optional text: blank becomes null; otherwise at most {@code max} characters. */
    static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        if (v.length() > max) {
            throw new BusinessRuleViolation("The " + field + " has at most " + max + " characters");
        }
        return v;
    }

    static int atLeast(int value, int min, String field) {
        if (value < min) {
            throw new BusinessRuleViolation("The " + field + " must be at least " + min);
        }
        return value;
    }

    static long atLeast(long value, long min, String field) {
        if (value < min) {
            throw new BusinessRuleViolation("The " + field + " must be at least " + min);
        }
        return value;
    }
}
