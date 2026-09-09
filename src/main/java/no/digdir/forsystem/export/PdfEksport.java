package no.digdir.forsystem.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

/**
 * Renders per-invoice detail views (XHTML → PDF via openhtmltopdf) and zips them. Each entry is named
 * {@code <kundenummer>-<faktura_uuid>.pdf} to match the reference PDF naming (docs/03, docs/06 §8).
 * The domain → XHTML mapping is done by {@link EksportService}; this component only renders and zips.
 */
@Component
public class PdfEksport {

    /** One invoice's PDF: the zip entry filename and its well-formed XHTML content. */
    public record FakturaPdf(String filnavn, String xhtml) {
    }

    public byte[] byggZip(List<FakturaPdf> fakturaer) {
        try (var ut = new ByteArrayOutputStream(); var zip = new ZipOutputStream(ut)) {
            for (FakturaPdf f : fakturaer) {
                zip.putNextEntry(new ZipEntry(f.filnavn()));
                zip.write(renderPdf(f.xhtml()));
                zip.closeEntry();
            }
            zip.finish();
            return ut.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke bygge PDF-zip", e);
        }
    }

    public byte[] renderPdf(String xhtml) {
        try (var os = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(xhtml, null)
                    .toStream(os)
                    .run();
            return os.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Kunne ikke rendre PDF", e);
        }
    }
}
