package no.digdir.forsystem.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates UUID version 7 (time-ordered) values — used as the stable {@code faktura_uuid} that
 * also names the PDF detail file (docs/03). Time-ordering keeps database index locality good.
 * Java 21 has no built-in v7, so this implements the layout directly.
 */
public final class UuidV7 {

    private static final SecureRandom RNG = new SecureRandom();

    private UuidV7() {
    }

    public static UUID naa() {
        long tsMs = System.currentTimeMillis() & 0xFFFFFFFFFFFFL; // 48 bits
        byte[] r = new byte[10];
        RNG.nextBytes(r);

        long rand12 = ((r[0] & 0x0FL) << 8) | (r[1] & 0xFFL);
        long msb = (tsMs << 16) | (0x7L << 12) | rand12; // version 7 in the top nibble of rand_a

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (r[i] & 0xFFL);
        }
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // RFC 4122 variant (10xx)

        return new UUID(msb, lsb);
    }
}
