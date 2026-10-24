package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewRes(List<CartItemRes> items, int totalQty, BigDecimal itemsTotal,
		BigDecimal productDiscount,
		BigDecimal memberDiscount,
		String memberTierName,
		BigDecimal shippingFee,
		BigDecimal totalAmount,
		List<String> unavailableItems,
		List<String> stoppedItems,
		java.util.Map<Integer, String> itemWarnings,
		String productVoucherError,
		List<String> errors,
		boolean canOrder,
		BigDecimal deliveryDistanceKm,
		String carrierName,
		Integer etaMinutes) {
}