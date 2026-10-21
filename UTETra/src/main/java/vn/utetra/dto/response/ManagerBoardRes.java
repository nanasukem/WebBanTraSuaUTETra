package vn.utetra.dto.response;

import java.util.List;

/** Màn hình Quầy đơn hàng: đơn của 1 chi nhánh chia theo cột */
public record ManagerBoardRes(
		Integer branchId,
		String branchName,
		List<ManagerOrderRes> newOrders,  // PENDING: đơn mới, chờ xác nhận
		List<ManagerOrderRes> making,     // CONFIRMED: đang pha chế
		List<ManagerOrderRes> shipping,   // PICKED_UP, DELIVERING: shipper đang giao
		List<ManagerOrderRes> recent) {   // đã xong / đã huỷ gần đây
}