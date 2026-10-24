package vn.utetra.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Branch;
import vn.utetra.entity.BranchProduct;
import vn.utetra.entity.Product;
import vn.utetra.entity.Topping;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.ToppingRepository;

@Service
@Transactional
public class BranchStockService {

	public record StockRow(Integer id, String name, String category, String status) {
		public String label() {
			return switch (status) {
				case "OUT" -> "Hết ở chi nhánh";
				case "OFF" -> "Ngừng bán (toàn chuỗi)";
				default -> "Còn bán";
			};
		}
	}

	public record BranchStock(Branch branch, List<StockRow> products, List<StockRow> toppings, long outCount) {
	}

	public record Toggled(String name, boolean available) {
	}

	private final BranchRepository branchRepository;
	private final ProductRepository productRepository;
	private final BranchProductRepository branchProductRepository;
	private final ToppingRepository toppingRepository;
	private final ToppingService toppingService;
	private final NotificationService notificationService;

	public BranchStockService(BranchRepository branchRepository, ProductRepository productRepository,
			BranchProductRepository branchProductRepository, ToppingRepository toppingRepository,
			ToppingService toppingService, NotificationService notificationService) {
		this.branchRepository = branchRepository;
		this.productRepository = productRepository;
		this.branchProductRepository = branchProductRepository;
		this.toppingRepository = toppingRepository;
		this.toppingService = toppingService;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	public Branch branch(Integer branchId) {
		return branchRepository.findById(branchId)
				.orElseThrow(() -> new BusinessException("Không tìm thấy chi nhánh #" + branchId));
	}

	@Transactional(readOnly = true)
	public BranchStock stock(Integer branchId) {
		Branch branch = branch(branchId);
		Set<Integer> hetMon = Set.copyOf(branchProductRepository.findUnavailableProductIds(branchId));
		Set<Integer> hetTopping = toppingService.unavailableToppingIds(branchId);

		List<StockRow> products = drinks().stream()
				.map(p -> new StockRow(p.getId(), p.getName(), p.getCategory() == null ? "" : p.getCategory().getName(),
						!Boolean.TRUE.equals(p.getIsAvailable()) ? "OFF" : hetMon.contains(p.getId()) ? "OUT" : "OK"))
				.toList();
		List<StockRow> toppings = toppings().stream()
				.map(t -> new StockRow(t.getId(), t.getName(), "Topping",
						!Boolean.TRUE.equals(t.getIsAvailable()) ? "OFF" : hetTopping.contains(t.getId()) ? "OUT" : "OK"))
				.toList();
		long out = products.stream().filter(r -> !"OK".equals(r.status())).count()
				+ toppings.stream().filter(r -> !"OK".equals(r.status())).count();
		return new BranchStock(branch, products, toppings, out);
	}

	@Transactional(readOnly = true)
	public List<Product> drinks() {
		return productRepository.findAll().stream().filter(p -> !p.isToppingItem())
				.sorted(Comparator.comparing((Product p) -> p.getCategory() == null ? 0 : p.getCategory().getId())
						.thenComparing(Product::getName))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<Topping> toppings() {
		return toppingRepository.findAll().stream().sorted(Comparator.comparing(Topping::getId)).toList();
	}

	@Transactional(readOnly = true)
	public List<vn.utetra.entity.BranchProduct> branchProducts(Integer branchId) {
		return branchProductRepository.findByBranchId(branchId);
	}

	@Transactional(readOnly = true)
	public java.util.Map<Integer, List<String>> unavailableBranchNamesByProduct() {
		java.util.Map<Integer, List<String>> outAt = new java.util.HashMap<>();
		for (Object[] r : branchProductRepository.findAllUnavailable()) {
			outAt.computeIfAbsent((Integer) r[0], k -> new java.util.ArrayList<>()).add((String) r[1]);
		}
		return outAt;
	}

	@Transactional(readOnly = true)
	public List<Integer> unavailableProductIds(Integer branchId) {
		return branchProductRepository.findUnavailableProductIds(branchId);
	}

	@Transactional(readOnly = true)
	public Set<Integer> unavailableToppingIds(Integer branchId) {
		return toppingService.unavailableToppingIds(branchId);
	}

	public Toggled toggleProduct(Integer branchId, Integer productId, boolean byAdmin) {
		Branch branch = branch(branchId);
		checkEditable(branch, byAdmin);
		Product p = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException("Không tìm thấy món #" + productId));
		if (p.isToppingItem()) {
			throw new BusinessException(p.getName() + " là topping, hãy bật / tắt ở mục Topping");
		}
		if (!Boolean.TRUE.equals(p.getIsAvailable())) {
			throw new BusinessException(p.getName()
					+ " đang ngừng bán toàn chuỗi" + (byAdmin ? ", mở bán lại ở trang Sản phẩm trước" : ", chi nhánh không mở bán được"));
		}
		BranchProduct bp = branchProductRepository.findByBranch_IdAndProduct_Id(branchId, productId).orElseGet(() -> {
			BranchProduct n = new BranchProduct();
			n.setBranch(branch);
			n.setProduct(p);
			n.setIsAvailable(true);
			return n;
		});
		bp.setIsAvailable(!Boolean.TRUE.equals(bp.getIsAvailable()));
		branchProductRepository.save(bp);
		boolean dangBan = bp.getIsAvailable();
		notifyOtherSide(branch, "món", p.getName(), dangBan, byAdmin);
		return new Toggled(p.getName(), dangBan);
	}

	public Toggled toggleTopping(Integer branchId, Integer toppingId, boolean byAdmin) {
		Branch branch = branch(branchId);
		checkEditable(branch, byAdmin);
		boolean dangBan = toppingService.toggleAtBranch(branch, toppingId);
		String name = toppingService.get(toppingId).getName();
		notifyOtherSide(branch, "topping", name, dangBan, byAdmin);
		return new Toggled(name, dangBan);
	}

	private void checkEditable(Branch branch, boolean byAdmin) {
		if (!byAdmin && branch.getStatus() != BranchStatus.APPROVED) {
			throw new BusinessException("Chi nhánh đang tạm ngưng, không thể điều chỉnh món.");
		}
	}

	private void notifyOtherSide(Branch branch, String what, String name, boolean dangBan, boolean byAdmin) {
		if (byAdmin) {
			if (branch.getOwner() == null) return;
			notificationService.create(branch.getOwner(), null, "STOCK",
					"Admin " + (dangBan ? "mở bán lại " : "báo hết ") + what + ": " + name,
					(dangBan ? "Đã mở bán lại " : "Tạm hết ") + what + " " + name + " tại " + branch.getName(),
					"/manager/branches/" + branch.getId() + "/products");
		} else {
			notificationService.notifyAdmins("STOCK",
					branch.getName() + (dangBan ? " mở bán lại: " : " báo hết: ") + name,
					(dangBan ? "Đã có lại " : "Tạm hết ") + what + " " + name + " tại " + branch.getName(),
					"/admin/branches/" + branch.getId() + "/stock", null);
		}
	}
}
