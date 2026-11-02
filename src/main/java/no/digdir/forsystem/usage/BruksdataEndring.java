package no.digdir.forsystem.usage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** One non-zero changed usage key in a frozen comparison. */
@Table("bruksdata_endring")
public record BruksdataEndring(
        @Id Long id,
        Long sammenligningId,
        String organisasjonsnummer,
        Long produktId,
        String type,
        String endringstype,
        Long gammelBruksdataId,
        Long nyBruksdataId,
        BigDecimal gammeltAntall,
        BigDecimal nyttAntall,
        BigDecimal deltaAntall,
        BigDecimal gammeltBelop,
        BigDecimal nyttBelop,
        BigDecimal deltaBelop,
        String avvistAv,
        OffsetDateTime avvistAt,
        String avvistAarsak) {

    public static BruksdataEndring fra(Long sammenligningId, BruksdataDifferanse.Endring endring) {
        return new BruksdataEndring(null, sammenligningId,
                endring.nokkel().organisasjonsnummer(), endring.nokkel().produktId(), endring.nokkel().type(),
                endring.endringstype().name(), endring.gammelBruksdataId(), endring.nyBruksdataId(),
                endring.gammeltAntall(), endring.nyttAntall(), endring.deltaAntall(),
                endring.gammeltBelop(), endring.nyttBelop(), endring.deltaBelop(), null, null, null);
    }

    public BigDecimal gammelVerdi() {
        return Bruksdatatype.valueOf(type).erVolum() ? gammeltAntall : gammeltBelop;
    }

    public BigDecimal nyVerdi() {
        return Bruksdatatype.valueOf(type).erVolum() ? nyttAntall : nyttBelop;
    }

    public BigDecimal delta() {
        return Bruksdatatype.valueOf(type).erVolum() ? deltaAntall : deltaBelop;
    }
}
