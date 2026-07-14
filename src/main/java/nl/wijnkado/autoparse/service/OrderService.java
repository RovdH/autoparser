package nl.wijnkado.autoparse.service;

import nl.wijnkado.autoparse.dto.OrderDto;
import nl.wijnkado.autoparse.dto.OrderCompletionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;


@Service
public class OrderService {

    private final RestTemplate restTemplate;

    @Value("${woocommerce.base-url}")
    private String baseUrl;

    @Value("${woocommerce.consumer-key}")
    private String consumerKey;

    @Value("${woocommerce.consumer-secret}")
    private String consumerSecret;

    /**
     * De order-meta key waarin de actieve bezorgplugin de bezorgdatum opslaat.
     * Overschrijf deze waarde in application.yml als de plugin een andere key gebruikt.
     */
    @Value("${woocommerce.delivery-date-meta-key:_wkdo_delivery_date}")
    private String deliveryDateMetaKey;

    private static final List<DateTimeFormatter> DELIVERY_DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu")
    );

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
     * Processing orders met een bezorgdatum binnen de inclusieve range.
     */
    public List<OrderDto> getProcessingOrdersByDeliveryDate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Bezorgdatum van en tot zijn verplicht.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("De begindatum mag niet na de einddatum liggen.");
        }

        return getProcessingOrders().stream()
                .filter(order -> "processing".equalsIgnoreCase(order.getStatus()))
                .filter(order -> getDeliveryDate(order)
                        .map(date -> !date.isBefore(from) && !date.isAfter(to))
                        .orElse(false))
                .toList();
    }

    public Optional<LocalDate> getDeliveryDate(OrderDto order) {
        if (order == null || order.getMetaData() == null) {
            return Optional.empty();
        }

        Set<String> acceptedKeys = new HashSet<>(Set.of(
                "delivery_date",
                "_delivery_date",
                "_wkdo_delivery_date",
                "_wkdo_delivery_date_display",
                "bezorgdatum"
        ));
        acceptedKeys.add(deliveryDateMetaKey.trim().toLowerCase());

        return order.getMetaData().stream()
                .filter(meta -> meta.getKey() != null
                        && acceptedKeys.contains(meta.getKey().trim().toLowerCase()))
                .map(OrderDto.MetaData::getValue)
                .filter(value -> value != null)
                .map(Object::toString)
                .map(this::parseDeliveryDate)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Zet alle nog processing orders voor één bezorgdatum op completed.
     * Fouten worden per order teruggegeven, zodat een gedeeltelijk resultaat zichtbaar blijft.
     */
    public OrderCompletionResult completeProcessingOrdersByDeliveryDate(LocalDate deliveryDate) {
        if (deliveryDate == null) {
            throw new IllegalArgumentException("Bezorgdatum is verplicht.");
        }

        List<OrderDto> orders = getProcessingOrdersByDeliveryDate(deliveryDate, deliveryDate);
        List<Long> completedOrderIds = new ArrayList<>();
        List<OrderCompletionResult.OrderFailure> failures = new ArrayList<>();

        for (OrderDto order : orders) {
            if (order.getId() == null) {
                failures.add(new OrderCompletionResult.OrderFailure(null,
                        "Order zonder geldig ordernummer overgeslagen."));
                continue;
            }

            try {
                markOrderCompleted(order.getId());
                completedOrderIds.add(order.getId());
            } catch (HttpStatusCodeException e) {
                failures.add(new OrderCompletionResult.OrderFailure(order.getId(),
                        "WooCommerce gaf " + e.getStatusCode() + " terug."));
            } catch (Exception e) {
                failures.add(new OrderCompletionResult.OrderFailure(order.getId(),
                        "Status kon niet worden bijgewerkt: " + e.getMessage()));
            }
        }

        return new OrderCompletionResult(
                deliveryDate,
                orders.size(),
                completedOrderIds.size(),
                List.copyOf(completedOrderIds),
                List.copyOf(failures)
        );
    }

    private void markOrderCompleted(Long orderId) {
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/orders/" + orderId)
                .queryParam("consumer_key", consumerKey)
                .queryParam("consumer_secret", consumerSecret)
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("status", "completed"), headers);

        restTemplate.exchange(uri, HttpMethod.PUT, request, Void.class);
    }

    private Optional<LocalDate> parseDeliveryDate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        String value = rawValue.trim();
        // Ook waarden als 2026-07-15T10:00:00 en "2026-07-15" accepteren.
        if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
            value = value.substring(0, 10);
        }
        value = value.replace("\"", "").trim();

        for (DateTimeFormatter formatter : DELIVERY_DATE_FORMATS) {
            try {
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (DateTimeParseException ignored) {
                // Probeer het volgende bekende formaat.
            }
        }
        return Optional.empty();
    }

}
