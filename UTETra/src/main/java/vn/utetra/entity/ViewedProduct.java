package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ViewedProducts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViewedProduct {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "viewed_at", nullable = false)
	private LocalDateTime viewedAt = LocalDateTime.now();
}