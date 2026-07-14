package nl.wijnkado.autoparse.dto;

import java.time.LocalDate;
import java.util.List;

public record OrderCompletionResult(
        LocalDate deliveryDate,
        int matched,
        int completed,
        List<Long> completedOrderIds,
        List<OrderFailure> failures
) {
    public record OrderFailure(Long orderId, String message) {
    }
}
