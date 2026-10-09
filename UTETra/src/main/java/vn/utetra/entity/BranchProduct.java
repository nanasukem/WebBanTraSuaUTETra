package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "BranchProducts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchProduct {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true; // Trạng thái còn hoặc hết hàng riêng cho từng chi nhánh
}