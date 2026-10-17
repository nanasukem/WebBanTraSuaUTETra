package vn.utetra.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CategoryFormDto {
	private Integer id;
	private String name;
	private MultipartFile imageFile;
	private String existingImageUrl;
}