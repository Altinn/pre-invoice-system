package no.digdir.forsystem.usage;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Frozen comparison of one complete candidate snapshot against one exported run baseline. */
@Table("bruksdata_sammenligning")
public record BruksdataSammenligning(
        @Id Long id,
        LocalDate periode,
        Long kandidatImportId,
        Long grunnlagKjoringId,
        String status,
        Integer uendredeRader,
        String opprettetAv,
        OffsetDateTime opprettetAt,
        Long erstattetAvId) {

    public BruksdataSammenligning medStatus(String nyStatus, Long nyErstattetAvId) {
        return new BruksdataSammenligning(id, periode, kandidatImportId, grunnlagKjoringId,
                nyStatus, uendredeRader, opprettetAv, opprettetAt, nyErstattetAvId);
    }
}
