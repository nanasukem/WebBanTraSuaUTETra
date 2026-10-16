package vn.utetra.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Data
public class ProductFormDto {
	private Integer id;
	private String name;
	private Integer categoryId;
	private BigDecimal basePrice;
	private String description;
	private MultipartFile imageFile;
}