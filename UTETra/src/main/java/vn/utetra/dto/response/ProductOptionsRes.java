package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Tùy chọn của 1 món để vẽ modal: giá cộng thêm theo size + danh sách topping đang bán */
/** toppingItem = true: topping mua riêng -> modal ẩn size / đường / đá / topping / ghi chú vị */
public record ProductOptionsRes(Map<String, BigDecimal> sizes, List<CartItemRes.ToppingRes> toppings,
		boolean toppingItem,
		List<CartItemRes.ToppingRes> stoppedToppings, // topping Admin đã ngừng bán: hiện mờ "Đã ngừng bán"
		List<CartItemRes.ToppingRes> outToppings,     // topping hết ở chi nhánh đang chọn: hiện mờ "Hết ở chi nhánh"
		String branchName) {                          // chi nhánh đang chọn (null = chọn khi thanh toán)
}