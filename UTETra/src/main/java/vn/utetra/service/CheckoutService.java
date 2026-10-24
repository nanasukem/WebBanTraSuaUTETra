package vn.utetra.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.VoucherRepository;

@Service
public class CheckoutService {

	private final VoucherRepository voucherRepository;
	private final OrderRepository orderRepository;
	private final OrderFlowService orderFlowService;
	private final CartService cartService;

	public CheckoutService(VoucherRepository voucherRepository, OrderRepository orderRepository,
			OrderFlowService orderFlowService, CartService cartService) {
		this.voucherRepository = voucherRepository;
		this.orderRepository = orderRepository;
		this.orderFlowService = orderFlowService;
		this.cartService = cartService;
	}

	@Transactional(readOnly = true)
	public Map<String, String> voucherType(String code) {
		String c = code == null ? "" : code.trim();
		return voucherRepository.findByCodeIgnoreCase(c)
				.map(v -> Map.of("code", v.getCode(), "applyTo", v.getApplyTo().name()))
				.orElseThrow(() -> new BusinessException("Mã " + c.toUpperCase() + " không tồn tại"));
	}

	@Transactional
	public void returnToCheckout(Integer userId, Integer orderId) {
		Order order = orderRepository.findById(orderId)
				.filter(o -> o.getUser() != null && o.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));

		if (order.getStatus() != OrderStatus.AWAITING_PAYMENT || order.getPaymentStatus() != PaymentStatus.UNPAID) {
			throw new BusinessException(order.getPaymentStatus() == PaymentStatus.PAID
					? "Đơn hàng đã thanh toán thành công, không thể quay lại chỉnh sửa"
					: "Đơn hàng không còn chờ thanh toán");
		}

		orderFlowService.cancelByCustomer(userId, orderId, "Quay lại sửa đơn trước khi thanh toán");
		cartService.reorder(userId, orderId);
	}
}
