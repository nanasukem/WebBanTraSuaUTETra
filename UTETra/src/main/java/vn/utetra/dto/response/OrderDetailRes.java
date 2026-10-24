package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record OrderDetailRes(
		Integer id,
		String status,
		String statusLabel,
		String badgeClass,
		boolean pickup,
		List<Step> steps,
		String branchName,
		String branchAddress,
		String branchPhone,
		String receiverName,
		String receiverPhone,
		String deliveryAddress,
		String deliveryNote,
		String carrierName,
		String shipperName,
		String shipperPhone,
		String note,
		List<Item> items,
		int totalQty,
		BigDecimal itemsTotal,
		BigDecimal productDiscount,
		String productVoucherCode,
		BigDecimal memberDiscount,
		String memberTierName,
		BigDecimal shippingFee,
		BigDecimal totalAmount,
		String paymentMethodLabel,
		String paymentStatusLabel,
		String cancelReason,
		String createdAt,
		String deliveredAt,
		boolean canCancel,
		String cancelledBy,
		PaymentInfoRes payment,
		OrderIssueRes issue,
		boolean canReportIssue,
		long issueDeadlineMillis,
		String etaLabel) {

	public record Item(
			String productName,
			String size,
			String sugarIce,
			String toppings,
			int quantity,
			BigDecimal unitPrice,
			BigDecimal subtotal,
			String note) {
	}

	public record Step(String label, boolean done, boolean current) {
	}
}