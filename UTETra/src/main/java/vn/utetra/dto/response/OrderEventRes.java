package vn.utetra.dto.response;

/**
 * Tin nhắn WebSocket đẩy xuống trình duyệt khi đơn có thay đổi.
 * type: NEW = đơn mới (gửi quán), STATUS = đổi trạng thái (gửi cả khách và quán)
 */
public record OrderEventRes(
		String type,
		Integer orderId,
		Integer branchId,
		String status,
		String statusLabel,
		String title,
		String message) {
}