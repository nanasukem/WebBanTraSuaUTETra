package vn.utetra.dto.response;

import java.math.BigDecimal;

public record ManagerCustomerRes(
		Integer userId,
		String fullName,
		String username,
		String phone,
		String tierName,
		long orderCount,
		long deliveredCount,
		long cancelledCount,
		BigDecimal totalSpent,
		String lastOrderAt) {
}
