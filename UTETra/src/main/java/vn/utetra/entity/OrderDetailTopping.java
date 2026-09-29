package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "OrderDetailToppings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailTopping {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "order_detail_id", nullable = false)
	private OrderDetail orderDetail;

	@ManyToOne
	@JoinColumn(name = "topping_id", nullable = false)
	private Topping topping;

	@Column(nullable = false)
	private BigDecimal price;
}