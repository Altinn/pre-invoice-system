package no.digdir.forsystem.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import no.digdir.forsystem.common.Regelbrudd;
import org.junit.jupiter.api.Test;

/** One upload field takes both usage files; the header row decides which pipeline reads it. */
class BruksdatafilTest {

    private static byte[] fil(String tekst) {
        return tekst.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void forsystemsOwnFormat() {
        assertThat(Bruksdatafil.gjenkjenn(fil("﻿periode,organisasjonsnummer,produktkode,type,antall,belop\n")))
                .isEqualTo(Bruksdatafil.Format.FORSYSTEM);
    }

    @Test
    void datavarehusExportInEitherLayout() {
        assertThat(Bruksdatafil.gjenkjenn(fil("\"maaned\",\"organisation_number\",\"product_name\"\n")))
                .isEqualTo(Bruksdatafil.Format.DATAVAREHUS);
        assertThat(Bruksdatafil.gjenkjenn(fil("\"organisation_number\",\"transaction_month\"\n")))
                .isEqualTo(Bruksdatafil.Format.DATAVAREHUS);
    }

    @Test
    void unknownFormatListsTheColumnsFoundAndBothExpectedFormats() {
        assertThatThrownBy(() -> Bruksdatafil.gjenkjenn(fil("orgnr,produkt,mengde\n1,2,3\n")))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("Kolonner i fila: orgnr, produkt, mengde.")
                .hasMessageContaining("periode, organisasjonsnummer")
                .hasMessageContaining("organisation_number, product_name")
                .hasMessageContaining("transaction_month");
    }

    @Test
    void semicolonFileSavedByExcelGetsAHint() {
        assertThatThrownBy(() -> Bruksdatafil.gjenkjenn(fil("maaned;organisation_number;product_name\n")))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageStartingWith("Fila er delt med semikolon");
    }

    @Test
    void excelWorkbookIsRefused() {
        assertThatThrownBy(() -> Bruksdatafil.gjenkjenn(new byte[]{'P', 'K', 3, 4, 0, 0}))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageContaining("Excel-regneark");
    }

    @Test
    void emptyFile() {
        assertThatThrownBy(() -> Bruksdatafil.gjenkjenn(fil("\n\n")))
                .isInstanceOf(Regelbrudd.class)
                .hasMessage("Fila er tom");
    }
}
