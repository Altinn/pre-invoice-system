package no.digdir.forsystem.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * CSV and XLSX exports of a run's invoice lines. Generic string-matrix input (header + rows) so the
 * column semantics live in {@link EksportService}.
 */
@Component
public class RegnearkEksport {

    public byte[] csv(List<String> header, List<List<String>> rader) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", header.stream().map(RegnearkEksport::siter).toList())).append('\n');
        for (List<String> rad : rader) {
            sb.append(String.join(",", rad.stream().map(RegnearkEksport::siter).toList())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx(String ark, List<String> header, List<List<String>> rader) {
        try (var wb = new XSSFWorkbook(); var ut = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet(ark);
            var hodeRad = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                hodeRad.createCell(i).setCellValue(header.get(i));
            }
            for (int r = 0; r < rader.size(); r++) {
                var rad = sheet.createRow(r + 1);
                List<String> verdier = rader.get(r);
                for (int c = 0; c < verdier.size(); c++) {
                    rad.createCell(c).setCellValue(verdier.get(c) == null ? "" : verdier.get(c));
                }
            }
            wb.write(ut);
            return ut.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke bygge XLSX", e);
        }
    }

    private static String siter(String v) {
        String s = v == null ? "" : v;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
