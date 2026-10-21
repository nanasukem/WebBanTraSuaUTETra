package vn.utetra.dto.request;

import java.util.List;

/** Gửi đánh giá cả đơn 1 lượt: các ly (chỉ ly có chấm sao mới lưu) + đánh giá quán và góp ý */
public record OrderReviewReq(List<ReviewItemReq> items, Integer storeRating, List<String> storeTags,
		String storeComment) {
}
