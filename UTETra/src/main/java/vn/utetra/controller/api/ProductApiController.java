package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utetra.entity.Product;
import vn.utetra.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

	private final ProductService productService;

	public ProductApiController(ProductService productService) {
		this.productService = productService;
	}

	// 1. Lấy danh sách sản phẩm (có lọc keyword, categoryId, sortBy)
	// GET /api/products?keyword=Tra&categoryId=1&sortBy=price_asc
	@GetMapping
	public ResponseEntity<List<Product>> getProducts(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer categoryId, @RequestParam(required = false) String sortBy) {
		List<Product> products = productService.filterProducts(keyword, categoryId, sortBy);
		return ResponseEntity.ok(products);
	}
}