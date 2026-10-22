package vn.utetra.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Dữ liệu form Thêm/Sửa đối tác giao hàng */
@Data
public class CarrierReq {

	@NotBlank(message = "Tên đối tác giao hàng không được để trống")
	@Size(max = 100, message = "Tên tối đa 100 ký tự")
	private String name;

	/** Phí cho các km đầu (OrderServiceImpl.BASE_KM) */
	@NotNull(message = "Nhập phí cho các km đầu")
	@DecimalMin(value = "0", message = "Phí giao không được âm")
	@Digits(integer = 12, fraction = 0, message = "Phí giao phải là số nguyên (đồng)")
	private BigDecimal fee;

	/** Phí mỗi km thêm */
	@NotNull(message = "Nhập phí mỗi km thêm")
	@DecimalMin(value = "0", message = "Phí mỗi km không được âm")
	@Digits(integer = 12, fraction = 0, message = "Phí mỗi km phải là số nguyên (đồng)")
	private BigDecimal perKmFee = BigDecimal.ZERO;
}