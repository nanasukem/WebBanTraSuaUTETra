package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** 1 dòng trong giỏ hàng gửi về trình duyệt (không gửi thẳng entity để tránh vòng lặp JSON) */
public record CartItemRes(
		Integer id,
		Integer productId,
		String productName,
		String image,
		String size,
		Integer sugarLevel,
		Integer iceLevel,
		List<ToppingRes> toppings,
		Integer quantity,
		BigDecimal unitPrice,   // giá 1 ly = giá món + size + topping
		BigDecimal lineTotal,   // unitPrice * quantity
		String note) {          // ghi chú món (null = không có)

	public record ToppingRes(Integer id, String name, BigDecimal price) {
	}
}