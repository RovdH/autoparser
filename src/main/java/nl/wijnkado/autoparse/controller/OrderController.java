package nl.wijnkado.autoparse.controller;

import nl.wijnkado.autoparse.dto.OrderDto;
import nl.wijnkado.autoparse.dto.OrderCompletionResult;
import nl.wijnkado.autoparse.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import java.time.ZoneId;

@RestController
public class OrderController {

    private static final ZoneId DELIVERY_TIME_ZONE = ZoneId.of("Europe/Amsterdam");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Optioneel: alle processing orders (handig voor debug)
     */
    @GetMapping("/orders/all")
    public List<OrderDto> getAllProcessingOrders() {
        return orderService.getProcessingOrders();
    }

    @GetMapping({"/orders", "/orders/by-delivery-date"})
    public List<OrderDto> getByDeliveryDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return orderService.getProcessingOrdersByDeliveryDate(from, to);
    }

    @GetMapping("/orders/summary")
    public OrderSelectionSummary getSelectionSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date != null ? date : tomorrow();
        List<Long> orderIds = orderService
                .getProcessingOrdersByDeliveryDate(selectedDate, selectedDate)
                .stream()
                .map(OrderDto::getId)
                .toList();

        return new OrderSelectionSummary(selectedDate, orderIds.size(), orderIds);
    }

    @PostMapping("/orders/complete")
    public OrderCompletionResult completeOrders(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return orderService.completeProcessingOrdersByDeliveryDate(
                date != null ? date : tomorrow());
    }

    private LocalDate tomorrow() {
        return LocalDate.now(DELIVERY_TIME_ZONE).plusDays(1);
    }

    public record OrderSelectionSummary(LocalDate deliveryDate, int count, List<Long> orderIds) {
    }
}
