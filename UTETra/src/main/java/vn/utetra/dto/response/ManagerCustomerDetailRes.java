package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ManagerCustomerDetailRes(
		Integer branchId,
		String branchName,
		ManagerCustomerRes customer,
		List<OrderRow> orders) {

	public record OrderRow(
			Integer id,
			String time,
			boolean pickup,
			String status,
			String statusLabel,
			String badgeClass,
			int totalQty,
			BigDecimal totalAmount) {
	}
}
