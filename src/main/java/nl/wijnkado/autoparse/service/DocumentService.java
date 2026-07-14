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
import java.util.Comparator;
import java.util.List;

@Service
public class DocumentService {

    private final OrderService orderService;
    private final ProductService productService;

    public DocumentService(OrderService orderService, ProductService productService) {
        this.orderService = orderService;
        this.productService = productService;
    }

    public Path generateOrdersDocument(LocalDate from, LocalDate to) throws IOException {
        // 1) Haal orders op en sorteer: oudste ID eerst
        List<OrderDto> orders = orderService.getProcessingOrdersByDeliveryDate(from, to)
                .stream()
                .sorted(Comparator.comparingLong(OrderDto::getId))
                .toList();

        if (orders.isEmpty()) {
            throw new IllegalStateException("Geen processing orders gevonden voor bezorgdatum "
                    + formatDateRange(from, to) + ".");
        }

        XWPFDocument document = new XWPFDocument();

        for (int i = 0; i < orders.size(); i++) {
            OrderDto order = orders.get(i);

            // Alle producten in de order afdrukken.
            List<LineItem> items = order.getLineItems() != null ? order.getLineItems() : List.of();

            for (int pi = 0; pi < items.size(); pi++) {
                LineItem item = items.get(pi);

                // Vanaf het 2e product: nieuwe pagina binnen dezelfde order
                if (pi > 0) {
                    XWPFParagraph pageBreak = document.createParagraph();
                    pageBreak.setPageBreak(true);

                }

                addPersonalMessageBlock(document, getPersonalMessage(item,
                        pi == 0 ? order.getCustomerNote() : null));

                // --- Product title ---
                String productTitle = "";
                Long productId = null;

                if (item != null) {
                    if (item.getName() != null) {
                        productTitle = StringEscapeUtils.unescapeHtml4(item.getName())
                                .replace('\u00A0', ' ')
                                .trim();
                    }
                    productId = item.getProductId();
                }

                XWPFParagraph titleParagraph = document.createParagraph();
                titleParagraph.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun titleRun = titleParagraph.createRun();
                titleRun.setBold(true);
                titleRun.setText(productTitle);

                // --- Productbeschrijving via Woo API ---
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
                    descRun.setFontSize(11);

                    // Respecteer ook nieuwe regels in de description
                    String[] descLines = productDescription.split("\\r?\\n");
                    for (int di = 0; di < descLines.length; di++) {
                        if (di > 0) {
                            descRun.addBreak();
                        }
                        descRun.setText(descLines[di]);
                    }
                }

                // --- Witregel onder de beschrijving ---
                addEmptyParagraph(document, ParagraphAlignment.CENTER);

                // --- Ordernummer in klein, lichtgrijs font onderaan dit productblok ---
                XWPFParagraph orderInfoParagraph = document.createParagraph();
                orderInfoParagraph.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun orderRun = orderInfoParagraph.createRun();
                orderRun.setText("Order: " + order.getId());
                orderRun.setFontSize(8);
                orderRun.setColor("888888");
            }

            // Pagina-einde na elke order, behalve de laatste order
            if (i < orders.size() - 1) {
                XWPFParagraph pageBreak = document.createParagraph();
                pageBreak.setPageBreak(true);
            }
        }

        // Output pad
        Path outputDir = Paths.get("output");
        Files.createDirectories(outputDir);
        String datePart = from.equals(to) ? from.toString() : from + "_tot_" + to;
        Path outputFile = outputDir.resolve("orders_bezorgdatum_" + datePart + ".docx");

        try (OutputStream os = Files.newOutputStream(outputFile)) {
            document.write(os);
        }
        document.close();

        return outputFile;
    }

    private String getPersonalMessage(LineItem item, String legacyCustomerNote) {
        if (item == null || item.getMetaData() == null) {
            return legacyCustomerNote != null ? legacyCustomerNote : "";
        }

        return item.getMetaData().stream()
                .filter(meta -> meta.getKey() != null
                        && "Persoonlijke boodschap".equalsIgnoreCase(meta.getKey().trim()))
                .map(OrderDto.MetaData::getValue)
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .findFirst()
                .orElse(legacyCustomerNote != null ? legacyCustomerNote : "");
    }

    private void addPersonalMessageBlock(XWPFDocument document, String rawMessage) {
        addEmptyParagraph(document, ParagraphAlignment.CENTER);

        String message = StringEscapeUtils.unescapeHtml4(rawMessage != null ? rawMessage : "")
                .replace('\u00A0', ' ')
                .trim();

        XWPFParagraph messageParagraph = document.createParagraph();
        messageParagraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun messageRun = messageParagraph.createRun();
        messageRun.setBold(true);

        String[] lines = message.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                messageRun.addBreak();
            }
            messageRun.setText(lines[i]);
        }

        addEmptyParagraph(document, ParagraphAlignment.CENTER);
        addSeparatorLine(document);
        addEmptyParagraph(document, ParagraphAlignment.CENTER);
    }

    private String formatDateRange(LocalDate from, LocalDate to) {
        return from.equals(to) ? from.toString() : from + " t/m " + to;
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
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(8));
        bottom.setSpace(BigInteger.ZERO);
        bottom.setColor("000000");
    }

    /**
     * HTML-stripper + entity decode (&nbsp;, &amp;, &eacute; etc.).
     */
    private String cleanHtml(String html) {
        if (html == null) {
            return "";
        }

        // 1) HTML line breaks → \n
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<p\\b[^>]*>", ""); // open <p> weg zodat je geen rare concatenatie krijgt

        // 2) overige tags weg
        text = text.replaceAll("<[^>]+>", "");

        // 3) HTML entities (&amp;, &nbsp;, &eacute;, &quot;)
        text = StringEscapeUtils.unescapeHtml4(text);

        // 4) non-breaking spaces (NBSP) → gewone spaties
        text = text.replace('\u00A0', ' ');

        return text.trim();
    }
}
