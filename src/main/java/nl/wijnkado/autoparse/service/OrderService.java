package nl.wijnkado.autoparse.service;

import nl.wijnkado.autoparse.dto.OrderDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.ArrayList;


@Service
public class OrderService {

    private final RestTemplate restTemplate;

    @Value("${woocommerce.base-url}")
    private String baseUrl;

    @Value("${woocommerce.consumer-key}")
    private String consumerKey;

    @Value("${woocommerce.consumer-secret}")
    private String consumerSecret;

    public OrderService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Haal alle 'processing' orders op als DTO's.
     */
 public List<OrderDto> getProcessingOrders() {
    if (baseUrl == null || baseUrl.isBlank()) {
        throw new IllegalStateException("Config error: woocommerce.base-url not set");
    }

    int page = 1;
    int pageSize = 100; 
    List<OrderDto> allOrders = new ArrayList<>();

    try {
        while (true) {
            URI uri = UriComponentsBuilder
                    .fromUriString(baseUrl + "/orders")
                    .queryParam("status", "processing")
                    .queryParam("per_page", pageSize)
                    .queryParam("page", page)
                    .queryParam("consumer_key", consumerKey)
                    .queryParam("consumer_secret", consumerSecret)
                    .build(true)
                    .toUri();

            OrderDto[] response = restTemplate.getForObject(uri, OrderDto[].class);

            if (response == null || response.length == 0) {
                // geen resultaten meer -> klaar
                break;
            }

            allOrders.addAll(Arrays.asList(response));

            // Als er minder dan pageSize terugkomt, is dit de laatste pagina
            if (response.length < pageSize) {
                break;
            }

            page++;
        }

        // Handige debug om te checken of 41911 er nu tussen zit
        System.out.println("Totaal processing orders uit Woo: " + allOrders.size());
        System.out.println("IDs: " + allOrders.stream().map(OrderDto::getId).toList());

        return allOrders;
    } catch (HttpStatusCodeException e) {
        throw new RuntimeException("WooCommerce API error: " + e.getStatusCode()
                + " - " + e.getResponseBodyAsString(), e);
    } catch (Exception e) {
        throw new RuntimeException("Unexpected error calling WooCommerce: "
                + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
    }
}


    /**
     * Filter de orders waar nog GEEN track & trace op zit.
     * We kijken naar meta_data key '_myparcel_shipments'.
     */
public List<OrderDto> getProcessingOrdersWithoutTrackTrace() {
    List<OrderDto> allProcessing = getProcessingOrders(); // jouw bestaande call

    return allProcessing.stream()
            // status moet 'processing' zijn
            .filter(o -> "processing".equalsIgnoreCase(o.getStatus()))
            // en géén echte track & trace
            .filter(this::hasNoRealTrackTrace)
            .toList();
}

private boolean hasNoRealTrackTrace(OrderDto order) {
    if (order.getMetaData() == null) {
        // geen metadata = sowieso geen T&T
        return true;
    }

    // Zoek alle _myparcel_shipments entries
    return order.getMetaData().stream()
            .filter(m -> "_myparcel_shipments".equals(m.getKey()))
            .map(m -> String.valueOf(m.getValue()))  // value is JSON-string
            // als er ergens een shipment is met een niet-lege barcode => dan heeft order T&T
            .noneMatch(this::shipmentHasRealBarcode);
}

/**
 * Checkt op basis van de ruwe JSON-string of er een niet-lege barcode in zit.
 * Voorbeelden:
 *  - "{\"...\"barcode\":\"3SXDXU030710702\"...}"  -> true  (heeft T&T)
 *  - "{\"...\"barcode\":\"\"...}"                -> false (nog geen T&T)
 *  - geen "barcode" key                           -> false (nog geen T&T)
 */
private boolean shipmentHasRealBarcode(String shipmentJson) {
    if (shipmentJson == null || shipmentJson.isBlank()) {
        return false;
    }

    String marker = "\"barcode\":\"";
    int idx = shipmentJson.indexOf(marker);
    if (idx == -1) {
        // helemaal geen barcode veld
        return false;
    }

    int start = idx + marker.length();
    int end = shipmentJson.indexOf("\"", start);
    if (end == -1) {
        // kapotte / incomplete JSON? doe maar alsof geen T&T
        return false;
    }

    String code = shipmentJson.substring(start, end).trim();
    return !code.isEmpty();
}


// private boolean hasTrackAndTrace(OrderDto order) {
//     if (order.getMetaData() == null) {
//         return false;
//     }

//     // We zoeken expliciet naar ECHTE T&T gegevens
//     return order.getMetaData().stream()
//             .filter(md -> "_myparcel_shipments".equals(md.getKey()))
//             .map(MetaData::getValue)
//             .filter(Objects::nonNull)
//             .map(Object::toString)
//             .anyMatch(v ->
//                     v.contains("track_trace") ||
//                     v.contains("barcode")
//             );
// }

}
