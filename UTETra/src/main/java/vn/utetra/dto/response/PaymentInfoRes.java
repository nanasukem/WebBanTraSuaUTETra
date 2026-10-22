package vn.utetra.dto.response;

import java.math.BigDecimal;

/**
 * Tình trạng thanh toán của 1 đơn để khách / quán / Admin biết đơn đã trả trước hay chưa:
 * "Đã thanh toán · VNPAY", "Chưa thanh toán · trả khi nhận hàng", "Chờ thanh toán", "Đang hoàn tiền", "Đã hoàn tiền"
 */
public record PaymentInfoRes(
		String method,             // COD / VNPAY / MOMO
		String methodLabel,        // "Ví MoMo"
		String status,             // UNPAID / PAID / REFUND_PENDING / REFUNDED
		String badge,              // chữ trên nhãn
		String badgeClass,         // màu nhãn Bootstrap
		String icon,               // bootstrap-icons
		String line,               // 1 câu giải thích: "Đã trả 50.000đ qua VNPAY lúc 10:20"
		boolean prepaid,           // đã trả trước online
		String paidAt,
		String transactionCode,    // mã giao dịch của cổng
		String bankCode,
		boolean awaiting,          // đơn đang chờ thanh toán
		long payDeadlineMillis,    // hạn trả (đếm ngược)
		String payDeadline,        // "10:35"
		Refund refund) {           // null = không có hoàn tiền

	/** Thông tin hoàn tiền khi đơn đã trả trước bị huỷ */
	public record Refund(
			String status,         // REFUND_PENDING / REFUNDED
			String statusLabel,
			BigDecimal amount,
			String destination,    // "Ví MoMo" / "Tài khoản / thẻ đã thanh toán qua VNPAY"
			String requestedAt,
			String refundedAt,
			String transactionCode,
			String note,           // lý do / lỗi gần nhất
			String arrival,        // thời gian tiền về
			boolean arrived,       // bước cuối "Tiền đã về tài khoản" đã xong chưa
			String arrivalLabel) { // "Tiền đã về ví MoMo" / "Dự kiến tiền về trước 27/10/2026"
	}
}
