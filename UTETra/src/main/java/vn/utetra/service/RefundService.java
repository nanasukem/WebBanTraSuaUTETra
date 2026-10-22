package vn.utetra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utetra.entity.Order;
import vn.utetra.entity.Payment;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Hoàn tiền đơn đã trả trước (VNPAY / MoMo) - giống app thật:
 * 1. Đơn bị huỷ (khách / quán / Admin huỷ) -> giao dịch chuyển "Đang hoàn tiền", đơn hiện "Đang hoàn tiền"
 * 2. Sau khi lưu xong, gọi API hoàn tiền của cổng (chạy nền, không bắt khách chờ)
 * 3. Cổng nhận lệnh -> "Đã hoàn tiền" + báo chuông cho khách. Lỗi (mất mạng...) -> 5 phút thử lại, tối đa 5 lần,
 *    quá 5 lần báo Admin xử lý tay (trang chi tiết đơn của Admin có nút "Hoàn tiền lại").
 * Cũng dùng khi khách trả tiền TRỄ (đơn đã bị huỷ vì quá hạn) hoặc trả 2 lần cho 1 đơn: tự hoàn khoản thừa.
 */
@Service
public class RefundService {

	private static final Logger log = LoggerFactory.getLogger(RefundService.class);
	public static final int MAX_ATTEMPTS = 5;

	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;
	private final VnPayService vnPayService;
	private final MomoService momoService;
	private final NotificationService notificationService;
	private final RefundService self;

	public RefundService(PaymentRepository paymentRepository, OrderRepository orderRepository,
			VnPayService vnPayService, MomoService momoService, NotificationService notificationService,
			@Lazy RefundService self) {
		this.paymentRepository = paymentRepository;
		this.orderRepository = orderRepository;
		this.vnPayService = vnPayService;
		this.momoService = momoService;
		this.notificationService = notificationService;
		this.self = self;
	}

	/** Đơn đã trả trước bị huỷ: hoàn khoản đã trả. Gọi trong transaction huỷ đơn */
	@Transactional
	public void refundOrder(Order order, String reason) {
		List<Payment> paid = paymentRepository.findByOrderIdAndStatus(order.getId(), "SUCCESS");
		order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
		orderRepository.save(order);
		if (paid.isEmpty()) {
			// Đơn mẫu không có dòng giao dịch -> để Admin hoàn tay
			log.warn("Đơn #{} đã trả trước nhưng không tìm thấy giao dịch để hoàn", order.getId());
			notificationService.notifyAdmins("REFUND", "Cần hoàn tiền tay đơn #" + order.getId(),
					"Không tìm thấy giao dịch VNPAY/MoMo của đơn", "/admin/orders/" + order.getId(),
					"REFUND_MANUAL:" + order.getId());
			return;
		}
		paid.forEach(p -> requestRefund(p, reason));
	}

	/**
	 * Hoàn 1 phần / toàn bộ đơn ĐÃ GIAO (khách báo vấn đề, quán đồng ý hoàn). Đơn trả trước: hoàn qua cổng.
	 * Trả về false nếu đơn không trả online (COD: quán tự hoàn tiền mặt / chuyển khoản cho khách).
	 */
	@Transactional
	public boolean refundDelivered(Order order, java.math.BigDecimal amount, String reason) {
		List<Payment> paid = paymentRepository.findByOrderIdAndStatus(order.getId(), "SUCCESS");
		if (paid.isEmpty()) return false;
		Payment p = paid.get(0);
		p.setRefundAmount(amount.min(p.getAmount()));
		requestRefund(p, reason);
		return true;
	}

	/** Đánh dấu 1 giao dịch cần hoàn, sau khi lưu xong thì gọi cổng (chạy nền) */
	@Transactional
	public void requestRefund(Payment p, String reason) {
		if (!"SUCCESS".equals(p.getStatus())) return;
		p.setStatus("REFUND_PENDING");
		p.setRefundRef("RF" + p.getId() + "T" + System.currentTimeMillis() / 1000);
		p.setRefundRequestedAt(LocalDateTime.now());
		p.setRefundAttempts(0);
		p.setRefundMessage(reason);
		paymentRepository.save(p);
		Integer id = p.getId();
		Runnable run = () -> CompletableFuture.runAsync(() -> {
			try {
				self.process(id);
			} catch (Exception e) {
				log.warn("Hoàn tiền giao dịch #{} lỗi: {}", id, e.getMessage());
			}
		});
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					run.run();
				}
			});
		} else {
			run.run();
		}
	}

	/** Gọi API hoàn tiền của cổng cho 1 giao dịch đang chờ hoàn */
	@Transactional
	public boolean process(Integer paymentId) {
		Payment p = paymentRepository.findById(paymentId).orElse(null);
		if (p == null || !"REFUND_PENDING".equals(p.getStatus())) return false;
		p.setRefundAttempts(p.getRefundAttempts() == null ? 1 : p.getRefundAttempts() + 1);

		RefundResult r;
		boolean demo = p.getTransactionCode() != null && p.getTransactionCode().startsWith("DEMO");
		if (demo) {
			r = new RefundResult(true, "00", "DEMORF" + p.getId(), "Hoàn tiền (chế độ demo)");
		} else if ("VNPAY".equals(p.getMethod())) {
			r = vnPayService.isConfigured() ? vnPayService.refund(p, "UTETra")
					: new RefundResult(false, null, null, "Chưa cấu hình VNPAY sandbox");
		} else {
			r = momoService.refund(p);
		}

		Order o = p.getOrder();
		if (r.success()) {
			p.setStatus("REFUNDED");
			p.setRefundedAt(LocalDateTime.now());
			p.setRefundTransactionCode(r.transactionNo());
			p.setRefundMessage(r.message());
			paymentRepository.save(p);

			boolean stillRefunding = paymentRepository.findByOrderIdAndStatus(o.getId(), "REFUND_PENDING").stream()
					.anyMatch(x -> !x.getId().equals(p.getId()));
			boolean full = p.refundValue().compareTo(p.getAmount()) >= 0;
			if (!stillRefunding && full) {
				o.setPaymentStatus(PaymentStatus.REFUNDED);
				orderRepository.save(o);
			}
			String where = "MOMO".equals(p.getMethod()) ? "ví MoMo" : "tài khoản / thẻ bạn đã dùng qua VNPAY";
			notificationService.create(o.getUser(), o, "REFUND",
					"Đã hoàn " + money(p) + "đ cho đơn #" + o.getId(),
					"Tiền được hoàn về " + where + ". " + RefundService.arrivalNote(p.getMethod()),
					"/orders/" + o.getId());
			return true;
		}

		p.setRefundMessage(r.message());
		paymentRepository.save(p);
		log.warn("Hoàn tiền giao dịch #{} (đơn #{}) lần {} lỗi: {}", p.getId(), o.getId(), p.getRefundAttempts(),
				r.message());
		if (p.getRefundAttempts() >= MAX_ATTEMPTS) {
			notificationService.notifyAdmins("REFUND", "Hoàn tiền đơn #" + o.getId() + " chưa được",
					r.message() + ". Vào chi tiết đơn để hoàn lại", "/admin/orders/" + o.getId(),
					"REFUND_FAIL:" + p.getId());
		}
		return false;
	}

	/** Admin bấm "Hoàn tiền lại": đếm lại số lần thử và gọi cổng ngay */
	@Transactional
	public boolean retry(Integer orderId) {
		boolean ok = false;
		for (Payment p : paymentRepository.findByOrderIdAndStatus(orderId, "REFUND_PENDING")) {
			p.setRefundAttempts(0);
			ok = self.process(p.getId()) || ok;
		}
		return ok;
	}

	/** 5 phút thử hoàn lại các giao dịch lần trước lỗi */
	@Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
	public void retryFailed() {
		for (Payment p : paymentRepository.findByStatusAndRefundAttemptsLessThan("REFUND_PENDING", MAX_ATTEMPTS)) {
			// Lần đầu đã chạy ngay sau khi huỷ; ở đây chỉ chạy lại khi lần trước lỗi / app tắt giữa chừng
			if (p.getRefundRequestedAt() == null || p.getRefundRequestedAt().isBefore(LocalDateTime.now().minusMinutes(1))) {
				try {
					self.process(p.getId());
				} catch (Exception e) {
					log.warn("Thử hoàn lại giao dịch #{} lỗi: {}", p.getId(), e.getMessage());
				}
			}
		}
	}

	/** Thời gian tiền về (giống thông báo của các app) */
	public static String arrivalNote(String method) {
		return "MOMO".equals(method)
				? "Ví MoMo thường nhận lại tiền ngay, chậm nhất 1-3 ngày làm việc."
				: "Thời gian tiền về tuỳ ngân hàng: thẻ ATM / tài khoản 1-3 ngày làm việc, thẻ Visa/Master 5-15 ngày làm việc.";
	}

	private static String money(Payment p) {
		return String.format("%,d", p.refundValue().longValue()).replace(',', '.');
	}
}
