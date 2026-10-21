
package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.entity.Order;
import vn.utetra.entity.Payment;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.PaymentRepository;
import vn.utetra.service.CartService;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.OrderNotifier;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

@Controller
@RequestMapping("/payment/vnpay")
public class VNPayPaymentController {

	private static final Logger log = LoggerFactory.getLogger(VNPayPaymentController.class);

	private static final int PAYMENT_TIMEOUT_MINUTES = 5;
	private static final int ORDER_TIMEOUT_HOURS = 24;

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final CurrentUserService currentUserService;
	private final CartService cartService;
	private final OrderNotifier orderNotifier;

	public VNPayPaymentController(OrderRepository orderRepository, PaymentRepository paymentRepository,
			CurrentUserService currentUserService, CartService cartService, OrderNotifier orderNotifier) {

		this.orderRepository = orderRepository;
		this.paymentRepository = paymentRepository;
		this.currentUserService = currentUserService;
		this.cartService = cartService;
		this.orderNotifier = orderNotifier;
	}

	@GetMapping("/{orderId}")
	@Transactional
	public String showPayment(@PathVariable Integer orderId, @RequestParam(defaultValue = "false") boolean retry,
			Principal principal, HttpSession session, Model model, CsrfToken csrfToken,
			RedirectAttributes redirectAttributes) {

		addCsrfToken(model, csrfToken);

		var user = currentUserService.requireUser(principal, session);
		Order order = findOwnedOrder(orderId, user.getId());

		if (order == null) {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Không tìm thấy đơn hàng hoặc bạn không có quyền truy cập.");
			return "redirect:/checkout";
		}

		if (order.getPaymentMethod() != PaymentMethod.VNPAY) {
			return "redirect:/checkout/success/" + orderId;
		}

		if (order.getPaymentStatus() == PaymentStatus.PAID) {
			return "redirect:/checkout/success/" + orderId;
		}

		if (order.getStatus() == OrderStatus.CANCELLED) {
			return showResult(model, order, "failed", "Đơn hàng đã bị hủy.", false);
		}

		if (isOrderExpired(order)) {
			cancelExpiredOrder(order);
			return showResult(model, order, "failed", "Đơn hàng đã hết hạn thanh toán và bị hủy.", false);
		}

		Payment payment = findLatestPayment(orderId);

		if (payment != null && "PENDING".equalsIgnoreCase(payment.getStatus()) && isPaymentExpired(payment)) {

			markFailed(payment);
			paymentRepository.save(payment);
			payment = null;
		}

		if (payment != null && "PENDING".equalsIgnoreCase(payment.getStatus())) {

			model.addAttribute("order", order);
			model.addAttribute("expiresAtMillis", getPaymentExpiresAtMillis(payment));

			return "payment/vnpay";
		}

		if (payment != null
				&& ("FAILED".equalsIgnoreCase(payment.getStatus()) || "CANCELLED".equalsIgnoreCase(payment.getStatus()))
				&& !retry) {

			return showResult(model, order, "failed", "Giao dịch chưa thành công. Bạn có thể thử thanh toán lại.",
					true);
		}

		Payment newPayment = createPendingPayment(order);

		model.addAttribute("order", order);
		model.addAttribute("expiresAtMillis", getPaymentExpiresAtMillis(newPayment));

		return "payment/vnpay";
	}

	@PostMapping("/{orderId}/process")
	@Transactional
	public String processPayment(@PathVariable Integer orderId,
			@RequestParam(value = "result", required = false) String result, Principal principal, HttpSession session,
			Model model, CsrfToken csrfToken, RedirectAttributes redirectAttributes) {

		addCsrfToken(model, csrfToken);

		var user = currentUserService.requireUser(principal, session);
		Order order = findOwnedOrder(orderId, user.getId());

		if (order == null) {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Không tìm thấy đơn hàng hoặc bạn không có quyền truy cập.");
			return "redirect:/checkout";
		}

		if (order.getPaymentMethod() != PaymentMethod.VNPAY) {
			return "redirect:/checkout/success/" + orderId;
		}

		if (order.getPaymentStatus() == PaymentStatus.PAID) {
			return "redirect:/checkout/success/" + orderId;
		}

		if (order.getStatus() == OrderStatus.CANCELLED) {
			return showResult(model, order, "failed", "Đơn hàng đã bị hủy.", false);
		}

		if (isOrderExpired(order)) {
			cancelExpiredOrder(order);
			return showResult(model, order, "failed", "Đơn hàng đã hết hạn thanh toán và bị hủy.", false);
		}

		Payment payment = findLatestPayment(orderId);

		log.info("VNPAY SIMULATION: orderId={}, result={}, paymentStatus={}", orderId, result,
				payment == null ? null : payment.getStatus());

		if (payment == null || !"PENDING".equalsIgnoreCase(payment.getStatus())) {

			return showResult(model, order, "failed",
					"Không tìm thấy giao dịch đang chờ thanh toán. " + "Vui lòng quay lại trang thanh toán để thử lại.",
					true);
		}

		if (isPaymentExpired(payment)) {
			markFailed(payment);
			paymentRepository.save(payment);

			return showResult(model, order, "failed", "Giao dịch đã hết thời gian thanh toán. Vui lòng thử lại.", true);
		}

		if (result == null || !"success".equalsIgnoreCase(result.trim())) {

			log.warn("Kết quả VNPAY mô phỏng không hợp lệ: orderId={}, result={}", orderId, result);

			return showResult(model, order, "failed", "Giao dịch chưa được xác nhận thành công.", true);
		}

		payment.setStatus("SUCCESS");
		payment.setTransactionCode(newTransactionCode());
		payment.setPaidAt(LocalDateTime.now());

		order.setPaymentStatus(PaymentStatus.PAID);

		paymentRepository.save(payment);
		orderRepository.save(order);

		cartService.clear(user.getId());
		orderNotifier.onNewOrder(order);

		log.info("VNPAY simulation success: orderId={}, transactionCode={}", order.getId(),
				payment.getTransactionCode());

		return "redirect:/checkout/success/" + order.getId();
	}

	private void addCsrfToken(Model model, CsrfToken csrfToken) {
		if (csrfToken != null) {
			model.addAttribute("_csrf", csrfToken);
		}
	}

	private Order findOwnedOrder(Integer orderId, Object userId) {
		Order order = orderRepository.findById(orderId).orElse(null);

		if (order == null || order.getUser() == null) {
			return null;
		}

		return Objects.equals(order.getUser().getId(), userId) ? order : null;
	}

	private Payment findLatestPayment(Integer orderId) {
		return paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
	}

	private Payment createPendingPayment(Order order) {
		Payment payment = new Payment();
		payment.setOrder(order);
		payment.setMethod("VNPAY");
		payment.setAmount(order.getTotalAmount());
		payment.setStatus("PENDING");
		payment.setCreatedAt(LocalDateTime.now());

		return paymentRepository.save(payment);
	}

	private boolean isPaymentExpired(Payment payment) {
		if (payment.getCreatedAt() == null) {
			return true;
		}

		LocalDateTime expiresAt = payment.getCreatedAt().plusMinutes(PAYMENT_TIMEOUT_MINUTES);

		return !LocalDateTime.now().isBefore(expiresAt);
	}

	private long getPaymentExpiresAtMillis(Payment payment) {
		LocalDateTime expiresAt = payment.getCreatedAt().plusMinutes(PAYMENT_TIMEOUT_MINUTES);

		return expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private boolean isOrderExpired(Order order) {
		if (order.getCreatedAt() == null) {
			return false;
		}

		LocalDateTime expiresAt = order.getCreatedAt().plusHours(ORDER_TIMEOUT_HOURS);

		return !LocalDateTime.now().isBefore(expiresAt);
	}

	private void cancelExpiredOrder(Order order) {
		order.setStatus(OrderStatus.CANCELLED);
		order.setPaymentStatus(PaymentStatus.UNPAID);

		Payment payment = findLatestPayment(order.getId());

		if (payment != null && "PENDING".equalsIgnoreCase(payment.getStatus())) {

			markFailed(payment);
			paymentRepository.save(payment);
		}

		orderRepository.save(order);
	}

	private void markFailed(Payment payment) {
		payment.setStatus("FAILED");
	}

	private String newTransactionCode() {
		return "VNPAYSIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
	}

	private String showResult(Model model, Order order, String result, String message, boolean retryAllowed) {

		model.addAttribute("order", order);
		model.addAttribute("result", result);
		model.addAttribute("success", "success".equalsIgnoreCase(result));
		model.addAttribute("message", message);
		model.addAttribute("retryAllowed", retryAllowed);
		model.addAttribute("payment", findLatestPayment(order.getId()));

		return "payment/vnpay-result";
	}
}
