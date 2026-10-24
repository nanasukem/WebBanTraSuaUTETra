package vn.utetra.dto.response;

import java.math.BigDecimal;
import java.util.List;

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
		BigDecimal unitPrice,
		BigDecimal lineTotal,
		String note,
		boolean toppingItem,
		String stopNote,
		String outNote,
		boolean blocked) {

	public record ToppingRes(Integer id, String name, BigDecimal price) {
	}
}