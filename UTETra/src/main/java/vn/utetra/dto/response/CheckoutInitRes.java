package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Dữ liệu ban đầu của trang Checkout */
public record CheckoutInitRes(
		String receiverName,          // điền sẵn cho "Đến lấy"
		String receiverPhone,
		List<AddressRes> addresses,
		List<CarrierOption> carriers,
		MemberTierInfo tier,
		boolean newCustomer) {

	public record CarrierOption(Integer id, String name, BigDecimal fee) {
	}
}