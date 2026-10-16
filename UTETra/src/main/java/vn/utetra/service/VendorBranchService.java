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

	@Transactional
	public boolean toggleProductAvailability(Integer branchId, Integer productId, boolean isAvailable) {
		BranchProduct bp = branchProductRepository.findByBranch_IdAndProduct_Id(branchId, productId).orElseGet(() -> {
			Branch branch = branchRepository.findById(branchId)
					.orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh theo ID: " + branchId));
			Product product = productRepository.findById(productId)
					.orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm theo ID: " + productId));

			BranchProduct newBp = new BranchProduct();
			newBp.setBranch(branch);
			newBp.setProduct(product);
			return newBp;
		});

		bp.setIsAvailable(isAvailable);
		branchProductRepository.save(bp);
		return isAvailable;
	}

	@Transactional(readOnly = true)
	public List<Integer> getUnavailableProductIds(Integer branchId) {
		return branchProductRepository.findUnavailableProductIds(branchId);
	}
}