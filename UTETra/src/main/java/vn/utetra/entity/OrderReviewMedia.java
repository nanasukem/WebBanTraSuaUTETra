package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "OrderReviewMedia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderReviewMedia {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "order_review_id", nullable = false)
	private Integer orderReviewId;

	@Column(name = "cloudinary_id", nullable = false, length = 255)
	private String cloudinaryId;

	@Column(name = "media_type", nullable = false, length = 10)
	private String mediaType;
}
