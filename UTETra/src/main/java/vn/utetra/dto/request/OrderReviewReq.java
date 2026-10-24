package vn.utetra.dto.request;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public record OrderReviewReq(List<ReviewItemReq> items, Integer storeRating, List<String> storeTags,
		String storeComment, List<MultipartFile> storeMedia) {

	public OrderReviewReq(List<ReviewItemReq> items, Integer storeRating, List<String> storeTags, String storeComment) {
		this(items, storeRating, storeTags, storeComment, List.of());
	}
}
