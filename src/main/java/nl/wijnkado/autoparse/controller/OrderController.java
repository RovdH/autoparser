package nl.wijnkado.autoparse.controller;

import nl.wijnkado.autoparse.dto.OrderDto;
import nl.wijnkado.autoparse.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Alleen 'processing' orders ZONDER track & trace.
     */
    @GetMapping("/orders")
    public List<OrderDto> getProcOrdersWithoutTrackTrace() {
        return orderService.getProcessingOrdersWithoutTrackTrace();
    }

    /**
     * Optioneel: alle processing orders (handig voor debug)
     */
    @GetMapping("/orders/all")
    public List<OrderDto> getAllProcessingOrders() {
        return orderService.getProcessingOrders();
    }
}
