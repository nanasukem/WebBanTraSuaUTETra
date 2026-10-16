package vn.utetra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.entity.Branch;
import vn.utetra.entity.BranchProduct;
import vn.utetra.entity.Product;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.ProductRepository;

import java.util.List;

@Service
public class VendorBranchService {

	private final BranchProductRepository branchProductRepository;
	private final BranchRepository branchRepository;
	private final ProductRepository productRepository;

	public VendorBranchService(BranchProductRepository branchProductRepository, BranchRepository branchRepository,
			ProductRepository productRepository) {
		this.branchProductRepository = branchProductRepository;
		this.branchRepository = branchRepository;
		this.productRepository = productRepository;
	}

	// 1. Toggle bật/tắt món tại 1 chi nhánh
	@Transactional
	public boolean toggleProductAvailability(Integer branchId, Integer productId, boolean isAvailable) {
		var existing = branchProductRepository.findByBranch_IdAndProduct_Id(branchId, productId);

		if (existing.isPresent()) {
			BranchProduct bp = existing.get();
			bp.setIsAvailable(isAvailable);
			branchProductRepository.save(bp);
		} else {
			Branch branch = branchRepository.findById(branchId)
					.orElseThrow(() -> new RuntimeException("Không tìm thấy chi nhánh!"));
			Product product = productRepository.findById(productId)
					.orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm!"));

			BranchProduct bp = new BranchProduct();
			bp.setBranch(branch);
			bp.setProduct(product);
			bp.setIsAvailable(isAvailable);
			branchProductRepository.save(bp);
		}
		return isAvailable;
	}

	// 2. Lấy danh sách ID món hết hàng tại chi nhánh (để lọc giao diện Menu)
	public List<Integer> getUnavailableProductIds(Integer branchId) {
		return branchProductRepository.findUnavailableProductIds(branchId);
	}
}