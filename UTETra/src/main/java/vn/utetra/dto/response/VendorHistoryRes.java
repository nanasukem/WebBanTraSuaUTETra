package vn.utetra.dto.response;

import java.util.List;

/** Trang "Đơn hàng của shop" theo 7 trạng thái của đề, có phân trang */
public record VendorHistoryRes(
		Integer branchId,
		String branchName,
		String status,               // tab đang mở
		String statusLabel,
		List<VendorTabRes> tabs,     // đủ 7 tab + số đơn
		List<VendorOrderRes> orders, // đơn của trang hiện tại
		int page,                    // bắt đầu từ 0
		int totalPages,
		long totalElements) {
}