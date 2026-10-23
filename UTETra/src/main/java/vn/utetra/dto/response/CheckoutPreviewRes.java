package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Kết quả xem trước ở trang Checkout (tính tiền nhưng KHÔNG lưu đơn) */
public record CheckoutPreviewRes(List<CartItemRes> items, int totalQty, BigDecimal itemsTotal, // tiền món
		BigDecimal productDiscount, // giảm tiền món (mã)
		BigDecimal memberDiscount, // giảm theo hạng thành viên
		String memberTierName, // hạng hiện tại của khách
		BigDecimal shippingFee, // phí ship
		BigDecimal totalAmount, // khách phải trả
		List<String> unavailableItems, // món đã hết ở chi nhánh đã chọn
		String productVoucherError, // lỗi mã giảm món (null = không lỗi)
		List<String> errors, // lỗi khác: chưa chọn chi nhánh, địa chỉ...
		boolean canOrder, // true = bấm "Đặt hàng" được
		BigDecimal deliveryDistanceKm, // khoảng cách chi nhánh -> địa chỉ giao (null = đến lấy / chưa tính được)
		String carrierName) { // "Shipper UTETra" (đơn giao tận nơi), null khi đến lấy
}