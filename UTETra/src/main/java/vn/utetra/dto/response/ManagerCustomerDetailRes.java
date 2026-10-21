package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** (Chức năng mở rộng) Chi tiết 1 khách: thông tin + các đơn của khách ở chi nhánh của Manager (mới nhất trước) */
public record ManagerCustomerDetailRes(
		Integer branchId,
		String branchName,
		ManagerCustomerRes customer,
		List<OrderRow> orders) {

	/** 1 dòng đơn trong lịch sử mua của khách tại chi nhánh */
	public record OrderRow(
			Integer id,
			String time,           // dd/MM/yyyy HH:mm
			boolean pickup,        // đến lấy tại cửa hàng
			String status,         // tên enum
			String statusLabel,
			String badgeClass,
			int totalQty,          // số ly
			BigDecimal totalAmount) {
	}
}
