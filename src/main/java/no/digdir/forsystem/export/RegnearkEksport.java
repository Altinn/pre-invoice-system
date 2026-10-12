package no.digdir.forsystem.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * CSV and XLSX of a run's invoice lines. Generic typed-matrix input (header + rows) so the column
 * semantics live in {@link FakturagrunnlagRegneark}. Numbers become numeric XLSX cells so Excel can sum
 * them (specs/002 D8); CSV stays comma-separated with {@code .} decimals for machines.
 */
@Component
public class RegnearkEksport {

    public byte[] csv(List<String> header, List<List<Object>> rader) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", header.stream().map(RegnearkEksport::siter).toList())).append('\n');
        for (List<Object> rad : rader) {
            sb.append(String.join(",", rad.stream().map(RegnearkEksport::tekst).map(RegnearkEksport::siter).toList()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] xlsx(String ark, List<String> header, List<List<Object>> rader) {
        try (var wb = new XSSFWorkbook(); var ut = new ByteArrayOutputStream()) {
            CellStyle heltall = stil(wb, "0");
            CellStyle toDesimaler = stil(wb, "#,##0.00");
            CellStyle fireDesimaler = stil(wb, "#,##0.0000");
            var sheet = wb.createSheet(ark);
            var hodeRad = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                hodeRad.createCell(i).setCellValue(header.get(i));
            }
            for (int r = 0; r < rader.size(); r++) {
                var rad = sheet.createRow(r + 1);
                List<Object> verdier = rader.get(r);
                for (int c = 0; c < verdier.size(); c++) {
                    Object v = verdier.get(c);
                    var celle = rad.createCell(c);
                    if (v instanceof BigDecimal b) {
                        celle.setCellValue(b.doubleValue());
                        celle.setCellStyle(b.scale() > 2 ? fireDesimaler : toDesimaler);
                    } else if (v instanceof Number n) {
                        celle.setCellValue(n.doubleValue());
                        celle.setCellStyle(heltall);
                    } else {
                        celle.setCellValue(v == null ? "" : v.toString());
                    }
                }
            }
            wb.write(ut);
            return ut.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke bygge XLSX", e);
        }
    }

    private static CellStyle stil(XSSFWorkbook wb, String format) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat(format));
        return s;
    }

    private static String tekst(Object v) {
        if (v == null) {
            return "";
        }
        return v instanceof BigDecimal b ? b.toPlainString() : v.toString();
    }

    private static String siter(String v) {
        String s = v == null ? "" : v;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
