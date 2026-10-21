package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trang chi tiết 1 món.
 * starCounts: số lượt đánh giá theo sao, phần tử 0 = 5 sao, 1 = 4 sao ... 4 = 1 sao.
 */
public record ProductDetailRes(
		Integer id,
		String name,
		Integer categoryId,
		String categoryName,
		BigDecimal basePrice,
		String imageCloudinaryId,
		String description,
		boolean available,
		int soldCount,
		double avgRating,
		long reviewCount,
		long favoriteCount,
		List<SizeOption> sizes,
		List<Long> starCounts) {

	/** Số sao tô đầy (làm tròn điểm trung bình), vd 4.6 -> 5 */
	public int fullStars() {
		return (int) Math.max(0, Math.min(5, Math.round(avgRating)));
	}

	/** 1 size: S/M/L + giá 1 ly của size đó (giá gốc + phụ thu) */
	public record SizeOption(String size, BigDecimal price) {
	}
}
