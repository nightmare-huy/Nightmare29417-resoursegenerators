package com.projectinfinity.pigenerator.energy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

/** Text forms of energy amounts that do not fit into a {@code long}. */
public final class EnergyFormat {
    /** Amounts with more digits than this are written as "1.2345E30". */
    private static final int MAX_FULL_DIGITS = 18;
    private static final String[] UNITS = {"k", "M", "G", "T", "P", "E"};

    /** 1234567 -> "1,234,567"; from 10^18 on: "1.2345E18" */
    public static String full(BigInteger value) {
        String digits = value.toString();
        if (digits.length() > MAX_FULL_DIGITS) {
            return scientific(value);
        }
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** 950 -> "950", 1234567 -> "1.23M", 8*10^18 -> "8.00E", from 10^21 on: "1.2345E21" */
    public static String compact(BigInteger value) {
        String digits = value.toString();
        if (digits.length() <= 4) {
            return digits;
        }
        int unit = (digits.length() - 1) / 3 - 1;
        if (unit >= UNITS.length) {
            return scientific(value);
        }
        BigDecimal scaled = new BigDecimal(value).movePointLeft((unit + 1) * 3);
        int decimals = scaled.compareTo(BigDecimal.valueOf(100)) >= 0 ? 0 : scaled.compareTo(BigDecimal.TEN) >= 0 ? 1 : 2;
        return scaled.setScale(decimals, RoundingMode.DOWN).toPlainString() + UNITS[unit];
    }

    /** 123456789 -> "1.2345E8" */
    public static String scientific(BigInteger value) {
        String digits = value.toString();
        if (digits.length() < 6) {
            return digits;
        }
        return digits.charAt(0) + "." + digits.substring(1, 5) + "E" + (digits.length() - 1);
    }

    private EnergyFormat() {
    }
}
