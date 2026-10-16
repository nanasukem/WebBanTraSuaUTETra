package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.BranchReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.UserRepository;
import vn.utetra.service.BranchService;
import vn.utetra.service.CloudinaryService;

import java.util.List;

@Service
public class BranchServiceImpl implements BranchService {

	private final BranchRepository branchRepository;
	private final UserRepository userRepository;
	private final CloudinaryService cloudinaryService;

	public BranchServiceImpl(BranchRepository branchRepository, UserRepository userRepository,
			CloudinaryService cloudinaryService) {
		this.branchRepository = branchRepository;
		this.userRepository = userRepository;
		this.cloudinaryService = cloudinaryService;
	}

	@Override
	public List<Branch> search(String keyword, BranchStatus status) {
		String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
		return branchRepository.searchBranches(searchKeyword, status);
	}

	@Override
	public List<Branch> findApproved() {
		return branchRepository.findByStatus(BranchStatus.APPROVED);
	}

	@Override
	public Branch findById(Integer id) {
		return branchRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh với ID: " + id));
	}

	@Override
	@Transactional
	public Branch create(BranchReq req) {
		User owner = userRepository.findById(req.getOwnerId())
				.orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng Vendor sở hữu"));

		Branch branch = new Branch();
		branch.setOwner(owner);
		branch.setName(req.getName());
		branch.setAddress(req.getAddress());
		branch.setPhone(req.getPhone());
		branch.setDescription(req.getDescription());
		branch.setCommissionRate(
				req.getCommissionRate() != null ? req.getCommissionRate() : new java.math.BigDecimal("5.00"));
		branch.setStatus(req.getStatus() != null ? req.getStatus() : BranchStatus.PENDING);

		if (req.getImageFile() != null && !req.getImageFile().isEmpty()) {
			String imageUrl = cloudinaryService.uploadImage(req.getImageFile(), "branches");
			branch.setImageCloudinaryId(imageUrl);
		}

		return branchRepository.save(branch);
	}

	@Override
	@Transactional
	public Branch update(Integer id, BranchReq req) {
		Branch branch = findById(id);

		if (req.getOwnerId() != null && !req.getOwnerId().equals(branch.getOwner().getId())) {
			User newOwner = userRepository.findById(req.getOwnerId())
					.orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng Vendor sở hữu"));
			branch.setOwner(newOwner);
		}

		branch.setName(req.getName());
		branch.setAddress(req.getAddress());
		branch.setPhone(req.getPhone());
		branch.setDescription(req.getDescription());
		if (req.getCommissionRate() != null) {
			branch.setCommissionRate(req.getCommissionRate());
		}
		if (req.getStatus() != null) {
			branch.setStatus(req.getStatus());
		}

		if (req.getImageFile() != null && !req.getImageFile().isEmpty()) {
			if (branch.getImageCloudinaryId() != null) {
				cloudinaryService.deleteImage(branch.getImageCloudinaryId());
			}
			String imageUrl = cloudinaryService.uploadImage(req.getImageFile(), "branches");
			branch.setImageCloudinaryId(imageUrl);
		}

		return branchRepository.save(branch);
	}

	@Override
	@Transactional
	public void changeStatus(Integer id, BranchStatus newStatus) {
		Branch branch = findById(id);
		branch.setStatus(newStatus);
		branchRepository.save(branch);
	}

	@Override
	public BranchReq toReq(Branch branch) {
		BranchReq req = new BranchReq();
		req.setId(branch.getId());
		req.setOwnerId(branch.getOwner() != null ? branch.getOwner().getId() : null);
		req.setName(branch.getName());
		req.setAddress(branch.getAddress());
		req.setPhone(branch.getPhone());
		req.setDescription(branch.getDescription());
		req.setCommissionRate(branch.getCommissionRate());
		req.setStatus(branch.getStatus());
		req.setImageCloudinaryId(branch.getImageCloudinaryId());
		return req;
	}

	@Override
	public List<User> findVendors() {
		return userRepository.findByRole_Name("VENDOR");
	}
}