package vn.utetra.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
	private Integer id;
	private String name;
	private String description;
	private BigDecimal basePrice;
	private Integer categoryId;
	private Boolean isAvailable;
	private MultipartFile imageFile;
	private String existingImageUrl;
}