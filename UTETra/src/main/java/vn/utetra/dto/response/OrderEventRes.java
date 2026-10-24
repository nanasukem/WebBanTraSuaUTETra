package vn.utetra.dto.response;

public record OrderEventRes(
		String type,
		Integer orderId,
		Integer branchId,
		String status,
		String statusLabel,
		String title,
		String message) {
}