package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ManagerOrderRes(
		Integer id,
		String time,
		boolean pickup,
		String status,
		String statusLabel,
		String badgeClass,
		String receiverName,
		String receiverPhone,
		String deliveryAddress,
		String deliveryNote,
		String note,
		List<String> items,
		int totalQty,
		BigDecimal totalAmount,
		String paymentLabel,
		String hint,
		List<Action> actions,
		boolean canCancel,
		boolean prepaid,
		String issueLabel,
		boolean issuePending,
		String shipperLabel) {

	public record Action(String to, String label, String style) {
	}
}