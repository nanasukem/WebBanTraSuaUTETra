package vn.utetra.dto.request;

import vn.utetra.entity.enums.DiscountType;
import vn.utetra.entity.enums.VoucherApplyTo;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Dữ liệu form Thêm/Sửa voucher */
@Data
public class VoucherReq {

	@NotBlank(message = "Mã voucher không được để trống")
	@Pattern(regexp = "^[A-Za-z0-9]{4,30}$", message = "Mã gồm 4-30 chữ cái hoặc số, không dấu, không khoảng trắng")
	private String code;

	@NotBlank(message = "Tên chương trình không được để trống")
	@Size(max = 150, message = "Tên tối đa 150 ký tự")
	private String name;

	/** null = voucher của app; có giá trị = voucher của shop */
	private Integer branchId;

	@NotNull(message = "Chọn loại áp dụng")
	private VoucherApplyTo applyTo;

	@NotNull(message = "Chọn kiểu giảm")
	private DiscountType discountType;

	@NotNull(message = "Nhập giá trị giảm")
	@DecimalMin(value = "1", message = "Giá trị giảm phải lớn hơn 0")
	private BigDecimal discountValue;

	@DecimalMin(value = "0", message = "Mức giảm tối đa không được âm")
	private BigDecimal maxDiscount;

	@NotNull(message = "Nhập giá trị đơn tối thiểu")
	@DecimalMin(value = "0", message = "Đơn tối thiểu không được âm")
	private BigDecimal minOrderValue = BigDecimal.ZERO;

	@NotNull(message = "Nhập số lượng")
	@Min(value = 1, message = "Số lượng ít nhất là 1")
	private Integer quantity;

	@NotNull(message = "Chọn ngày bắt đầu")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime startDate;

	@NotNull(message = "Chọn ngày kết thúc")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime endDate;

	@AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu")
	public boolean isDateRangeValid() {
		return startDate == null || endDate == null || endDate.isAfter(startDate);
	}

	@AssertTrue(message = "Giảm theo % thì giá trị phải từ 1 đến 100")
	public boolean isPercentValid() {
		return discountType != DiscountType.PERCENT || discountValue == null
				|| discountValue.compareTo(BigDecimal.valueOf(100)) <= 0;
	}
}