package vn.utetra.dto.response;

import java.math.BigDecimal;

public record AddressRes(
		Integer id,
		String receiverName,
		String receiverPhone,
		String addressLine,
		String province,
		String ward,
		String street,
		String addressType,
		String note,
		BigDecimal latitude,
		BigDecimal longitude,
		boolean isDefault) {
}