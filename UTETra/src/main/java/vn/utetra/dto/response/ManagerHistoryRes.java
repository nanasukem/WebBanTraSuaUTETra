package vn.utetra.dto.response;

import java.util.List;

/** Trang "Đơn hàng của chi nhánh" theo tab trạng thái, có phân trang */
public record ManagerHistoryRes(
		Integer branchId,
		String branchName,
		String status,               // tab đang mở
		String statusLabel,
		List<ManagerTabRes> tabs,     // đủ 7 tab + số đơn
		List<ManagerOrderRes> orders, // đơn của trang hiện tại
		int page,                    // bắt đầu từ 0
		int totalPages,
		long totalElements) {
}