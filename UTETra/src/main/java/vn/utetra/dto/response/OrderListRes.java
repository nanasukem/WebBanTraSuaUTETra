package vn.utetra.dto.response;

import java.math.BigDecimal;

/** 1 đơn trong danh sách "Đơn hàng của tôi" */
public record OrderListRes(
		Integer id,
		String branchName,
		boolean pickup,            // true = đến lấy tại cửa hàng
		String status,             // tên enum: PENDING, CONFIRMED...
		String statusLabel,        // chữ hiển thị: "Chờ xác nhận"...
		String badgeClass,         // màu badge Bootstrap
		int totalQty,
		String firstItem,          // "2× Cà phê muối (L)"
		int moreItems,             // còn bao nhiêu món khác
		BigDecimal totalAmount,
		String paymentMethodLabel,
		String createdAt,          // "14:05 20/10/2026"
		boolean canCancel,
		String cancelledBy,        // CUSTOMER / STORE / SYSTEM (null = chưa huỷ)
		int toReview) {            // đơn đã giao: số ly còn chờ đánh giá (0 = đã đánh giá hết / quá hạn)
}