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

    private static final String HODE = "\"organisation_number\",\"fakturareferanse\"\n";

    @Test
    void utf8WithNorwegianLettersIsAccepted() {
        assertThat(Bruksdatafil.gjenkjenn(fil(HODE + "\"974760746\",\"Deres ref: Morten Møllers\"\n")))
                .isEqualTo(Bruksdatafil.Format.DATAVAREHUS);
        assertThat(Bruksdatafil.utf8(fil("Deres ref: Morten Møllers"))).hasSize(25);
    }

    @Test
    void fileSavedAsAnsiIsRefusedWithTheLine() {
        byte[] ansi = (HODE + "\"974760746\",\"Deres ref: Morten Møllers\"\n")
                .getBytes(java.nio.charset.Charset.forName("windows-1252"));
        assertThatThrownBy(() -> Bruksdatafil.gjenkjenn(ansi))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageStartingWith("Fila er ikke lagret som UTF-8 (linje 2)")
                .hasMessageContaining("CSV UTF-8");
    }

    @Test
    void lettersConvertedWronglyOnceAreRefused() {
        // UTF-8 read as Windows-1252 and saved again as UTF-8: «ø» becomes «Ã¸», 25 characters become 26.
        String dobbel = new String(fil("Deres ref: Morten Møllers"), java.nio.charset.Charset.forName("windows-1252"));
        assertThatThrownBy(() -> Bruksdatafil.gjenkjenn(fil(HODE + "\"974760746\",\"" + dobbel + "\"\n")))
                .isInstanceOf(Regelbrudd.class)
                .hasMessageStartingWith("Fila har æ, ø eller å som er konvertert feil («Ã¸», linje 2)");
        for (String bokstav : new String[]{"æ", "å", "Æ", "Ø", "Å", "é"}) {
            String feil = new String(fil(bokstav), java.nio.charset.Charset.forName("windows-1252"));
            assertThatThrownBy(() -> Bruksdatafil.utf8(fil(feil))).as(bokstav).isInstanceOf(Regelbrudd.class);
        }
    }
}
