package vn.utetra.dto.response;

import java.util.List;

/**
 * Trang "Đánh giá đơn hàng" (như app Katinat / Highlands): chấm sao từng món của đơn trong 1 lượt
 * + đánh giá quán (phục vụ, đóng gói) và góp ý.
 */
public record OrderReviewPageRes(
		Integer orderId,
		boolean pickup,
		String branchName,
		Integer branchId,           // để mở trang đánh giá công khai của quán
		String branchImage,         // ảnh chi nhánh (null -> hiện icon quán)
		String deliveredAt,
		String deadline,            // hạn đánh giá (30 ngày sau khi nhận)
		boolean canReview,          // đơn đã giao + còn hạn
		List<Item> items,
		int pendingCount,           // số ly chưa đánh giá
		boolean storeReviewed,      // đã đánh giá quán chưa
		Integer storeRating,
		List<String> storeTags,
		String storeComment) {

	/** 1 ly trong đơn (đã đánh giá thì kèm nội dung đã gửi) */
	public record Item(
			Integer orderDetailId,
			Integer productId,
			String productName,
			String productImage,
			String info,            // "Size M · 50% đường · 100% đá"
			int quantity,
			boolean reviewed,
			Integer rating,
			List<String> tags,
			String content,
			List<ReviewRes.Media> media) {
	}
}