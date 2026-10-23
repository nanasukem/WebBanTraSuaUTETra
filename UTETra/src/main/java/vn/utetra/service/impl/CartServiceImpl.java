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
	private final vn.utetra.repository.BranchRepository branchRepository;
	private final vn.utetra.repository.BranchProductRepository branchProductRepository;
	private final vn.utetra.repository.BranchToppingRepository branchToppingRepository;

	public CartServiceImpl(CartItemRepository cartItemRepository, CartItemToppingRepository cartItemToppingRepository,
			ProductRepository productRepository, ProductSizeRepository productSizeRepository,
			ToppingRepository toppingRepository, UserRepository userRepository, OrderRepository orderRepository,
			vn.utetra.repository.BranchRepository branchRepository,
			vn.utetra.repository.BranchProductRepository branchProductRepository,
			vn.utetra.repository.BranchToppingRepository branchToppingRepository) {
		this.orderRepository = orderRepository;
		this.branchRepository = branchRepository;
		this.branchProductRepository = branchProductRepository;
		this.branchToppingRepository = branchToppingRepository;
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

		if (product.isToppingItem()) {
			// Topping mua riêng: 1 cỡ, không chọn đường / đá, không thêm topping vào topping
			req.setSize("M");
			req.setSugarLevel(100);
			req.setIceLevel(100);
			req.setToppingIds(List.of());
		}
		String size = req.getSize() == null ? "S" : req.getSize().trim().toUpperCase(); // mặc định S
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
	public CartRes updateOptions(Integer userId, Integer cartItemId, CartAddReq req) {
		CartItem ci = ownedItem(userId, cartItemId);
		req.setProductId(ci.getProduct().getId()); // chỉ đổi tuỳ chọn, không đổi món
		// Xoá dòng cũ rồi thêm lại theo lựa chọn mới (trùng cấu hình với dòng khác thì cộng dồn).
		// Lỗi (topping ngừng bán, size sai...) -> cả giao dịch huỷ, giỏ giữ nguyên.
		cartItemToppingRepository.deleteByCartItemId(ci.getId());
		cartItemRepository.delete(ci);
		cartItemRepository.flush();
		return addItem(userId, req);
	}
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
		return getOptions(productId, null);
	}

	@Override
	public ProductOptionsRes getOptions(Integer productId, Integer branchId) {
		Product p = productRepository.findById(productId).orElseThrow(() -> new BusinessException("Món không tồn tại"));
		if (!Boolean.TRUE.equals(p.getIsAvailable()))
			throw new BusinessException("Món " + p.getName() + " đã ngừng bán");

		Map<String, BigDecimal> sizes = new LinkedHashMap<>(); // giữ thứ tự S, M, L
		productSizeRepository.findByProductId(productId).stream()
				.sorted(Comparator.comparingInt(ps -> "SML".indexOf(ps.getSize())))
				.forEach(ps -> sizes.put(ps.getSize(), ps.getExtraPrice()));
		if (sizes.isEmpty())
			throw new BusinessException("Món này chưa được cấu hình size");

		List<CartItemRes.ToppingRes> toppings = p.isToppingItem() ? List.of()
				: toppingRepository.findAll().stream()
						.filter(t -> Boolean.TRUE.equals(t.getIsAvailable())).sorted(Comparator.comparing(Topping::getId))
						.map(t -> new CartItemRes.ToppingRes(t.getId(), t.getName(), t.getPrice())).toList();
		List<CartItemRes.ToppingRes> stopped = p.isToppingItem() ? List.of()
				: toppingRepository.findAll().stream()
						.filter(t -> !Boolean.TRUE.equals(t.getIsAvailable())).sorted(Comparator.comparing(Topping::getId))
						.map(t -> new CartItemRes.ToppingRes(t.getId(), t.getName(), t.getPrice())).toList();
		// Topping hết ở chi nhánh đang chọn: bỏ khỏi danh sách chọn, hiện mờ "Hết ở chi nhánh"
		vn.utetra.entity.Branch branch = branchId == null ? null : branchRepository.findById(branchId).orElse(null);
		List<CartItemRes.ToppingRes> out = List.of();
		if (branch != null) {
			java.util.Set<Integer> het = new java.util.HashSet<>(branchToppingRepository.findUnavailableToppingIds(branchId));
			out = toppings.stream().filter(t -> het.contains(t.id())).toList();
			toppings = toppings.stream().filter(t -> !het.contains(t.id())).toList();
		}
		return new ProductOptionsRes(sizes, toppings, p.isToppingItem(), stopped, out,
				branch == null ? null : branch.getName());
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

		String stopNote = stopNote(p, toppings);
		return new CartItemRes(ci.getId(), p.getId(), p.getName(), p.getImageCloudinaryId(), ci.getSize(),
				ci.getSugarLevel(), ci.getIceLevel(),
				toppings.stream().map(t -> new CartItemRes.ToppingRes(t.getId(), t.getName(), t.getPrice())).toList(),
				ci.getQuantity(), unitPrice, unitPrice.multiply(BigDecimal.valueOf(ci.getQuantity())), ci.getNote(),
				p.isToppingItem(), stopNote, outNote(p, toppings),
				stopNote != null || outEverywhere(p, toppings));
	}

	/** Chi nhánh đang hoạt động */
	private List<vn.utetra.entity.Branch> openBranches() {
		return branchRepository.findByStatus(vn.utetra.entity.enums.BranchStatus.APPROVED);
	}

	private boolean productOut(Integer branchId, Product p) {
		boolean mon = branchProductRepository.findByBranch_IdAndProduct_Id(branchId, p.getId())
				.map(bp -> !Boolean.TRUE.equals(bp.getIsAvailable())).orElse(false);
		return mon || (p.getTopping() != null && toppingOut(branchId, p.getTopping()));
	}

	private boolean toppingOut(Integer branchId, Topping t) {
		return branchToppingRepository.findByBranch_IdAndTopping_Id(branchId, t.getId())
				.map(bt -> !Boolean.TRUE.equals(bt.getIsAvailable())).orElse(false);
	}

	/** "Hết ở UTETra Võ Văn Ngân" · "Topping Trân châu đen hết ở UTETra Q1" (khách đổi chi nhánh lúc thanh toán được) */
	private String outNote(Product p, List<Topping> toppings) {
		List<vn.utetra.entity.Branch> open = openBranches();
		if (open.isEmpty()) return null;
		List<String> parts = new ArrayList<>();
		List<String> monHet = open.stream().filter(b -> productOut(b.getId(), p)).map(vn.utetra.entity.Branch::getName).toList();
		if (!monHet.isEmpty()) {
			parts.add(monHet.size() == open.size() ? "Hết ở tất cả chi nhánh" : "Hết ở " + String.join(", ", monHet));
		}
		for (Topping t : toppings) {
			List<String> het = open.stream().filter(b -> toppingOut(b.getId(), t)).map(vn.utetra.entity.Branch::getName).toList();
			if (!het.isEmpty()) {
				parts.add("Topping " + t.getName() + (het.size() == open.size() ? " hết ở tất cả chi nhánh"
						: " hết ở " + String.join(", ", het)));
			}
		}
		return parts.isEmpty() ? null : String.join(" · ", parts);
	}

	/** Không chi nhánh nào làm được ly này -> phải xoá khỏi giỏ */
	private boolean outEverywhere(Product p, List<Topping> toppings) {
		List<vn.utetra.entity.Branch> open = openBranches();
		return !open.isEmpty() && open.stream().allMatch(b -> productOut(b.getId(), p)
				|| toppings.stream().anyMatch(t -> toppingOut(b.getId(), t)));
	}

	/** Món / topping trong giỏ mà Admin đã ngừng bán toàn chuỗi -> ghi rõ ngay ở dòng đó */
	private static String stopNote(Product p, List<Topping> toppings) {
		if (!Boolean.TRUE.equals(p.getIsAvailable())
				|| (p.getTopping() != null && !Boolean.TRUE.equals(p.getTopping().getIsAvailable()))) {
			return "Đã ngừng bán";
		}
		List<String> stopped = toppings.stream().filter(t -> !Boolean.TRUE.equals(t.getIsAvailable()))
				.map(Topping::getName).toList();
		return stopped.isEmpty() ? null : "Topping " + String.join(", ", stopped) + " đã ngừng bán";
	}
}