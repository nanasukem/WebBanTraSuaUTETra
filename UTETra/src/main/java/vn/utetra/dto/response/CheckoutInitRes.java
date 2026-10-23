package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Dữ liệu ban đầu của trang Checkout */
public record CheckoutInitRes(
		String receiverName,          // điền sẵn cho "Đến lấy"
		String receiverPhone,
		List<AddressRes> addresses,
		MemberTierInfo tier,
		boolean newCustomer,
		java.math.BigDecimal shippingBaseFee,   // phí đồng giá 3 km đầu (Admin cài)
		java.math.BigDecimal shippingPerKmFee,  // mỗi km thêm
		Integer preferredBranchId) {            // chi nhánh đã chọn ở đầu menu (null = tự chọn gần nhất)
}