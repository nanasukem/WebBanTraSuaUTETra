package vn.utetra.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;
import vn.utetra.entity.enums.BranchStatus;

import java.math.BigDecimal;

/** Dữ liệu form Thêm/Sửa chi nhánh (Admin) */
@Data
public class BranchReq {

	private Integer id;

	@NotBlank(message = "Tên chi nhánh không được để trống")
	@Size(max = 100, message = "Tên tối đa 100 ký tự")
	private String name;

	@NotBlank(message = "Địa chỉ không được để trống")
	@Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
	private String address;
	
	@DecimalMin(value = "-90", message = "Vĩ độ phải từ -90 đến 90")
	@DecimalMax(value = "90", message = "Vĩ độ phải từ -90 đến 90")
	private BigDecimal latitude;

	@DecimalMin(value = "-180", message = "Kinh độ phải từ -180 đến 180")
	@DecimalMax(value = "180", message = "Kinh độ phải từ -180 đến 180")
	private BigDecimal longitude;

	@Pattern(regexp = "^$|^0\\d{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và có 10-11 số")
	private String phone;

	@Size(max = 500, message = "Mô tả tối đa 500 ký tự")
	private String description;

	/** Giờ mở cửa (mặc định 7:00 - 22:00) */
	@org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm")
	private java.time.LocalTime openTime = java.time.LocalTime.of(7, 0);

	@org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm")
	private java.time.LocalTime closeTime = java.time.LocalTime.of(22, 0);

	@NotNull(message = "Vui lòng chọn Manager quản lý chi nhánh")
	private Integer ownerId;


	private BranchStatus status;

	private String imageCloudinaryId;

	private MultipartFile imageFile;
}