package vn.utetra.service;

import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.OrderListRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.entity.enums.OrderStatus;

import java.util.List;
import java.util.Map;

/**
 * Vòng đời đơn hàng sau khi đặt:
 * - Khách: xem đơn theo 6 tab, xem chi tiết, huỷ đơn khi còn Chờ xác nhận.
 * - Đổi trạng thái (bước 7 Barista/Manager và Shipper gọi lại hàm updateStatus).
 */
public interface OrderFlowService {

	/** Đơn của khách theo tab, mới nhất lên đầu */
	List<OrderListRes> myOrders(Integer userId, OrderTab tab);

	/** Số đơn mỗi tab: {"PENDING": 1, "SHIPPING": 0, ...} */
	Map<String, Long> myOrderCounts(Integer userId);

	/** Chi tiết 1 đơn của chính khách (đơn người khác -> ResourceNotFoundException) */
	OrderDetailRes myOrder(Integer userId, Integer orderId);
	
	/** Chi tiết 1 đơn, KHÔNG kiểm tra người xem (nơi gọi tự kiểm tra quyền, vd Manager chỉ xem đơn chi nhánh mình) */
	OrderDetailRes orderDetail(Integer orderId);
	
	/** Khách huỷ đơn: chỉ khi PENDING, bắt buộc lý do, hoàn lượt voucher */
	void cancelByCustomer(Integer userId, Integer orderId, String reason);

	/**
	 * Đổi trạng thái theo đúng luồng của đề (không kiểm tra quyền, controller tự kiểm tra).
	 * Giao tận nơi: PENDING -> CONFIRMED -> PICKED_UP -> DELIVERING -> DELIVERED
	 * Đến lấy:      PENDING -> CONFIRMED -> DELIVERED
	 * PENDING -> CANCELLED (cancelReason bắt buộc)
	 */
	void updateStatus(Integer orderId, OrderStatus to, String cancelReason);
}