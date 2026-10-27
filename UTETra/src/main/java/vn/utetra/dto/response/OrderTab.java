package vn.utetra.dto.response;

import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

public enum OrderTab {
	PENDING("Chờ xác nhận", List.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING)),
	CONFIRMED("Đang chuẩn bị", List.of(OrderStatus.CONFIRMED, OrderStatus.READY)),
	SHIPPING("Đang giao", List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING)),
	ISSUE("Đang giải quyết", List.of(OrderStatus.DELIVERED)),
	DELIVERED("Hoàn thành", List.of(OrderStatus.DELIVERED)), CANCELLED("Đã huỷ / Giao thất bại", List.of(OrderStatus.CANCELLED, OrderStatus.RETURNED));

	private final String label;
	private final List<OrderStatus> statuses;

	OrderTab(String label, List<OrderStatus> statuses) {
		this.label = label;
		this.statuses = statuses;
	}

	public String getLabel() {
		return label;
	}

	public List<OrderStatus> getStatuses() {
		return statuses;
	}

	public static OrderTab parse(String value) {
		if (value == null || value.isBlank())
			return PENDING;
		try {
			return valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return PENDING;
		}
	}
}