package nl.wijnkado.autoparse.service;

import nl.wijnkado.autoparse.dto.OrderDto;
import nl.wijnkado.autoparse.dto.OrderCompletionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

class OrderServiceTest {

    private OrderService orderService;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        orderService = new OrderService(restTemplate);
        ReflectionTestUtils.setField(orderService, "deliveryDateMetaKey", "custom_delivery_day");
        ReflectionTestUtils.setField(orderService, "baseUrl", "https://example.test/wp-json/wc/v3");
        ReflectionTestUtils.setField(orderService, "consumerKey", "ck_test");
        ReflectionTestUtils.setField(orderService, "consumerSecret", "cs_test");
    }

    @Test
    void readsConfiguredDeliveryDateMetaKey() {
        OrderDto order = orderWithMeta("custom_delivery_day", "2026-07-15");

        assertEquals(LocalDate.of(2026, 7, 15), orderService.getDeliveryDate(order).orElseThrow());
    }

    @Test
    void acceptsDutchDateFormatAndKnownFallbackKey() {
        OrderDto order = orderWithMeta("Bezorgdatum", "15-07-2026");

        assertEquals(LocalDate.of(2026, 7, 15), orderService.getDeliveryDate(order).orElseThrow());
    }

    @Test
    void readsWkdoStorageMetaFromDeliveryPlugin() {
        OrderDto order = orderWithMeta("_wkdo_delivery_date", "2026-07-15");

        assertEquals(LocalDate.of(2026, 7, 15), orderService.getDeliveryDate(order).orElseThrow());
    }

    @Test
    void completesMatchingProcessingOrdersInWooCommerce() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://example.test/wp-json/wc/v3/orders"
                        + "?status=processing&per_page=100&page=1"
                        + "&consumer_key=ck_test&consumer_secret=cs_test"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{
                          "id": 421,
                          "status": "processing",
                          "meta_data": [{"key": "_wkdo_delivery_date", "value": "2026-07-15"}]
                        }]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.test/wp-json/wc/v3/orders/421"
                        + "?consumer_key=ck_test&consumer_secret=cs_test"))
                .andExpect(method(PUT))
                .andExpect(content().json("{\"status\":\"completed\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        OrderCompletionResult result = orderService.completeProcessingOrdersByDeliveryDate(
                LocalDate.of(2026, 7, 15));

        assertEquals(1, result.matched());
        assertEquals(1, result.completed());
        assertEquals(List.of(421L), result.completedOrderIds());
        assertTrue(result.failures().isEmpty());
        server.verify();
    }

    private OrderDto orderWithMeta(String key, Object value) {
        OrderDto.MetaData meta = new OrderDto.MetaData();
        meta.setKey(key);
        meta.setValue(value);
        OrderDto order = new OrderDto();
        order.setMetaData(List.of(meta));
        return order;
    }
}
