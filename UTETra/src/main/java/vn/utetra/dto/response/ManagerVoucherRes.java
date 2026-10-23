package vn.utetra.dto.response;

import java.math.BigDecimal;

/**
 * 1 voucher trên trang "Khuyến mãi của chi nhánh" (Manager CHỈ XEM - Admin tạo / sửa).
 * status: RUNNING | UPCOMING | ENDED | SOLD_OUT | OFF
 */
public record ManagerVoucherRes(
		Integer id,
		String code,
		String name,
		boolean chainWide,          // true = toàn chuỗi, false = riêng chi nhánh này
		String applyToLabel,        // Giảm tiền món
		String discountText,        // "20% (tối đa 30.000đ)" / "10.000đ"
		String conditionText,       // "Đơn từ 50.000đ · Mỗi khách 1 lần · Chỉ VNPAY"
		String period,              // "01/10/2026 – 31/10/2026"
		String status,
		String statusLabel,
		String badgeClass,
		int quantity,
		int usedCount,              // lượt đã dùng toàn hệ thống
		long usedAtBranch,          // lượt dùng tại chi nhánh này (đơn không huỷ)
		BigDecimal discountAtBranch // tổng tiền đã giảm cho khách tại chi nhánh này
) {
}
