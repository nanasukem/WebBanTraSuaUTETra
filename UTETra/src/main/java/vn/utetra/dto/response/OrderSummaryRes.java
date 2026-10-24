package vn.utetra.dto.response;

import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;

import java.math.BigDecimal;

public record OrderSummaryRes(
		Integer id,
		String branchName,
		String branchAddress,
		String branchPhone,
		DeliveryType deliveryType,
		String receiverName,
		String receiverPhone,
		String deliveryAddress,
		String carrierName,
		BigDecimal totalAmount,
		PaymentMethod paymentMethod,
		OrderStatus status,
		int totalQty,
		String createdAt) {
}