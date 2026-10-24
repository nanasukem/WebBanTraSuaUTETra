package vn.utetra.dto.response;

import java.util.List;

public record OrderReviewPageRes(
		Integer orderId,
		boolean pickup,
		String branchName,
		Integer branchId,
		String branchImage,
		String deliveredAt,
		String deadline,
		boolean canReview,
		List<Item> items,
		int pendingCount,
		boolean storeReviewed,
		Integer storeRating,
		List<String> storeTags,
		String storeComment,
		List<ReviewRes.Media> storeMedia) {

	public record Item(
			Integer orderDetailId,
			Integer productId,
			String productName,
			String productImage,
			String info,
			int quantity,
			boolean reviewed,
			Integer rating,
			List<String> tags,
			String content,
			List<ReviewRes.Media> media) {
	}
}