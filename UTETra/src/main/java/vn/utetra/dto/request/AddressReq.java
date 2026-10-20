package vn.utetra.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/** Thêm / sửa địa chỉ nhận hàng (kiểu Shopee: Tỉnh + Phường + Tên đường; server tự ghép address_line) */
@Data
public class AddressReq {

	@NotBlank(message = "Vui lòng nhập họ và tên người nhận")
	@Size(max = 100, message = "Tên tối đa 100 ký tự")
	private String receiverName;

	@NotBlank(message = "Vui lòng nhập số điện thoại")
	@Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 số, bắt đầu bằng 0")
	private String receiverPhone;

	// ----- Kiểu mới (giống Shopee) -----
	@Size(max = 100, message = "Tên Tỉnh/Thành phố quá dài")
	private String province;

	@Size(max = 100, message = "Tên Phường/Xã quá dài")
	private String ward;

	@Size(max = 150, message = "Tên đường, toà nhà, số nhà tối đa 150 ký tự")
	private String street;

	// ----- Kiểu cũ: 1 dòng địa chỉ (bỏ trống nếu đã gửi đủ 3 ô trên) -----
	@Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
	private String addressLine;

	@DecimalMin(value = "-90", message = "Vĩ độ không hợp lệ")
	@DecimalMax(value = "90", message = "Vĩ độ không hợp lệ")
	private BigDecimal latitude;

	@DecimalMin(value = "-180", message = "Kinh độ không hợp lệ")
	@DecimalMax(value = "180", message = "Kinh độ không hợp lệ")
	private BigDecimal longitude;

	@Pattern(regexp = "^(HOME|OFFICE)?$", message = "Loại địa chỉ không hợp lệ")
	private String addressType; // HOME = Nhà riêng, OFFICE = Văn phòng
	
	@Size(max = 200)
	private String note;
	
	private Boolean isDefault = false;
}