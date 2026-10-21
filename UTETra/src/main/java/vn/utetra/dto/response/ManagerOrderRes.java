package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** 1 thẻ đơn trên màn hình Quầy đơn hàng của quán */
public record ManagerOrderRes(
		Integer id,
		String time,               // "14:05"
		boolean pickup,
		String status,
		String statusLabel,
		String badgeClass,
		String receiverName,
		String receiverPhone,
		String deliveryAddress,
		String deliveryNote,
		String note,               // ghi chú cho quán
		List<String> items,        // "2× Cà phê muối · L · 70% đường · 50% đá + Kem cheese"
		int totalQty,
		BigDecimal totalAmount,
		String paymentLabel,
		String hint,               // gợi ý việc cần làm tiếp
		List<Action> actions,      // nút đổi trạng thái 1 click
		boolean canCancel) {

	/** 1 nút: chuyển sang trạng thái "to" */
	public record Action(String to, String label, String style) {
	}
}