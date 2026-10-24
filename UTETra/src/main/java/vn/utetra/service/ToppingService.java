package vn.utetra.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import vn.utetra.entity.Branch;
import vn.utetra.entity.BranchProduct;
import vn.utetra.entity.BranchTopping;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.entity.ProductSize;
import vn.utetra.entity.Topping;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchToppingRepository;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.ProductSizeRepository;
import vn.utetra.repository.ToppingRepository;

@Service
@Transactional
public class ToppingService {

	public static final String CATEGORY_NAME = "Topping";
	public static final BigDecimal MAX_PRICE = BigDecimal.valueOf(100_000);

	public record ToppingRow(Topping topping, Product product, long usedAsAddOn, long soldAlone,
			List<String> outAt) {
		public boolean used() {
			return usedAsAddOn + soldAlone > 0;
		}
	}

	private final ToppingRepository toppingRepository;
	private final ProductRepository productRepository;
	private final ProductSizeRepository productSizeRepository;
	private final CategoryRepository categoryRepository;
	private final BranchToppingRepository branchToppingRepository;
	private final BranchProductRepository branchProductRepository;
	private final CloudinaryService cloudinaryService;
	private final EntityManager em;

	public ToppingService(ToppingRepository toppingRepository, ProductRepository productRepository,
			ProductSizeRepository productSizeRepository, CategoryRepository categoryRepository,
			BranchToppingRepository branchToppingRepository, BranchProductRepository branchProductRepository,
			CloudinaryService cloudinaryService, EntityManager em) {
		this.toppingRepository = toppingRepository;
		this.productRepository = productRepository;
		this.productSizeRepository = productSizeRepository;
		this.categoryRepository = categoryRepository;
		this.branchToppingRepository = branchToppingRepository;
		this.branchProductRepository = branchProductRepository;
		this.cloudinaryService = cloudinaryService;
		this.em = em;
	}

	@Transactional(readOnly = true)
	public List<ToppingRow> list() {
		java.util.Map<Integer, List<String>> outAt = new java.util.HashMap<>();
		for (Object[] r : branchToppingRepository.findAllUnavailable()) {
			outAt.computeIfAbsent((Integer) r[0], k -> new java.util.ArrayList<>()).add((String) r[1]);
		}
		return toppingRepository.findAll().stream().sorted(Comparator.comparing(Topping::getId))
				.map(t -> {
					Product p = productRepository.findByTopping_Id(t.getId()).orElse(null);
					return new ToppingRow(t, p, usedAsAddOn(t.getId()), p == null ? 0 : soldAlone(p.getId()),
							outAt.getOrDefault(t.getId(), List.of()));
				}).toList();
	}

	@Transactional(readOnly = true)
	public Topping get(Integer id) {
		return toppingRepository.findById(id)
				.orElseThrow(() -> new BusinessException("Không tìm thấy topping #" + id));
	}

	public Topping create(String name, BigDecimal price, String description, MultipartFile image) {
		Topping t = new Topping();
		t.setName(cleanName(name, null));
		t.setPrice(checkPrice(price));
		t.setIsAvailable(true);
		t = toppingRepository.save(t);

		Product p = new Product();
		p.setCategory(toppingCategory());
		p.setTopping(t);
		p.setSoldCount(0);
		p.setCreatedAt(java.time.LocalDateTime.now());
		p.setImageCloudinaryId(upload(image, null));
		copy(t, p, description);
		p = productRepository.save(p);

		ProductSize size = new ProductSize();
		size.setProduct(p);
		size.setSize("M");
		size.setExtraPrice(BigDecimal.ZERO);
		productSizeRepository.save(size);
		return t;
	}

	public Topping update(Integer id, String name, BigDecimal price, String description, MultipartFile image) {
		Topping t = get(id);
		t.setName(cleanName(name, id));
		t.setPrice(checkPrice(price));
		toppingRepository.save(t);

		Product p = productOf(t);
		String old = p.getImageCloudinaryId();
		String url = upload(image, old);
		p.setImageCloudinaryId(url);
		copy(t, p, description);
		productRepository.save(p);
		if (old != null && !old.equals(url)) deleteImageQuietly(old);
		return t;
	}

	public Topping toggle(Integer id) {
		Topping t = get(id);
		t.setIsAvailable(!Boolean.TRUE.equals(t.getIsAvailable()));
		toppingRepository.save(t);
		Product p = productOf(t);
		p.setIsAvailable(t.getIsAvailable());
		productRepository.save(p);
		return t;
	}

	public void delete(Integer id) {
		Topping t = get(id);
		Product p = productRepository.findByTopping_Id(id).orElse(null);
		if (usedAsAddOn(id) > 0 || (p != null && soldAlone(p.getId()) > 0)) {
			throw new BusinessException("Topping \"" + t.getName()
					+ "\" đã có trong đơn hàng nên không xoá được (để hoá đơn cũ còn nguyên). Bạn hãy bấm Ngưng bán.");
		}
		em.createQuery("DELETE FROM CartItemTopping c WHERE c.topping.id = :id").setParameter("id", id).executeUpdate();
		em.createQuery("DELETE FROM BranchTopping b WHERE b.topping.id = :id").setParameter("id", id).executeUpdate();
		String image = null;
		if (p != null) {
			Integer pid = p.getId();
			image = p.getImageCloudinaryId();
			em.createQuery("DELETE FROM CartItemTopping c WHERE c.cartItem.id IN "
					+ "(SELECT ci.id FROM CartItem ci WHERE ci.product.id = :pid)").setParameter("pid", pid).executeUpdate();
			for (String entity : List.of("CartItem", "Favorite", "ViewedProduct", "BranchProduct", "ProductSize")) {
				em.createQuery("DELETE FROM " + entity + " x WHERE x.product.id = :pid").setParameter("pid", pid)
						.executeUpdate();
			}
			em.createQuery("DELETE FROM Product p WHERE p.id = :pid").setParameter("pid", pid).executeUpdate();
		}
		em.createQuery("DELETE FROM Topping t WHERE t.id = :id").setParameter("id", id).executeUpdate();
		if (image != null) deleteImageQuietly(image);
	}

	@Transactional(readOnly = true)
	public boolean isToppingCategory(Integer categoryId) {
		if (categoryId == null) return false;
		return productRepository.findByToppingIsNotNull().stream()
				.anyMatch(p -> p.getCategory() != null && categoryId.equals(p.getCategory().getId()))
				|| categoryRepository.findById(categoryId)
						.map(c -> CATEGORY_NAME.equalsIgnoreCase(c.getName().trim())).orElse(false);
	}

	@Transactional(readOnly = true)
	public Set<Integer> unavailableToppingIds(Integer branchId) {
		return branchId == null ? Set.of() : new HashSet<>(branchToppingRepository.findUnavailableToppingIds(branchId));
	}

	public boolean toggleAtBranch(Branch branch, Integer toppingId) {
		Topping t = get(toppingId);
		if (!Boolean.TRUE.equals(t.getIsAvailable())) {
			throw new BusinessException("Topping \"" + t.getName() + "\" đang ngưng bán toàn chuỗi, không mở bán ở chi nhánh được");
		}
		BranchTopping bt = branchToppingRepository.findByBranch_IdAndTopping_Id(branch.getId(), toppingId)
				.orElseGet(() -> {
					BranchTopping n = new BranchTopping();
					n.setBranch(branch);
					n.setTopping(t);
					n.setIsAvailable(true);
					return n;
				});
		bt.setIsAvailable(!Boolean.TRUE.equals(bt.getIsAvailable()));
		branchToppingRepository.save(bt);

		productRepository.findByTopping_Id(toppingId).ifPresent(p -> {
			BranchProduct bp = branchProductRepository.findByBranch_IdAndProduct_Id(branch.getId(), p.getId())
					.orElseGet(() -> {
						BranchProduct n = new BranchProduct();
						n.setBranch(branch);
						n.setProduct(p);
						return n;
					});
			bp.setIsAvailable(bt.getIsAvailable());
			branchProductRepository.save(bp);
		});
		return bt.getIsAvailable();
	}

	private Product productOf(Topping t) {
		return productRepository.findByTopping_Id(t.getId()).orElseGet(() -> {
			Product p = new Product();
			p.setCategory(toppingCategory());
			p.setTopping(t);
			p.setSoldCount(0);
			p.setCreatedAt(java.time.LocalDateTime.now());
			copy(t, p, null);
			p = productRepository.save(p);
			ProductSize size = new ProductSize();
			size.setProduct(p);
			size.setSize("M");
			size.setExtraPrice(BigDecimal.ZERO);
			productSizeRepository.save(size);
			return p;
		});
	}

	private void copy(Topping t, Product p, String description) {
		p.setName(t.getName());
		p.setBasePrice(t.getPrice());
		p.setIsAvailable(t.getIsAvailable());
		String d = description == null || description.isBlank() ? null : description.trim();
		if (d != null && d.length() > 500) throw new BusinessException("Mô tả tối đa 500 ký tự");
		if (description != null) p.setDescription(d);
		if (p.getDescription() == null) p.setDescription("Topping " + t.getName() + " bán riêng");
	}

	private Category toppingCategory() {
		return productRepository.findByToppingIsNotNull().stream().map(Product::getCategory)
				.filter(c -> c != null).findFirst()
				.or(() -> categoryRepository.findByName(CATEGORY_NAME))
				.orElseGet(() -> {
					Category c = new Category();
					c.setName(CATEGORY_NAME);
					return categoryRepository.save(c);
				});
	}

	private String cleanName(String name, Integer selfId) {
		String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
		if (n.isEmpty()) throw new BusinessException("Vui lòng nhập tên topping");
		if (n.length() > 100) throw new BusinessException("Tên topping tối đa 100 ký tự");
		boolean trung = toppingRepository.findAll().stream()
				.anyMatch(t -> t.getName().equalsIgnoreCase(n) && !t.getId().equals(selfId));
		if (trung) throw new BusinessException("Đã có topping tên \"" + n + "\"");
		return n;
	}

	private static BigDecimal checkPrice(BigDecimal price) {
		if (price == null) throw new BusinessException("Vui lòng nhập giá topping");
		if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
			throw new BusinessException("Giá topping từ 0đ đến 100.000đ");
		}
		if (price.stripTrailingZeros().scale() > 0) throw new BusinessException("Giá topping phải là số nguyên (đồng)");
		return price;
	}

	private String upload(MultipartFile image, String old) {
		if (image == null || image.isEmpty()) return old;
		String ct = image.getContentType() == null ? "" : image.getContentType();
		if (!ct.startsWith("image/")) throw new BusinessException("Ảnh topping phải là file ảnh");
		if (image.getSize() > 5L * 1024 * 1024) throw new BusinessException("Ảnh topping tối đa 5MB");
		String url = cloudinaryService.uploadImage(image, "toppings");
		if (url == null) throw new BusinessException("Không tải được ảnh lên, vui lòng thử lại");
		return url;
	}

	private void deleteImageQuietly(String url) {
		try {
			cloudinaryService.deleteImage(url);
		} catch (RuntimeException ignored) {
		}
	}

	private long usedAsAddOn(Integer toppingId) {
		return em.createQuery("SELECT COUNT(x) FROM OrderDetailTopping x WHERE x.topping.id = :id", Long.class)
				.setParameter("id", toppingId).getSingleResult();
	}

	private long soldAlone(Integer productId) {
		return em.createQuery("SELECT COUNT(d) FROM OrderDetail d WHERE d.product.id = :pid", Long.class)
				.setParameter("pid", productId).getSingleResult();
	}
}
