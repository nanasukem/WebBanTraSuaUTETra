package vn.utetra.dto.response;

import java.util.List;

/** 1 đánh giá + bình luận của khách đã mua (kèm ảnh/video) */
public record ReviewRes(
		Integer id,
		String reviewerName,
		String reviewerAvatar,
		int rating,
		String content,
		String createdAt,
		String purchasedInfo,      // "Size L · 70% đường · 50% đá"
		List<Media> media) {

	/** type: IMAGE | VIDEO */
	public record Media(String url, String type) {
	}
}
