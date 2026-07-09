package com.portalcomunitario.msauth.certificado;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class PdfCertificadoGenerator {

    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CL"));
    private static final Color AZUL = new Color(0, 48, 135);
    private static final Color GRIS = new Color(90, 90, 90);
    private static final int TINTA = 0x1E3A8A;

    public byte[] generar(String nombre, String rut, String direccion,
                          String folio, String junta, String sede, String comuna, String motivo) {
        Document doc = new Document(PageSize.A4, 70, 70, 96, 70);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new BordePagina());
        doc.open();

        agregarLogo(doc);

        Font hJunta = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, AZUL);
        Font sub = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, GRIS);
        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, AZUL);
        Font cuerpo = FontFactory.getFont(FontFactory.HELVETICA, 12);
        Font firma = FontFactory.getFont(FontFactory.HELVETICA, 10.5f);
        Font pie = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8.5f, GRIS);

        Paragraph pJunta = new Paragraph(junta, hJunta);
        pJunta.setAlignment(Element.ALIGN_CENTER);
        pJunta.setSpacingBefore(6);
        doc.add(pJunta);
        Paragraph pSede = new Paragraph(sede + (comuna != null ? ", " + comuna : "") + ", Región Metropolitana", sub);
        pSede.setAlignment(Element.ALIGN_CENTER);
        doc.add(pSede);

        doc.add(regla());

        Paragraph pTit = new Paragraph("CERTIFICADO DE RESIDENCIA", titulo);
        pTit.setAlignment(Element.ALIGN_CENTER);
        pTit.setSpacingBefore(14);
        doc.add(pTit);
        Paragraph pFolio = new Paragraph("N° " + folio, sub);
        pFolio.setAlignment(Element.ALIGN_CENTER);
        pFolio.setSpacingAfter(24);
        doc.add(pFolio);

        String texto = "Quien suscribe, en representación de la " + junta + ", certifica que " +
                (nombre != null ? nombre : "el/la vecino/a") +
                (rut != null && !rut.isBlank() ? ", cédula de identidad N° " + rut + "," : "") +
                " reside actualmente en " + (direccion != null && !direccion.isBlank() ? direccion : "el domicilio informado") +
                ", comuna de " + (comuna != null ? comuna : "la comuna") + ", Región Metropolitana.";
        Paragraph pCuerpo = new Paragraph(texto, cuerpo);
        pCuerpo.setAlignment(Element.ALIGN_JUSTIFIED);
        pCuerpo.setLeading(20);
        pCuerpo.setSpacingAfter(14);
        doc.add(pCuerpo);

        if (motivo != null && !motivo.isBlank()) {
            Paragraph pMotivo = new Paragraph("Se extiende el presente certificado para ser presentado en: " + motivo + ".", cuerpo);
            pMotivo.setAlignment(Element.ALIGN_JUSTIFIED);
            pMotivo.setLeading(20);
            doc.add(pMotivo);
        }

        Paragraph fechaEmision = new Paragraph((comuna != null ? comuna : "") + ", " + LocalDate.now().format(FECHA) + ".", cuerpo);
        fechaEmision.setAlignment(Element.ALIGN_RIGHT);
        fechaEmision.setSpacingBefore(18);
        doc.add(fechaEmision);

        LocalDate vence = LocalDate.now().plusDays(SolicitudCertificado.VALIDEZ_DIAS);
        Paragraph vigencia = new Paragraph(
                "Válido por " + SolicitudCertificado.VALIDEZ_DIAS + " días. Vence el " + vence.format(FECHA) + ".", sub);
        vigencia.setAlignment(Element.ALIGN_RIGHT);
        doc.add(vigencia);

        PdfPTable firmas = new PdfPTable(2);
        firmas.setWidthPercentage(90);
        firmas.setSpacingBefore(46);
        firmas.addCell(celdaFirmaImg("/logos/firma1.png"));
        firmas.addCell(celdaFirmaImg("/logos/firma2.png"));
        firmas.addCell(celdaRol("Presidente/a Junta de Vecinos", firma));
        firmas.addCell(celdaRol("Secretario/a Junta de Vecinos", firma));
        doc.add(firmas);

        Paragraph nota = new Paragraph(
                "Documento generado por el Portal Comunitario. Verificable con el N° de folio ante la junta de vecinos.", pie);
        nota.setAlignment(Element.ALIGN_CENTER);
        nota.setSpacingBefore(34);
        doc.add(nota);

        doc.close();
        return out.toByteArray();
    }

    private Paragraph regla() {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(8);
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(new Color(210, 216, 228));
        c.setBorderWidthBottom(1f);
        c.setFixedHeight(2f);
        line.addCell(c);
        p.add(line);
        return p;
    }

    private void agregarLogo(Document doc) {
        try (InputStream is = getClass().getResourceAsStream("/logos/maipu.png")) {
            if (is != null) {
                Image logo = Image.getInstance(is.readAllBytes());
                logo.scaleToFit(85, 85);
                logo.setAlignment(Image.ALIGN_CENTER);
                doc.add(logo);
            }
        } catch (Exception ignore) { /* sin logo */ }
    }

    private PdfPCell celdaFirmaImg(String recurso) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setFixedHeight(56);
        c.setVerticalAlignment(Element.ALIGN_BOTTOM);
        try (InputStream is = getClass().getResourceAsStream(recurso)) {
            if (is != null) {
                Image f = Image.getInstance(tintarAzul(is.readAllBytes()));
                f.scaleToFit(150, 48);
                f.setAlignment(Image.ALIGN_CENTER);
                c.addElement(f);
            }
        } catch (Exception ignore) { /* sin firma */ }
        return c;
    }

    private PdfPCell celdaRol(String texto, Font font) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.TOP);
        c.setBorderColorTop(new Color(60, 60, 60));
        c.setBorderWidthTop(0.8f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setPaddingTop(6);
        Paragraph p = new Paragraph(texto, font);
        p.setAlignment(Element.ALIGN_CENTER);
        c.addElement(p);
        return c;
    }

    private byte[] tintarAzul(byte[] png) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) return png;
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a0 = (argb >>> 24) & 0xff;
                int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
                int lum = (r + g + b) / 3;
                int alpha = (a0 * (255 - lum)) / 255; // oscuro => más tinta; blanco/transparente => nada
                dst.setRGB(x, y, (alpha << 24) | TINTA);
            }
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(dst, "png", o);
        return o.toByteArray();
    }

    static class BordePagina extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            PdfContentByte cb = writer.getDirectContentUnder();
            Rectangle r = doc.getPageSize();
            cb.setColorStroke(AZUL);
            cb.setLineWidth(2f);
            cb.roundRectangle(30, 30, r.getWidth() - 60, r.getHeight() - 60, 10);
            cb.stroke();
            cb.setLineWidth(0.5f);
            cb.roundRectangle(35, 35, r.getWidth() - 70, r.getHeight() - 70, 8);
            cb.stroke();
        }
    }
}
