package vn.utetra.dto.response;

import java.math.BigDecimal;

public record NearbyBranchRes(
		Integer id,
		String name,
		String address,
		String phone,
		BigDecimal latitude,
		BigDecimal longitude,
		Double distanceKm,
		boolean open,
		String hours,
		String openStatus) {
}