package vn.utetra.dto.response;

import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

/**
 * Tab "Đơn hàng của tôi" giống app thật: Chờ xác nhận (gồm cả đơn Chờ thanh toán VNPAY/MoMo) -> Đang chuẩn bị
 * (quán đang pha + pha xong chờ lấy / chờ shipper) -> Đang giao (shipper đã lấy + đang giao) -> Hoàn thành -> Đã huỷ.
 * Đồ uống không trả hàng: khách "Báo vấn đề với đơn" (OrderIssueService). Đơn đang chờ quán xử lý nằm ở tab
 * "Đang giải quyết", quán xử lý xong mới về "Hoàn thành".
 */
public enum OrderTab {
	PENDING("Chờ xác nhận", List.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.PENDING)),
	CONFIRMED("Đang chuẩn bị", List.of(OrderStatus.CONFIRMED, OrderStatus.READY)),
	SHIPPING("Đang giao", List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING)),
	// Đơn đã nhận nhưng khách báo vấn đề, quán chưa xử lý xong -> chưa tính là Hoàn thành (OrderFlowServiceImpl lọc)
	ISSUE("Đang giải quyết", List.of(OrderStatus.DELIVERED)),
	DELIVERED("Hoàn thành", List.of(OrderStatus.DELIVERED)), CANCELLED("Đã huỷ", List.of(OrderStatus.CANCELLED));

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