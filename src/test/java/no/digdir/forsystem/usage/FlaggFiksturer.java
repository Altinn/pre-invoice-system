package no.digdir.forsystem.usage;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The reduced flagged export (specs/017, {@code /dwh/eksport-flagg-3m.csv}) plus a deliberately naive
 * reader of it, so expected figures in the tests are computed independently of {@link DwhCsv}.
 */
final class FlaggFiksturer {

    static final String FIL = "/dwh/eksport-flagg-3m.csv";

    private FlaggFiksturer() {
    }

    static byte[] bytes() throws IOException {
        try (InputStream in = FlaggFiksturer.class.getResourceAsStream(FIL)) {
            return in.readAllBytes();
        }
    }

    /** Rows as column → raw text; quotes stripped, {@code NULL} kept as the text "NULL". */
    static List<Map<String, String>> rader() throws IOException {
        String[] linjer = new String(bytes(), StandardCharsets.UTF_8).split("\n");
        String[] hode = felt(linjer[0]);
        List<Map<String, String>> ut = new ArrayList<>();
        for (int i = 1; i < linjer.length; i++) {
            if (linjer[i].isBlank()) {
                continue;
            }
            String[] f = felt(linjer[i]);
            Map<String, String> r = new java.util.LinkedHashMap<>();
            for (int k = 0; k < hode.length; k++) {
                r.put(hode[k], f[k]);
            }
            ut.add(r);
        }
        return ut;
    }

    /** The fixture has no commas inside quoted fields, so a plain split is enough. */
    private static String[] felt(String linje) {
        String[] d = linje.split(",", -1);
        for (int i = 0; i < d.length; i++) {
            d[i] = d[i].replace("\"", "");
        }
        return d;
    }

    static boolean flagget(Map<String, String> r) {
        return "1".equals(r.get("flag_inv_altinn"));
    }

    static BigDecimal tall(String v) {
        return "NULL".equals(v) || v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v);
    }
}
