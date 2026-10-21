package vn.utetra.dto.response;

import java.math.BigDecimal;

/**
 * 1 thẻ món trên trang Thực đơn (dùng chung cho các khối Bán chạy, Mới nhất, Đánh giá cao, Yêu thích nhất
 * và danh sách toàn bộ thực đơn). Tên thuộc tính giống entity Product để template dùng chung.
 */
public record ProductCardRes(
		Integer id,
		String name,
		String categoryName,
		BigDecimal basePrice,
		String imageCloudinaryId,
		String description,
		int soldCount,
		double avgRating,     // điểm đánh giá trung bình (0 = chưa có đánh giá)
		long reviewCount,     // số lượt đánh giá
		long favoriteCount) { // số lượt thích
}
