package vn.utetra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.config.PaymentProperties;
import vn.utetra.entity.Order;
import vn.utetra.entity.Payment;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
	private static final Set<String> DONE = Set.of("SUCCESS", "REFUND_PENDING", "REFUNDED");

	private final PaymentProperties props;
	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final VnPayService vnPayService;
	private final MomoService momoService;
	private final RefundService refundService;
	private final NotificationService notificationService;
	private final OrderNotifier orderNotifier;
	private final OrderFlowService orderFlowService;
	private final PaymentService self;

	public PaymentService(PaymentProperties props, PaymentRepository paymentRepository, OrderRepository orderRepository,
			VnPayService vnPayService, MomoService momoService, RefundService refundService,
			NotificationService notificationService, OrderNotifier orderNotifier, OrderFlowService orderFlowService,
			@Lazy PaymentService self) {
		this.props = props;
		this.paymentRepository = paymentRepository;
		this.orderRepository = orderRepository;
		this.vnPayService = vnPayService;
		this.momoService = momoService;
		this.refundService = refundService;
		this.notificationService = notificationService;
		this.orderNotifier = orderNotifier;
		this.orderFlowService = orderFlowService;
		this.self = self;
	}

	public record Outcome(Integer orderId, String status, String message) {
	}

	@Transactional
	public String startPayment(Integer userId, Integer orderId, String method, String clientIp) {
		Order o = owned(userId, orderId);
		if (o.getStatus() != OrderStatus.AWAITING_PAYMENT || o.getPaymentStatus() != PaymentStatus.UNPAID) {
			throw new BusinessException(o.getPaymentStatus() == PaymentStatus.PAID
					? "Đơn #" + orderId + " đã được thanh toán"
					: "Đơn #" + orderId + " không còn chờ thanh toán");
		}
		LocalDateTime deadline = deadline(o);
		if (!LocalDateTime.now().isBefore(deadline.minusSeconds(30))) {
			throw new BusinessException("Đơn #" + orderId + " đã hết hạn thanh toán");
		}
		if (method != null && !method.isBlank()) changeMethod(o, PaymentMethod.valueOf(method.trim().toUpperCase()));
		PaymentMethod m = o.getPaymentMethod();
		if (m != PaymentMethod.VNPAY && m != PaymentMethod.MOMO) throw new BusinessException("Đơn này thanh toán khi nhận hàng");

		for (Payment old : paymentRepository.findByOrderIdAndStatus(orderId, "PENDING")) {
			old.setStatus("CANCELLED");
			old.setMessage("Khách đã tạo giao dịch mới");
		}

		Payment p = new Payment();
		p.setOrder(o);
		p.setMethod(m.name());
		p.setAmount(o.getTotalAmount());
		p.setStatus("PENDING");
		p.setTxnRef("UTE" + orderId + "T" + (System.currentTimeMillis() / 1000) + ThreadLocalRandom.current().nextInt(10, 99));
		p.setGatewayDate(LocalDateTime.now(VnPayService.VN).format(VnPayService.VNP_TIME));
		p.setCreatedAt(LocalDateTime.now());
		paymentRepository.save(p);

		String info = "Thanh toan don hang UTETra " + orderId;
		if (m == PaymentMethod.VNPAY) {
			if (!vnPayService.isConfigured()) return "/payment/" + orderId + "/demo";
			return vnPayService.createPaymentUrl(p, info, clientIp, deadline, null);
		}
		return momoService.createPayUrl(p, info);
	}

	@Transactional
	public void switchToCod(Integer userId, Integer orderId) {
		Order o = owned(userId, orderId);
		if (o.getStatus() != OrderStatus.AWAITING_PAYMENT || o.getPaymentStatus() != PaymentStatus.UNPAID) {
			throw new BusinessException("Đơn #" + orderId + " không còn chờ thanh toán");
		}
		changeMethod(o, PaymentMethod.COD);
		for (Payment old : paymentRepository.findByOrderIdAndStatus(orderId, "PENDING")) {
			old.setStatus("CANCELLED");
			old.setMessage("Khách đổi sang thanh toán khi nhận hàng");
		}
		o.setStatus(OrderStatus.PENDING);
		o.setUpdatedAt(LocalDateTime.now());
		orderRepository.save(o);
		orderNotifier.onNewOrder(o);
	}

	private void changeMethod(Order o, PaymentMethod to) {
		if (to == o.getPaymentMethod()) return;
		Voucher v = o.getProductVoucher();
		if (v != null && v.getPaymentMethod() != null && v.getPaymentMethod() != to) {
			throw new BusinessException("Mã " + v.getCode() + " chỉ áp dụng khi thanh toán bằng "
					+ label(v.getPaymentMethod()) + ", không đổi phương thức được");
		}
		o.setPaymentMethod(to);
	}

	@Transactional
	public Outcome vnpayReturn(Map<String, String> params) {
		return apply(vnPayService.readResult(params), "return");
	}

	@Transactional
	public Map<String, String> vnpayIpn(Map<String, String> params) {
		GatewayResult r = vnPayService.readResult(params);
		if (!r.verified()) return Map.of("RspCode", "97", "Message", "Invalid Checksum");
		Payment p = paymentRepository.findByTxnRef(r.txnRef()).orElse(null);
		if (p == null) return Map.of("RspCode", "01", "Message", "Order not Found");
		if (p.getAmount().longValue() != r.amount()) return Map.of("RspCode", "04", "Message", "Invalid Amount");
		if (!"PENDING".equals(p.getStatus()) && !"CANCELLED".equals(p.getStatus())
				&& !"EXPIRED".equals(p.getStatus())) {
			return Map.of("RspCode", "02", "Message", "Order already confirmed");
		}
		apply(r, "ipn");
		return Map.of("RspCode", "00", "Message", "Confirm Success");
	}

	@Transactional
	public Outcome momoReturn(Map<String, String> params) {
		return apply(momoService.readResult(params), "return");
	}

	@Transactional
	public void momoIpn(Map<String, String> params) {
		apply(momoService.readResult(params), "ipn");
	}

	@Transactional
	public Outcome demoResult(Integer userId, Integer orderId, boolean success) {
		owned(userId, orderId);
		Payment p = paymentRepository.findByOrderIdAndStatus(orderId, "PENDING").stream().findFirst()
				.orElseThrow(() -> new BusinessException("Không có giao dịch đang chờ, vui lòng bấm Thanh toán lại"));
		GatewayResult r = new GatewayResult(true, success ? "SUCCESS" : "FAILED", p.getTxnRef(),
				success ? "DEMO" + System.currentTimeMillis() % 100000000 : null, p.getAmount().longValue(),
				success ? "NCB" : null, success ? "00" : "24",
				success ? "Giao dịch thành công (demo)" : VnPayService.responseMessage("24"));
		return apply(r, "demo");
	}

	private Outcome apply(GatewayResult r, String source) {
		if (!r.verified()) return new Outcome(null, "INVALID", r.message());
		Payment p = paymentRepository.findByTxnRef(r.txnRef()).orElse(null);
		if (p == null) return new Outcome(null, "INVALID", "Không tìm thấy giao dịch " + r.txnRef());
		Integer orderId = p.getOrder().getId();
		log.info("Thanh toán {} ({}): txnRef={}, status={}, code={}", p.getMethod(), source, r.txnRef(), r.status(),
				r.responseCode());

		if (DONE.contains(p.getStatus())) return new Outcome(orderId, "SUCCESS", "Giao dịch đã được ghi nhận");
		if (r.success() && r.amount() != p.getAmount().longValue()) {
			p.setStatus("FAILED");
			p.setMessage("Số tiền cổng báo về (" + r.amount() + ") khác số tiền đơn");
			return new Outcome(orderId, "FAILED", "Số tiền thanh toán không khớp, vui lòng liên hệ UTETra");
		}
		if (r.success()) {
			markPaid(p, r);
			return new Outcome(orderId, "SUCCESS", "Thanh toán thành công");
		}
		if (r.pending()) return new Outcome(orderId, "PENDING", r.message());

		if ("PENDING".equals(p.getStatus())) {
			p.setStatus("24".equals(r.responseCode()) || "1006".equals(r.responseCode()) ? "CANCELLED" : "FAILED");
			p.setResponseCode(r.responseCode());
			p.setMessage(r.message());
		}
		return new Outcome(orderId, "FAILED", r.message());
	}

	private void markPaid(Payment p, GatewayResult r) {
		LocalDateTime now = LocalDateTime.now();
		p.setStatus("SUCCESS");
		p.setTransactionCode(r.transactionNo());
		p.setBankCode(r.bankCode());
		p.setResponseCode(r.responseCode());
		p.setMessage(r.message());
		p.setPaidAt(now);
		paymentRepository.save(p);

		Order o = p.getOrder();
		if (o.getStatus() == OrderStatus.AWAITING_PAYMENT && o.getPaymentStatus() == PaymentStatus.UNPAID) {
			o.setPaymentMethod(PaymentMethod.valueOf(p.getMethod()));
			o.setPaymentStatus(PaymentStatus.PAID);
			o.setStatus(OrderStatus.PENDING);
			o.setUpdatedAt(now);
			orderRepository.save(o);
			notificationService.create(o.getUser(), o, "PAYMENT", "Thanh toán thành công đơn #" + o.getId(),
					"Đã trả " + money(p) + "đ qua " + label(o.getPaymentMethod()) + ". Quán sẽ xác nhận đơn trong ít phút",
					"/orders/" + o.getId());
			orderNotifier.onNewOrder(o);
			return;
		}
		String reason = o.getStatus() == OrderStatus.CANCELLED
				? "Bạn trả tiền sau khi đơn đã huỷ - UTETra tự hoàn lại"
				: "Đơn đã được thanh toán trước đó - UTETra tự hoàn khoản trả trùng";
		if (o.getStatus() == OrderStatus.CANCELLED && o.getPaymentStatus() == PaymentStatus.UNPAID) {
			o.setPaymentStatus(PaymentStatus.REFUND_PENDING);
			orderRepository.save(o);
		}
		refundService.requestRefund(p, reason);
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
	public void syncAndExpire() {
		for (Payment p : paymentRepository.findByStatusAndCreatedAtBefore("PENDING", LocalDateTime.now().minusMinutes(1))) {
			try {
				self.syncPayment(p.getId());
			} catch (Exception e) {
				log.warn("Hỏi lại giao dịch #{} lỗi: {}", p.getId(), e.getMessage());
			}
		}
		LocalDateTime cutoff = LocalDateTime.now().minusMinutes(props.timeoutMinutes());
		for (Order o : orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, cutoff)) {
			try {
				self.expireOrder(o.getId());
			} catch (Exception e) {
				log.warn("Huỷ đơn quá hạn #{} lỗi: {}", o.getId(), e.getMessage());
			}
		}
	}

	@Transactional
	public void syncPayment(Integer paymentId) {
		Payment p = paymentRepository.findById(paymentId).orElse(null);
		if (p == null || !"PENDING".equals(p.getStatus())) return;
		GatewayResult r;
		if ("VNPAY".equals(p.getMethod())) {
			if (!vnPayService.isConfigured()) {
				expireIfOld(p);
				return;
			}
			r = vnPayService.query(p);
		} else {
			r = momoService.query(p);
		}
		if (r.verified() && !r.pending()) {
			apply(r, "query");
		} else {
			expireIfOld(p);
		}
	}

	private void expireIfOld(Payment p) {
		if (p.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(props.timeoutMinutes() + 5L))) {
			p.setStatus("EXPIRED");
			p.setMessage("Quá hạn thanh toán");
		}
	}

	@Transactional
	public void expireOrder(Integer orderId) {
		Order o = orderRepository.findById(orderId).orElse(null);
		if (o == null || o.getStatus() != OrderStatus.AWAITING_PAYMENT) return;
		if (LocalDateTime.now().isBefore(deadline(o))) return;
		for (Payment p : paymentRepository.findByOrderIdAndStatus(orderId, "PENDING")) {
			p.setStatus("EXPIRED");
			p.setMessage("Quá hạn thanh toán");
		}
		orderFlowService.cancelBySystem(orderId, "Quá " + props.timeoutMinutes() + " phút chưa thanh toán");
	}

	@Transactional
	public Map<String, Object> status(Integer userId, Integer orderId, boolean refresh) {
		Order o = owned(userId, orderId);
		Payment last = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
		if (refresh && last != null && "PENDING".equals(last.getStatus())) {
			self.syncPayment(last.getId());
			o = owned(userId, orderId);
			last = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
		}
		return Map.of("orderStatus", o.getStatus().name(), "paymentStatus", o.getPaymentStatus().name(),
				"transactionStatus", last == null ? "" : last.getStatus(),
				"deadlineMillis", deadlineMillis(o));
	}

	public LocalDateTime deadline(Order o) {
		return o.getCreatedAt().plusMinutes(props.timeoutMinutes());
	}

	public long deadlineMillis(Order o) {
		return deadline(o).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	public Order owned(Integer userId, Integer orderId) {
		return orderRepository.findById(orderId).filter(o -> o.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
	}

	public boolean vnpayDemo() {
		return !vnPayService.isConfigured();
	}

	public List<Payment> history(Integer orderId) {
		return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
	}

	public static String label(PaymentMethod m) {
		return m == PaymentMethod.MOMO ? "Ví MoMo" : m == PaymentMethod.VNPAY ? "VNPAY" : "tiền mặt (COD)";
	}

	private static String money(Payment p) {
		return String.format("%,d", p.getAmount().longValue()).replace(',', '.');
	}
}
