package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.response.CartItemRes;
import vn.utetra.dto.response.CartRes;
import vn.utetra.entity.*;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.*;
import vn.utetra.service.CartService;
import vn.utetra.dto.response.ProductOptionsRes;
import vn.utetra.dto.response.ReorderRes;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

	private static final Set<Integer> LEVELS = Set.of(0, 30, 50, 70, 100); // khớp CHECK trong DB
	private static final int MAX_QTY = 50;

	private final CartItemRepository cartItemRepository;
	private final CartItemToppingRepository cartItemToppingRepository;
	private final ProductRepository productRepository;
	private final ProductSizeRepository productSizeRepository;
	private final ToppingRepository toppingRepository;
	private final UserRepository userRepository;
	private final OrderRepository orderRepository;

	public CartServiceImpl(CartItemRepository cartItemRepository, CartItemToppingRepository cartItemToppingRepository,
			ProductRepository productRepository, ProductSizeRepository productSizeRepository,
			ToppingRepository toppingRepository, UserRepository userRepository, OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
		this.cartItemRepository = cartItemRepository;
		this.cartItemToppingRepository = cartItemToppingRepository;
		this.productRepository = productRepository;
		this.productSizeRepository = productSizeRepository;
		this.toppingRepository = toppingRepository;
		this.userRepository = userRepository;
	}

	// ================= ĐỌC GIỎ =================
	@Override
	public CartRes getCart(Integer userId) {
		List<CartItemRes> items = cartItemRepository.findByUserId(userId).stream()
				.sorted(Comparator.comparing(CartItem::getId)).map(this::toRes).toList();
		int totalQty = items.stream().mapToInt(CartItemRes::quantity).sum();
		BigDecimal subtotal = items.stream().map(CartItemRes::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new CartRes(items, totalQty, subtotal);
	}

	// ================= THÊM MÓN =================
	@Override
	@Transactional
	public CartRes addItem(Integer userId, CartAddReq req) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng #" + userId));

		if (req.getProductId() == null)
			throw new BusinessException("Thiếu mã món");
		Product product = productRepository.findById(req.getProductId())
				.orElseThrow(() -> new BusinessException("Món không tồn tại"));
		if (!Boolean.TRUE.equals(product.getIsAvailable()))
			throw new BusinessException("Món " + product.getName() + " đã ngừng bán");

		String size = req.getSize() == null ? "M" : req.getSize().trim().toUpperCase();
		if (productSizeRepository.findByProductIdAndSize(product.getId(), size).isEmpty())
			throw new BusinessException("Món này không có size " + size);

		int sugar = checkLevel(req.getSugarLevel(), "đường");
		int ice = checkLevel(req.getIceLevel(), "đá");
		int qty = req.getQuantity() == null ? 1 : req.getQuantity();
		checkQty(qty);
		List<Topping> toppings = loadToppings(req.getToppingIds());
		Set<Integer> wanted = toppings.stream().map(Topping::getId).collect(Collectors.toSet());
		String note = req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim();
		if (note != null && note.length() > 200) throw new BusinessException("Ghi chú món tối đa 200 ký tự");

		// Trùng cấu hình (cả topping và ghi chú) -> cộng dồn
		for (CartItem ci : cartItemRepository.findAllByUserIdAndProductIdAndSizeAndSugarLevelAndIceLevel(userId,
				product.getId(), size, sugar, ice)) {
			if (toppingIdsOf(ci).equals(wanted) && java.util.Objects.equals(ci.getNote(), note)) {
				int newQty = ci.getQuantity() + qty;
				checkQty(newQty);
				ci.setQuantity(newQty);
				return getCart(userId);
			}
		}

		// Khác cấu hình -> dòng mới
		CartItem ci = new CartItem();
		ci.setUser(user);
		ci.setProduct(product);
		ci.setSize(size);
		ci.setSugarLevel(sugar);
		ci.setIceLevel(ice);
		ci.setQuantity(qty);
		ci.setNote(note);
		cartItemRepository.save(ci);
		for (Topping t : toppings) {
			cartItemToppingRepository.save(new CartItemTopping(null, ci, t));
		}
		return getCart(userId);
	}

	// ================= MUA LẠI =================
	@Override
	@Transactional
	public ReorderRes reorder(Integer userId, Integer orderId) {
		Order order = orderRepository.findById(orderId)
				.filter(o -> o.getUser() != null && o.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn #" + orderId));
		int added = 0;
		List<String> notes = new ArrayList<>();
		for (OrderDetail d : order.getDetails()) {
			String name = d.getProduct().getName();
			CartAddReq req = new CartAddReq();
			req.setProductId(d.getProduct().getId());
			req.setSize(d.getSize());
			req.setSugarLevel(d.getSugarLevel());
			req.setIceLevel(d.getIceLevel());
			req.setQuantity(d.getQuantity());
			req.setNote(d.getNote());
			List<Integer> toppingIds = new ArrayList<>();
			for (OrderDetailTopping t : d.getToppings()) {
				if (Boolean.TRUE.equals(t.getTopping().getIsAvailable()))
					toppingIds.add(t.getTopping().getId());
				else
					notes.add(name + ": topping " + t.getTopping().getName() + " đã hết nên đã bỏ ra");
			}
			req.setToppingIds(toppingIds);
			try {
				addItem(userId, req); // gọi trong cùng class -> lỗi 1 món không làm hỏng cả lượt
				added += d.getQuantity();
			} catch (BusinessException e) {
				notes.add(name + ": " + e.getMessage());
			}
		}
		return new ReorderRes(added, notes);
	}

	// ================= SỬA / XOÁ =================
	@Override
	@Transactional
	public CartRes updateQuantity(Integer userId, Integer cartItemId, Integer quantity) {
		CartItem ci = ownedItem(userId, cartItemId);
		checkQty(quantity == null ? 0 : quantity);
		ci.setQuantity(quantity);
		return getCart(userId);
	}

	@Override
	@Transactional
	public CartRes removeItem(Integer userId, Integer cartItemId) {
		CartItem ci = ownedItem(userId, cartItemId);
		cartItemToppingRepository.deleteByCartItemId(ci.getId());
		cartItemRepository.delete(ci);
		return getCart(userId);
	}

	@Override
	@Transactional
	public void clear(Integer userId) {
		for (CartItem ci : cartItemRepository.findByUserId(userId)) {
			cartItemToppingRepository.deleteByCartItemId(ci.getId());
			cartItemRepository.delete(ci);
		}
	}

	// ================= TÙY CHỌN MÓN (cho modal) =================
	@Override
	public ProductOptionsRes getOptions(Integer productId) {
		Product p = productRepository.findById(productId).orElseThrow(() -> new BusinessException("Món không tồn tại"));
		if (!Boolean.TRUE.equals(p.getIsAvailable()))
			throw new BusinessException("Món " + p.getName() + " đã ngừng bán");

		Map<String, BigDecimal> sizes = new LinkedHashMap<>(); // giữ thứ tự S, M, L
		productSizeRepository.findByProductId(productId).stream()
				.sorted(Comparator.comparingInt(ps -> "SML".indexOf(ps.getSize())))
				.forEach(ps -> sizes.put(ps.getSize(), ps.getExtraPrice()));
		if (sizes.isEmpty())
			throw new BusinessException("Món này chưa được cấu hình size");

		List<CartItemRes.ToppingRes> toppings = toppingRepository.findAll().stream()
				.filter(t -> Boolean.TRUE.equals(t.getIsAvailable())).sorted(Comparator.comparing(Topping::getId))
				.map(t -> new CartItemRes.ToppingRes(t.getId(), t.getName(), t.getPrice())).toList();
		return new ProductOptionsRes(sizes, toppings);
	}

	// ================= HÀM PHỤ =================

	/** Dòng giỏ hàng phải thuộc đúng user, nếu không coi như không tồn tại */
	private CartItem ownedItem(Integer userId, Integer cartItemId) {
		return cartItemRepository.findById(cartItemId).filter(ci -> ci.getUser().getId().equals(userId)).orElseThrow(
				() -> new ResourceNotFoundException("Không tìm thấy món #" + cartItemId + " trong giỏ hàng"));
	}

	private Set<Integer> toppingIdsOf(CartItem ci) {
		return cartItemToppingRepository.findByCartItemId(ci.getId()).stream().map(cit -> cit.getTopping().getId())
				.collect(Collectors.toSet());
	}

	private List<Topping> loadToppings(List<Integer> ids) {
		if (ids == null || ids.isEmpty())
			return List.of();
		Set<Integer> distinct = new HashSet<>(ids);
		List<Topping> toppings = toppingRepository.findAllById(distinct);
		if (toppings.size() != distinct.size())
			throw new BusinessException("Topping không tồn tại");
		for (Topping t : toppings) {
			if (!Boolean.TRUE.equals(t.getIsAvailable()))
				throw new BusinessException("Topping " + t.getName() + " đã hết");
		}
		return toppings;
	}

	private int checkLevel(Integer level, String label) {
		int v = level == null ? 100 : level;
		if (!LEVELS.contains(v))
			throw new BusinessException("Mức " + label + " phải là 0, 30, 50, 70 hoặc 100%");
		return v;
	}

	private void checkQty(int qty) {
		if (qty < 1 || qty > MAX_QTY)
			throw new BusinessException("Số lượng mỗi món phải từ 1 đến " + MAX_QTY);
	}

	/** Entity -> dữ liệu gửi trình duyệt. Giá tính lại từ DB ở đây */
	private CartItemRes toRes(CartItem ci) {
		Product p = ci.getProduct();
		List<Topping> toppings = cartItemToppingRepository.findByCartItemId(ci.getId()).stream()
				.map(CartItemTopping::getTopping).sorted(Comparator.comparing(Topping::getId)).toList();

		BigDecimal sizeExtra = productSizeRepository.findByProductIdAndSize(p.getId(), ci.getSize())
				.map(ProductSize::getExtraPrice).orElse(BigDecimal.ZERO);
		BigDecimal toppingSum = toppings.stream().map(Topping::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal unitPrice = p.getBasePrice().add(sizeExtra).add(toppingSum);

		return new CartItemRes(ci.getId(), p.getId(), p.getName(), p.getImageCloudinaryId(), ci.getSize(),
				ci.getSugarLevel(), ci.getIceLevel(),
				toppings.stream().map(t -> new CartItemRes.ToppingRes(t.getId(), t.getName(), t.getPrice())).toList(),
				ci.getQuantity(), unitPrice, unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity())), ci.getNote());
	}
}