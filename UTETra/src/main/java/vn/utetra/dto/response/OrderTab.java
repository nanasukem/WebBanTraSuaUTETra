package vn.utetra.dto.response;

import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

/**
 * Tab "Đơn hàng của tôi" giống app thật: Chờ xác nhận (gồm cả đơn Chờ thanh toán VNPAY/MoMo) -> Đang chuẩn bị
 * (quán đang pha + pha xong chờ lấy / chờ shipper) -> Đang giao (shipper đã lấy + đang giao) -> Hoàn thành -> Đã huỷ.
 * Tab "Trả hàng - hoàn tiền" chỉ hiện khi có đơn.
 */
public enum OrderTab {
	PENDING("Chờ xác nhận", List.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING)),
	CONFIRMED("Đang chuẩn bị", List.of(OrderStatus.CONFIRMED, OrderStatus.READY)),
	SHIPPING("Đang giao", List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING)),
	DELIVERED("Hoàn thành", List.of(OrderStatus.DELIVERED)), CANCELLED("Đã huỷ", List.of(OrderStatus.CANCELLED)),
	RETURNED("Trả hàng - hoàn tiền", List.of(OrderStatus.RETURNED));

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