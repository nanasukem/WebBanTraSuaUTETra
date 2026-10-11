package vn.utetra.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
	private String name;
	private BigDecimal basePrice;
	private String description;
	private Integer branchId;
	private Integer categoryId;
	private Boolean isAvailable;
	private MultipartFile imageFile;
}