package vn.utetra.dto.response;

import java.math.BigDecimal;

/** Chi nhánh kèm khoảng cách tới khách (để chọn chi nhánh gần nhất) */
public record NearbyBranchRes(
		Integer id,
		String name,
		String address,
		String phone,
		BigDecimal latitude,
		BigDecimal longitude,
		Double distanceKm) { // null = không tính được (khách chưa có vị trí / chi nhánh chưa có tọa độ)
}