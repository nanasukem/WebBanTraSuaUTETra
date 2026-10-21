package vn.utetra.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UnpaidVnpayOrderExpirationService {

	private final OrderRepository orderRepository;

	public UnpaidVnpayOrderExpirationService(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	/**
	 * Kiểm tra mỗi phút và hủy đơn VNPAY đã quá 24 giờ nhưng vẫn chưa thanh toán.
	 */
	@Scheduled(fixedDelay = 60_000)
	@Transactional
	public void cancelExpiredOrders() {
		LocalDateTime deadline = LocalDateTime.now().minusHours(24);

		List<Order> expiredOrders = orderRepository.findByPaymentMethodAndPaymentStatusAndStatusNotAndCreatedAtBefore(
				PaymentMethod.VNPAY, PaymentStatus.UNPAID, OrderStatus.CANCELLED, deadline);

		for (Order order : expiredOrders) {
			// Kiểm tra lại trước khi hủy.
			if (order.getPaymentMethod() == PaymentMethod.VNPAY && order.getPaymentStatus() == PaymentStatus.UNPAID
					&& order.getStatus() != OrderStatus.CANCELLED && order.getCreatedAt() != null
					&& order.getCreatedAt().isBefore(deadline)) {

				order.setStatus(OrderStatus.CANCELLED);
			}
		}

		if (!expiredOrders.isEmpty()) {
			orderRepository.saveAll(expiredOrders);
		}
	}
}
