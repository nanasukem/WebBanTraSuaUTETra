package vn.utetra.dto.response;

import java.math.BigDecimal;

/**
 * (Chức năng mở rộng) 1 khách trong trang "Khách hàng của chi nhánh" của Manager.
 * Chỉ số liệu ở chi nhánh của Manager; không có email, địa chỉ (Manager chỉ xem, không quản lý tài khoản khách).
 */
public record ManagerCustomerRes(
		Integer userId,
		String fullName,
		String username,
		String phone,
		String tierName,          // hạng thành viên hiện tại (tính trên toàn chuỗi)
		long orderCount,          // số đơn ở chi nhánh này (mọi trạng thái)
		long deliveredCount,      // số đơn đã giao / đã nhận
		long cancelledCount,      // số đơn đã huỷ
		BigDecimal totalSpent,    // tổng tiền các đơn đã giao ở chi nhánh này
		String lastOrderAt) {     // lần đặt gần nhất ở chi nhánh này, dd/MM/yyyy HH:mm
}
