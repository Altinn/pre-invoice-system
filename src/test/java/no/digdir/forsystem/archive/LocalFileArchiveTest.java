package no.digdir.forsystem.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileArchiveTest {

    @TempDir Path katalog;

    @Test
    void deletesAnArchivedFileAndToleratesAMissingOne() {
        var arkiv = new LocalFileArchive(katalog.resolve("arkiv").toString());
        var lagret = arkiv.lagre("kjoring-1/lg04.txt", new byte[] {1, 2, 3});

        arkiv.slett(lagret.blobUrl());
        assertThat(Path.of(lagret.blobUrl())).doesNotExist();
        arkiv.slett(lagret.blobUrl());
    }

    @Test
    void refusesToDeleteOutsideTheArchive() throws IOException {
        var arkiv = new LocalFileArchive(katalog.resolve("arkiv").toString());
        Path utenfor = Files.writeString(katalog.resolve("annet.txt"), "behold");

        assertThatThrownBy(() -> arkiv.slett(utenfor.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThat(utenfor).exists();
    }
}
