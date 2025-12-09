package nl.wijnkado.autoparse.service;

import nl.wijnkado.autoparse.dto.OrderDto;
import nl.wijnkado.autoparse.dto.OrderDto.LineItem;
import nl.wijnkado.autoparse.dto.ProductDto;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Service
public class DocumentService {

    private final OrderService orderService;
    private final ProductService productService;

    public DocumentService(OrderService orderService, ProductService productService) {
        this.orderService = orderService;
        this.productService = productService;
    }

    public Path generateOrdersDocument() throws IOException {
        List<OrderDto> orders = orderService.getProcessingOrdersWithoutTrackTrace();

        Collections.reverse(orders);

        if (orders.isEmpty()) {
            throw new IllegalStateException("Geen orders zonder track & trace gevonden.");
        }

        XWPFDocument document = new XWPFDocument();

        for (int i = 0; i < orders.size(); i++) {
            OrderDto order = orders.get(i);

            // --- 1) Witregel boven customer note ---
            addEmptyParagraph(document, ParagraphAlignment.CENTER);

            // --- 2) Customer note gecentreerd + HTML entities unescapen + echte enters ---
            String rawNote = order.getCustomerNote() != null ? order.getCustomerNote() : "";

            // HTML entities (&amp;, &eacute;, &quot;, etc.) decoderen
            String note = StringEscapeUtils.unescapeHtml4(rawNote);
            // non-breaking spaces naar normale spaties
            note = note.replace('\u00A0', ' ').trim();

            XWPFParagraph noteParagraph = document.createParagraph();
            noteParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun noteRun = noteParagraph.createRun();
            noteRun.setBold(true);

            // elke regel apart in Word met echte breaks
            String[] noteLines = note.split("\\r?\\n");
            for (int li = 0; li < noteLines.length; li++) {
                if (li > 0) {
                    noteRun.addBreak();
                }
                noteRun.setText(noteLines[li]);
            }

            // --- 3) Witregel onder customer note ---
            addEmptyParagraph(document, ParagraphAlignment.CENTER);

            // --- 4) Scheidingslijn tussen customer note en wijnblok ---
            addSeparatorLine(document);

            // --- 5) Extra witregel onder de lijn ---
            addEmptyParagraph(document, ParagraphAlignment.CENTER);

            // --- 6) Product title (line_items[0].name) ---
            String productTitle = "";
            Long productId = null;

            if (order.getLineItems() != null && !order.getLineItems().isEmpty()) {
                LineItem item = order.getLineItems().get(0);
                productTitle = item.getName() != null ? item.getName() : "";
                productId = item.getProductId();
            }

            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setText(productTitle);

            // --- 7) Productbeschrijving via Woo API ---
            String productDescription = "";
            if (productId != null) {
                ProductDto product = productService.getProductById(productId);
                if (product != null) {
                    productDescription = cleanHtml(product.getBestDescription());
                }
            }

            if (productDescription != null && !productDescription.isBlank()) {
                XWPFParagraph descParagraph = document.createParagraph();
                descParagraph.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun descRun = descParagraph.createRun();
                descRun.setText(productDescription);
            }

            // --- 8) Witregel onder de beschrijving ---
            addEmptyParagraph(document, ParagraphAlignment.CENTER);

            // --- 9) Ordernummer in klein, lichtgrijs font onderaan dit blok ---
            XWPFParagraph orderInfoParagraph = document.createParagraph();
            orderInfoParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun orderRun = orderInfoParagraph.createRun();
            orderRun.setText("Order: " + order.getId());
            orderRun.setFontSize(8);        // klein font
            orderRun.setColor("888888");    // lichtgrijs (hex)

            // Pagina-einde na elke order, behalve de laatste
            if (i < orders.size() - 1) {
                XWPFParagraph pageBreak = document.createParagraph();
                pageBreak.setPageBreak(true);
            }
        }

        // Output pad, bv. ./output/orders_2025-11-25.docx
        Path outputDir = Paths.get("output");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("orders_" + LocalDate.now() + ".docx");

        try (OutputStream os = Files.newOutputStream(outputFile)) {
            document.write(os);
        }
        document.close();

        return outputFile;
    }

    private void addEmptyParagraph(XWPFDocument doc, ParagraphAlignment alignment) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(alignment);
        XWPFRun r = p.createRun();
        r.setText("");
    }

    /**
     * Scheidingslijn over de volle breedte van het tekstvlak.
     */
    private void addSeparatorLine(XWPFDocument doc) {
        XWPFParagraph separator = doc.createParagraph();
        separator.setAlignment(ParagraphAlignment.CENTER);

        CTP ctp = separator.getCTP();
        CTPPr pr = ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
        CTPBdr borders = pr.isSetPBdr() ? pr.getPBdr() : pr.addNewPBdr();

        CTBorder bottom = borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom();
        bottom.setVal(STBorder.SINGLE);              // normale lijn
        bottom.setSz(BigInteger.valueOf(8));         // lijndikte
        bottom.setSpace(BigInteger.ZERO);            // geen extra ruimte
        bottom.setColor("000000");                   // zwart
    }

    /**
     * HTML-stripper + entity decode (&nbsp;, &amp;, &eacute; etc.).
     */
private String cleanHtml(String html) {
    if (html == null) {
        return "";
    }

    // 1) Zet HTML line breaks om naar \n
    String text = html
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>", "\n");

    // 2) Strip overige HTML
    text = text.replaceAll("<[^>]+>", "");

    // 3) Decodeer HTML entities (zoals &amp; &eacute; &quot;)
    text = StringEscapeUtils.unescapeHtml4(text);

    // 4) Non-breaking spaces naar normale spaties
    text = text.replace('\u00A0', ' ');

    return text.trim();
}

}
