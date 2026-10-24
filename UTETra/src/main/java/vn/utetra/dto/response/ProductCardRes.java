package vn.utetra.dto.response;

import java.math.BigDecimal;

public record ProductCardRes(
		Integer id,
		String name,
		String categoryName,
		BigDecimal basePrice,
		String imageCloudinaryId,
		String description,
		int soldCount,
		double avgRating,
		long reviewCount,
		long favoriteCount,
		boolean available) {
}
