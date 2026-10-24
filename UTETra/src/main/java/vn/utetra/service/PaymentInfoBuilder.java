package vn.utetra.service;

import org.springframework.stereotype.Component;
import vn.utetra.config.PaymentProperties;
import vn.utetra.dto.response.PaymentInfoRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.Payment;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class PaymentInfoBuilder {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

	private final PaymentRepository paymentRepository;
	private final PaymentProperties props;

	public PaymentInfoBuilder(PaymentRepository paymentRepository, PaymentProperties props) {
		this.paymentRepository = paymentRepository;
		this.props = props;
	}

	public PaymentInfoRes build(Order o) {
		PaymentMethod m = o.getPaymentMethod();
		PaymentStatus s = o.getPaymentStatus() == null ? PaymentStatus.UNPAID : o.getPaymentStatus();
		boolean online = m == PaymentMethod.VNPAY || m == PaymentMethod.MOMO;
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		String methodLabel = methodLabel(m);
		String total = money(o.getTotalAmount().longValue());

		List<Payment> payments = online || s != PaymentStatus.UNPAID
				? paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()) : List.of();
		Optional<Payment> paid = payments.stream()
				.filter(p -> List.of("SUCCESS", "REFUND_PENDING", "REFUNDED").contains(p.getStatus())).findFirst();

		boolean awaiting = o.getStatus() == OrderStatus.AWAITING_PAYMENT;
		LocalDateTime deadline = o.getCreatedAt().plusMinutes(props.timeoutMinutes());
		long deadlineMillis = awaiting ? deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;

		String badge, badgeClass, icon, line;
		if (awaiting) {
			badge = "Chờ thanh toán";
			badgeClass = "text-bg-warning";
			icon = "bi-hourglass-split";
			line = "Bạn cần thanh toán " + total + "đ qua " + methodLabel + " trước " + deadline.format(HOUR)
					+ ", quá hạn đơn sẽ tự huỷ.";
		} else if (s == PaymentStatus.REFUNDED) {
			badge = "Đã hoàn tiền";
			badgeClass = "text-bg-info";
			icon = "bi-arrow-counterclockwise";
			line = "Đã hoàn " + total + "đ về " + destination(m) + ".";
		} else if (s == PaymentStatus.REFUND_PENDING) {
			badge = "Đang hoàn tiền";
			badgeClass = "text-bg-info";
			icon = "bi-arrow-repeat";
			line = "UTETra đang hoàn " + total + "đ về " + destination(m) + ".";
		} else if (s == PaymentStatus.PAID) {
			badge = online ? "Đã thanh toán" : "Đã thanh toán tiền mặt";
			badgeClass = "text-bg-success";
			icon = "bi-check-circle-fill";
			line = online
					? "Đã thanh toán " + total + "đ qua " + methodLabel
							+ paid.map(p -> p.getPaidAt() == null ? "" : " lúc " + p.getPaidAt().format(TIME)).orElse("")
							+ ". Khi nhận món bạn không cần trả thêm."
					: "Đã thu " + total + "đ tiền mặt khi " + (pickup ? "nhận món tại quán." : "giao hàng.");
		} else if (o.getStatus() == OrderStatus.CANCELLED) {
			badge = "Chưa thanh toán";
			badgeClass = "text-bg-secondary";
			icon = "bi-dash-circle";
			line = "Đơn đã huỷ khi chưa thanh toán nên không phát sinh tiền.";
		} else {
			badge = "Chưa thanh toán";
			badgeClass = "text-bg-light border text-dark";
			icon = "bi-cash-coin";
			line = "Thanh toán " + total + "đ tiền mặt khi " + (pickup ? "đến lấy món tại quán." : "nhận hàng (COD).");
		}

		PaymentInfoRes.Refund refund = paid
				.filter(p -> "REFUND_PENDING".equals(p.getStatus()) || "REFUNDED".equals(p.getStatus()))
				.map(p -> new PaymentInfoRes.Refund(p.getStatus(),
						"REFUNDED".equals(p.getStatus()) ? "Đã hoàn tiền" : "Đang xử lý hoàn tiền", p.refundValue(),
						destination(m), time(p.getRefundRequestedAt()), time(p.getRefundedAt()),
						p.getRefundTransactionCode(), p.getRefundMessage(), RefundService.arrivalNote(p.getMethod()),
						arrived(p), arrivalLabel(p)))
				.orElse(null);
		if (refund == null && s == PaymentStatus.REFUND_PENDING) {
			refund = new PaymentInfoRes.Refund("REFUND_PENDING", "Đang xử lý hoàn tiền", o.getTotalAmount(),
					destination(m), null, null, null, "UTETra sẽ liên hệ để hoàn tiền", RefundService.arrivalNote(m.name()),
					false, "Tiền về tài khoản của bạn");
		}

		return new PaymentInfoRes(m == null ? null : m.name(), methodLabel, s.name(), badge, badgeClass, icon, line,
				online && paid.isPresent(), paid.map(p -> time(p.getPaidAt())).orElse(null),
				paid.map(Payment::getTransactionCode).orElse(null), paid.map(Payment::getBankCode).orElse(null),
				awaiting, deadlineMillis, awaiting ? deadline.format(HOUR) : null, refund);
	}

	private static boolean arrived(Payment p) {
		if (!"REFUNDED".equals(p.getStatus()) || p.getRefundedAt() == null) return false;
		if ("MOMO".equals(p.getMethod())) return true;
		return !LocalDateTime.now().isBefore(expectedArrival(p));
	}

	private static String arrivalLabel(Payment p) {
		boolean momo = "MOMO".equals(p.getMethod());
		if (!"REFUNDED".equals(p.getStatus()) || p.getRefundedAt() == null) {
			return momo ? "Tiền về ví MoMo của bạn" : "Tiền về tài khoản / thẻ của bạn";
		}
		if (momo) return "Tiền đã về ví MoMo của bạn";
		return arrived(p) ? "Tiền đã về tài khoản / thẻ của bạn"
				: "Ngân hàng đang xử lý, dự kiến tiền về trước " + expectedArrival(p).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
	}

	private static LocalDateTime expectedArrival(Payment p) {
		LocalDateTime t = p.getRefundedAt();
		int days = 0;
		while (days < 3) {
			t = t.plusDays(1);
			if (t.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && t.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) days++;
		}
		return t;
	}

	public String storeLabel(Order o) {
		PaymentStatus s = o.getPaymentStatus();
		String method = methodLabel(o.getPaymentMethod());
		if (s == PaymentStatus.PAID) {
			return o.getPaymentMethod() == PaymentMethod.COD ? "Đã thu tiền mặt" : "Đã thanh toán qua " + method + " - không thu tiền";
		}
		if (s == PaymentStatus.REFUND_PENDING) return "Đang hoàn tiền cho khách (" + method + ")";
		if (s == PaymentStatus.REFUNDED) return "Đã hoàn tiền cho khách (" + method + ")";
		if (o.getStatus() == OrderStatus.CANCELLED) return "Chưa thanh toán";
		boolean pickup = o.getDeliveryType() == DeliveryType.PICKUP;
		return "Chưa thanh toán - thu " + money(o.getTotalAmount().longValue()) + "đ tiền mặt khi "
				+ (pickup ? "khách lấy" : "giao");
	}

	public static String methodLabel(PaymentMethod m) {
		if (m == null) return "";
		return switch (m) {
			case COD -> "Tiền mặt (COD)";
			case VNPAY -> "VNPAY";
			case MOMO -> "Ví MoMo";
		};
	}

	private static String destination(PaymentMethod m) {
		return m == PaymentMethod.MOMO ? "ví MoMo của bạn" : "tài khoản / thẻ bạn đã thanh toán qua VNPAY";
	}

	private static String time(LocalDateTime t) {
		return t == null ? null : t.format(TIME);
	}

	private static String money(long v) {
		return String.format("%,d", v).replace(',', '.');
	}
}
