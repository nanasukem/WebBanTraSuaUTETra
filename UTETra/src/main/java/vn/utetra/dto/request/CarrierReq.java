package vn.utetra.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Dữ liệu form Thêm/Sửa nhà vận chuyển */
@Data
public class CarrierReq {

	@NotBlank(message = "Tên nhà vận chuyển không được để trống")
	@Size(max = 100, message = "Tên tối đa 100 ký tự")
	private String name;

	@NotNull(message = "Nhập phí vận chuyển")
	@DecimalMin(value = "0", message = "Phí vận chuyển không được âm")
	@Digits(integer = 12, fraction = 0, message = "Phí vận chuyển phải là số nguyên (đồng)")
	private BigDecimal fee;
}