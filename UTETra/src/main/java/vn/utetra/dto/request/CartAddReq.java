package vn.utetra.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Dữ liệu JS gửi lên khi bấm "Thêm vào giỏ" - KHÔNG có giá tiền */
@Data
public class CartAddReq {

	@NotNull(message = "Thiếu mã món")
	private Integer productId;

	@Pattern(regexp = "^[SMLsml]$", message = "Size phải là S, M hoặc L")
	private String size = "M";

	private Integer sugarLevel = 100;

	private Integer iceLevel = 100;

	private List<Integer> toppingIds = new ArrayList<>();

	@Min(value = 1, message = "Số lượng ít nhất là 1")
	@Max(value = 50, message = "Mỗi món tối đa 50 ly")
	private Integer quantity = 1;

	/** Ghi chú món (không bắt buộc), tối đa 200 ký tự */
	@Size(max = 200, message = "Ghi chú món tối đa 200 ký tự")
	private String note;
}