package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Bảng OrderDetails - mỗi dòng là 1 loại ly trong đơn */
@Entity
@Table(name = "OrderDetails")
@Getter
@Setter
@NoArgsConstructor
public class OrderDetail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "size", nullable = false, length = 1)
	private String size = "M"; // S, M, L

	@Column(name = "sugar_level", nullable = false)
	private Integer sugarLevel = 100; // 0, 30, 50, 70, 100

	@Column(name = "ice_level", nullable = false)
	private Integer iceLevel = 100; // 0, 30, 50, 70, 100

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
	private BigDecimal unitPrice; // giá 1 ly = base + size + topping, chốt lúc đặt

	@Column(name = "subtotal", nullable = false, precision = 12, scale = 0)
	private BigDecimal subtotal; // unitPrice * quantity

	@OneToMany(mappedBy = "orderDetail", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderDetailTopping> toppings = new ArrayList<>();

	public void addTopping(OrderDetailTopping t) {
		t.setOrderDetail(this);
		toppings.add(t);
	}
}