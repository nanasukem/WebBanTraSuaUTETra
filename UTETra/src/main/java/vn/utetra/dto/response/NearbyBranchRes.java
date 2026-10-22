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
		Double distanceKm, // null = không tính được (khách chưa có vị trí / chi nhánh chưa có tọa độ)
		boolean open,      // đang mở cửa không
		String hours,      // "7:00 - 22:00"
		String openStatus) { // "Đang mở cửa · đóng lúc 22:00" / "Đã đóng cửa · mở lại lúc 7:00"
}