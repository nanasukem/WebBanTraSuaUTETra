package vn.utetra.dto.response;

import java.util.List;

public record ManagerBoardRes(
		Integer branchId,
		String branchName,
		List<ManagerOrderRes> newOrders,
		List<ManagerOrderRes> making,
		List<ManagerOrderRes> shipping,
		List<ManagerOrderRes> recent) {
}