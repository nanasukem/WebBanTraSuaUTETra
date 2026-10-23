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
		String note,            // ghi chú món (null = không có)
		boolean toppingItem,    // true = topping mua riêng (không có size / đường / đá)
		String stopNote,        // != null: "Đã ngừng bán" / "Topping X đã ngừng bán" (Admin tắt toàn chuỗi)
		String outNote,         // != null: "Hết ở UTETra Q1" / "Topping X hết ở ..." (chi nhánh báo hết)
		boolean blocked) {      // true = phải xoá món này mới thanh toán được (ngừng bán / hết ở mọi chi nhánh)

	public record ToppingRes(Integer id, String name, BigDecimal price) {
	}
}