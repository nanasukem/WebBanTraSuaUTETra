package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Bảng OrderDetailToppings - topping đi kèm 1 ly, lưu giá lúc đặt */
@Entity
@Table(name = "OrderDetailToppings")
@Getter
@Setter
@NoArgsConstructor
public class OrderDetailTopping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_detail_id", nullable = false)
	private OrderDetail orderDetail;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "topping_id", nullable = false)
	private Topping topping;

	@Column(name = "price", nullable = false, precision = 12, scale = 0)
	private BigDecimal price;
}