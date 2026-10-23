package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tình trạng topping theo chi nhánh (Manager báo "hết topping" cho riêng chi nhánh mình).
 * Không có dòng cho (chi nhánh, topping) = topping đang bán ở chi nhánh đó.
 */
@Entity
@Table(name = "BranchToppings", uniqueConstraints = @UniqueConstraint(columnNames = { "branch_id", "topping_id" }))
@Getter
@Setter
@NoArgsConstructor
public class BranchTopping {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "topping_id", nullable = false)
	private Topping topping;

	@Column(name = "is_available", nullable = false)
	private Boolean isAvailable = true;
}
