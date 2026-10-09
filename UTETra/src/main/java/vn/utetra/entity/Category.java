package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 100, unique = true)
	private String name; // Trà sữa, Trà trái cây, Cà phê, Đá xay...

	@Column(name = "image_cloudinary_id")
	private String imageCloudinaryId;
}