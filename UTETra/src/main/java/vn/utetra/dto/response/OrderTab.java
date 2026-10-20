package vn.utetra.dto.response;

import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

/**
 * 6 tab "Đơn hàng của tôi" theo đề. Tab "Đang giao" gồm cả PICKED_UP (shipper
 * đã lấy hàng) và DELIVERING.
 */
public enum OrderTab {
	PENDING("Chờ xác nhận", List.of(OrderStatus.PENDING)), CONFIRMED("Đã xác nhận", List.of(OrderStatus.CONFIRMED)),
	SHIPPING("Đang giao", List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING)),
	DELIVERED("Đã giao", List.of(OrderStatus.DELIVERED)), CANCELLED("Đã huỷ", List.of(OrderStatus.CANCELLED)),
	RETURNED("Trả hàng", List.of(OrderStatus.RETURNED));

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

	/** "?tab=shipping" -> SHIPPING; sai hoặc trống -> PENDING */
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