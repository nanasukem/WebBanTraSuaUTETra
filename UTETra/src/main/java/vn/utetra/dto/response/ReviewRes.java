package vn.utetra.dto.response;

import java.util.List;

public record ReviewRes(
		Integer id,
		String reviewerName,
		String reviewerAvatar,
		int rating,
		String content,
		String createdAt,
		String purchasedInfo,
		List<String> tags,
		List<Media> media) {

	public record Media(String url, String type) {
	}
}
