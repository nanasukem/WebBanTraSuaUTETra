package vn.utetra.dto.response;

/** 1 ly khách đã mua và đã nhận (đơn Đã giao) - đánh giá được nếu reviewed = false */
public record ReviewableItemRes(
		Integer orderDetailId,
		Integer orderId,
		Integer productId,
		String productName,
		String productImage,
		String info,          // "Size L · 70% đường · 50% đá"
		String deliveredAt,
		boolean reviewed) {
}
