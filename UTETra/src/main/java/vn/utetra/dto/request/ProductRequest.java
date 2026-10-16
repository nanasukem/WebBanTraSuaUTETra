package vn.utetra.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;

/** Form Thêm/Sửa món trong menu chung (không gắn chi nhánh) */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
	private String name;
	private BigDecimal basePrice;
	private String description;
	private Integer categoryId;
	private Boolean isAvailable;
	private MultipartFile imageFile;
}