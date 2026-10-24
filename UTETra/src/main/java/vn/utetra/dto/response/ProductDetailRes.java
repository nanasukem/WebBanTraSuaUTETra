package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

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

	public int fullStars() {
		return (int) Math.max(0, Math.min(5, Math.round(avgRating)));
	}

	public record SizeOption(String size, BigDecimal price) {
	}
}
