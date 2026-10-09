package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ReviewMedia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewMedia {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "review_id", nullable = false)
	private Review review;

	@Column(name = "cloudinary_id", nullable = false, length = 255)
	private String cloudinaryId;

	@Column(name = "media_type", nullable = false, length = 10)
	private String mediaType; // IMAGE, VIDEO
}