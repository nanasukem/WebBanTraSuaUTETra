package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutInitRes(
		String receiverName,
		String receiverPhone,
		List<AddressRes> addresses,
		MemberTierInfo tier,
		boolean newCustomer,
		java.math.BigDecimal shippingBaseFee,
		java.math.BigDecimal shippingPerKmFee,
		Integer preferredBranchId) {
}