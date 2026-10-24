package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
	private String size = "M";

	@Column(name = "sugar_level", nullable = false)
	private Integer sugarLevel = 100;

	@Column(name = "ice_level", nullable = false)
	private Integer iceLevel = 100;

	@Column(name = "quantity", nullable = false)
	private Integer quantity;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
	private BigDecimal unitPrice;

	@Column(name = "subtotal", nullable = false, precision = 12, scale = 0)
	private BigDecimal subtotal;

	@Column(length = 200)
	private String note;

	@OneToMany(mappedBy = "orderDetail", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderDetailTopping> toppings = new ArrayList<>();

	public void addTopping(OrderDetailTopping t) {
		t.setOrderDetail(this);
		toppings.add(t);
	}
}