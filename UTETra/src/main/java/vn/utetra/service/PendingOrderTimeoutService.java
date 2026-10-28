package vn.utetra.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.repository.OrderRepository;

@Service
public class PendingOrderTimeoutService {

	private final OrderRepository orderRepository;
	private final OrderFlowService orderFlowService;

	@Value("${order.confirm-timeout-minutes:10}")
	private long timeoutMinutes;

	public PendingOrderTimeoutService(OrderRepository orderRepository, OrderFlowService orderFlowService) {
		this.orderRepository = orderRepository;
		this.orderFlowService = orderFlowService;
	}

	public long getTimeoutMinutes() {
		return timeoutMinutes;
	}

	public LocalDateTime deadlineOf(Order o) {
		LocalDateTime from = o.getUpdatedAt() != null ? o.getUpdatedAt() : o.getCreatedAt();
		return from == null ? null : from.plusMinutes(timeoutMinutes);
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
	public void cancelUnconfirmed() {
		LocalDateTime now = LocalDateTime.now();
		for (Order o : orderRepository.findByStatus(OrderStatus.PENDING)) {
			LocalDateTime deadline = deadlineOf(o);
			if (deadline == null || !deadline.isBefore(now)) {
				continue;
			}
			try {
				orderFlowService.cancelBySystem(o.getId(),
						"Quán không xác nhận đơn trong " + timeoutMinutes + " phút");
			} catch (RuntimeException ignored) {
			}
		}
	}
}
