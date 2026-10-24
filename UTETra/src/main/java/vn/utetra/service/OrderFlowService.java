package vn.utetra.service;

import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.OrderListRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.entity.enums.OrderStatus;

import java.util.List;
import java.util.Map;

public interface OrderFlowService {

	List<OrderListRes> myOrders(Integer userId, OrderTab tab);

	Map<String, Long> myOrderCounts(Integer userId);

	OrderDetailRes myOrder(Integer userId, Integer orderId);
	
	OrderDetailRes orderDetail(Integer orderId);
	
	void cancelByCustomer(Integer userId, Integer orderId, String reason);

	void updateStatus(Integer orderId, OrderStatus to, String cancelReason);

	void cancelBySystem(Integer orderId, String reason);
}