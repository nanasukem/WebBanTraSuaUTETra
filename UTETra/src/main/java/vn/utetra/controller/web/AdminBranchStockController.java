package vn.utetra.controller.web;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * Admin xem MÓN CÒN / HẾT của 1 chi nhánh (/admin/branches/{id}/stock) - chỉ xem.
 * Hết ở chi nhánh do Manager bật / tắt; Ngừng bán toàn chuỗi do Admin.
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

	public AdminBranchStockController(BranchRepository branchRepository, ProductRepository productRepository,
			BranchProductRepository branchProductRepository, ToppingRepository toppingRepository,
			ToppingService toppingService) {
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
}
