package vn.utetra.dto.response;

import java.util.List;

/** Màn hình Quầy đơn hàng: đơn của 1 chi nhánh chia theo cột */
public record VendorBoardRes(
		Integer branchId,
		String branchName,
		List<VendorOrderRes> newOrders,  // PENDING: đơn mới, chờ xác nhận
		List<VendorOrderRes> making,     // CONFIRMED: đang pha chế
		List<VendorOrderRes> shipping,   // PICKED_UP, DELIVERING: shipper đang giao
		List<VendorOrderRes> recent) {   // đã xong / đã huỷ gần đây
}