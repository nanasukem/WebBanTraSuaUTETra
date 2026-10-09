package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "CartItemToppings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItemTopping {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "cart_item_id", nullable = false)
	private CartItem cartItem;

	@ManyToOne
	@JoinColumn(name = "topping_id", nullable = false)
	private Topping topping;
}