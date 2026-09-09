package no.digdir.forsystem.archive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local-filesystem {@link FileArchive} — the default everywhere the app runs offline (docker
 * compose, dev, test). Base directory from {@code forsystem.arkiv.katalog} (default {@code ./data/arkiv}).
 * Contains no Azure code; a blob adapter would be a separate class in this package.
 */
@Component
class LocalFileArchive implements FileArchive {

    private final Path base;

    LocalFileArchive(@Value("${forsystem.arkiv.katalog:./data/arkiv}") String katalog) {
        this.base = Path.of(katalog).toAbsolutePath().normalize();
    }

    @Override
    public Arkivert lagre(String relativSti, byte[] innhold) {
        try {
            Path mål = base.resolve(relativSti).normalize();
            if (!mål.startsWith(base)) {
                throw new IllegalArgumentException("Ugyldig sti utenfor arkivet: " + relativSti);
            }
            Files.createDirectories(mål.getParent());
            Files.write(mål, innhold);
            return new Arkivert(mål.toString(), sha256(innhold));
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke arkivere " + relativSti, e);
        }
    }

    @Override
    public byte[] hent(String blobUrl) {
        try {
            return Files.readAllBytes(Path.of(blobUrl));
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke lese arkivert fil " + blobUrl, e);
        }
    }

    private static String sha256(byte[] innhold) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(innhold));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
