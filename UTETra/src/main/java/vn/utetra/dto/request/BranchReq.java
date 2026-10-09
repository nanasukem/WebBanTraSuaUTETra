package vn.utetra.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Dữ liệu form Thêm/Sửa chi nhánh (Admin/Manager) */
@Data
public class BranchReq {

	@NotBlank(message = "Tên chi nhánh không được để trống")
	@Size(max = 100, message = "Tên tối đa 100 ký tự")
	private String name;

	@NotBlank(message = "Địa chỉ không được để trống")
	@Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
	private String address;

	@Pattern(regexp = "^$|^0\\d{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và có 10-11 số")
	private String phone;

	@Size(max = 500, message = "Mô tả tối đa 500 ký tự")
	private String description;

	@NotNull(message = "Vui lòng chọn chủ shop (Vendor)")
	private Integer ownerId;

	@NotNull(message = "Vui lòng nhập % chiết khấu")
	@DecimalMin(value = "0", message = "Chiết khấu không được âm")
	@DecimalMax(value = "100", message = "Chiết khấu tối đa 100%")
	private BigDecimal commissionRate = new BigDecimal("5.00");
}