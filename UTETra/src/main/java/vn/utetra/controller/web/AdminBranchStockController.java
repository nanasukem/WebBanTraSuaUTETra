package vn.utetra.controller.web;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestMapping;

import vn.utetra.entity.Branch;
import vn.utetra.entity.Product;
import vn.utetra.entity.Topping;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.ToppingRepository;
import vn.utetra.service.ToppingService;

/**
 * Admin xem và BẬT / TẮT MÓN, TOPPING của 1 chi nhánh (/admin/branches/{id}/stock).
 * Hết ở chi nhánh: Manager hoặc Admin bật / tắt (Admin tắt / bật thì Manager được báo); Ngừng bán toàn chuỗi do Admin.
 */
@Controller
@RequestMapping("/admin/branches")
@Transactional(readOnly = true)
public class AdminBranchStockController {

	/** 1 dòng: tên, danh mục, trạng thái (OK = còn bán, OUT = chi nhánh báo hết, OFF = Admin ngừng bán) */
	public record StockRow(Integer id, String name, String category, String status) {
		public String label() {
			return switch (status) {
			case "OUT" -> "Hết ở chi nhánh";
			case "OFF" -> "Ngừng bán (toàn chuỗi)";
			default -> "Còn bán";
			};
		}
	}

	private final BranchRepository branchRepository;
	private final ProductRepository productRepository;
	private final BranchProductRepository branchProductRepository;
	private final ToppingRepository toppingRepository;
	private final ToppingService toppingService;
	private final vn.utetra.service.NotificationService notificationService;

	public AdminBranchStockController(BranchRepository branchRepository, ProductRepository productRepository,
			BranchProductRepository branchProductRepository, ToppingRepository toppingRepository,
			ToppingService toppingService, vn.utetra.service.NotificationService notificationService) {
		this.notificationService = notificationService;
		this.branchRepository = branchRepository;
		this.productRepository = productRepository;
		this.branchProductRepository = branchProductRepository;
		this.toppingRepository = toppingRepository;
		this.toppingService = toppingService;
	}

	@GetMapping("/{id}/stock")
	public String stock(@PathVariable Integer id, Model model) {
		Branch branch = branchRepository.findById(id).orElse(null);
		if (branch == null) return "redirect:/admin/branches";

		Set<Integer> hetMon = Set.copyOf(branchProductRepository.findUnavailableProductIds(id));
		Set<Integer> hetTopping = toppingService.unavailableToppingIds(id);

		List<StockRow> products = productRepository.findAll().stream().filter(p -> !p.isToppingItem())
				.sorted(Comparator.comparing((Product p) -> p.getCategory() == null ? 0 : p.getCategory().getId())
						.thenComparing(Product::getName))
				.map(p -> new StockRow(p.getId(), p.getName(), p.getCategory() == null ? "" : p.getCategory().getName(),
						!Boolean.TRUE.equals(p.getIsAvailable()) ? "OFF" : hetMon.contains(p.getId()) ? "OUT" : "OK"))
				.toList();
		List<StockRow> toppings = toppingRepository.findAll().stream().sorted(Comparator.comparing(Topping::getId))
				.map(t -> new StockRow(t.getId(), t.getName(), "Topping",
						!Boolean.TRUE.equals(t.getIsAvailable()) ? "OFF" : hetTopping.contains(t.getId()) ? "OUT" : "OK"))
				.toList();

		model.addAttribute("branch", branch);
		model.addAttribute("products", products);
		model.addAttribute("toppings", toppings);
		model.addAttribute("outCount", products.stream().filter(r -> !r.status().equals("OK")).count()
				+ toppings.stream().filter(r -> !r.status().equals("OK")).count());
		return "admin/branches/stock";
	}

	/** Admin báo hết / mở bán lại 1 món ở chi nhánh (giống nút của Manager) */
	@PostMapping("/{id}/stock/products/{productId}/toggle")
	@Transactional
	public String toggleProduct(@PathVariable Integer id, @PathVariable Integer productId, RedirectAttributes ra) {
		Branch branch = branchRepository.findById(id).orElse(null);
		Product p = productRepository.findById(productId).orElse(null);
		if (branch == null || p == null || p.isToppingItem()) {
			ra.addFlashAttribute("errorMessage", "Không tìm thấy món hoặc chi nhánh");
			return "redirect:/admin/branches/" + id + "/stock";
		}
		if (!Boolean.TRUE.equals(p.getIsAvailable())) {
			ra.addFlashAttribute("errorMessage", p.getName() + " đang ngừng bán toàn chuỗi, mở bán lại ở trang Sản phẩm trước");
			return "redirect:/admin/branches/" + id + "/stock";
		}
		vn.utetra.entity.BranchProduct bp = branchProductRepository.findByBranch_IdAndProduct_Id(id, productId)
				.orElseGet(() -> {
					vn.utetra.entity.BranchProduct n = new vn.utetra.entity.BranchProduct();
					n.setBranch(branch);
					n.setProduct(p);
					n.setIsAvailable(true); // chưa có dòng = đang bán
					return n;
				});
		bp.setIsAvailable(!Boolean.TRUE.equals(bp.getIsAvailable()));
		branchProductRepository.save(bp);
		boolean dangBan = bp.getIsAvailable();
		notifyManager(branch, "món", p.getName(), dangBan);
		ra.addFlashAttribute("successMessage", (dangBan ? "Đã mở bán lại " : "Đã báo hết ") + p.getName() + " tại " + branch.getName());
		return "redirect:/admin/branches/" + id + "/stock";
	}

	/** Admin báo hết / mở bán lại 1 topping ở chi nhánh (cả thêm vào ly lẫn mua riêng) */
	@PostMapping("/{id}/stock/toppings/{toppingId}/toggle")
	@Transactional
	public String toggleTopping(@PathVariable Integer id, @PathVariable Integer toppingId, RedirectAttributes ra) {
		Branch branch = branchRepository.findById(id).orElse(null);
		if (branch == null) return "redirect:/admin/branches";
		try {
			boolean dangBan = toppingService.toggleAtBranch(branch, toppingId);
			String name = toppingService.get(toppingId).getName();
			notifyManager(branch, "topping", name, dangBan);
			ra.addFlashAttribute("successMessage", (dangBan ? "Đã mở bán lại topping " : "Đã báo hết topping ") + name
					+ " tại " + branch.getName());
		} catch (vn.utetra.exception.BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/branches/" + id + "/stock";
	}

	/** Báo Manager của chi nhánh: Admin vừa đổi tình trạng món / topping ở chi nhánh mình */
	private void notifyManager(Branch branch, String what, String name, boolean dangBan) {
		if (branch.getOwner() == null) return;
		notificationService.create(branch.getOwner(), null, "STOCK",
				"Admin " + (dangBan ? "mở bán lại " : "báo hết ") + what + ": " + name,
				(dangBan ? "Đã mở bán lại " : "Tạm hết ") + what + " " + name + " tại " + branch.getName(),
				"/manager/branches/" + branch.getId() + "/products");
	}
}
