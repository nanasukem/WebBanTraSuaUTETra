package vn.utetra.dto.response;

import java.util.List;

public record ManagerHistoryRes(
		Integer branchId,
		String branchName,
		String status,
		String statusLabel,
		List<ManagerTabRes> tabs,
		List<ManagerOrderRes> orders,
		int page,
		int totalPages,
		long totalElements) {
}