package vn.utetra.dto.response;

public record ReviewableItemRes(
		Integer orderDetailId,
		Integer orderId,
		Integer productId,
		String productName,
		String productImage,
		String info,
		String deliveredAt,
		boolean reviewed) {
}
