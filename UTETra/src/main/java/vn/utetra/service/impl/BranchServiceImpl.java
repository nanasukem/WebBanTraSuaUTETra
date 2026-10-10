package vn.utetra.service.impl;

import vn.utetra.dto.request.BranchReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.UserRepository;
import vn.utetra.service.BranchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

	private static final String ROLE_VENDOR = "VENDOR";

	private final BranchRepository branchRepository;
	private final UserRepository userRepository;

	public BranchServiceImpl(BranchRepository branchRepository, UserRepository userRepository) {
		this.branchRepository = branchRepository;
		this.userRepository = userRepository;
	}

	@Override
	public List<Branch> search(String keyword, BranchStatus status) {
		String kw = null;
		if (keyword != null && !keyword.isBlank()) {
			// Chuẩn hoá Unicode: "a + dấu" (tổ hợp) -> "ậ" (dựng sẵn), giống dữ liệu trong
			// DB
			kw = java.text.Normalizer.normalize(keyword.trim(), java.text.Normalizer.Form.NFC);
		}
		return branchRepository.search(kw, status == null ? null : status.name());
	}

	@Override
	public List<Branch> findApproved() {
		return branchRepository.findByStatusOrderByNameAsc(BranchStatus.APPROVED);
	}

	@Override
	public Branch findById(Integer id) {
		return branchRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh #" + id));
	}

	@Override
	@Transactional
	public Branch create(BranchReq req) {
		String name = req.getName().trim();
		if (branchRepository.existsByNameIgnoreCase(name)) {
			throw new BusinessException("Tên chi nhánh \"" + name + "\" đã tồn tại");
		}
		Branch branch = new Branch();
		copy(req, branch);
		branch.setStatus(BranchStatus.APPROVED); // Admin/Manager tạo thì duyệt luôn
		return branchRepository.save(branch);
	}

	@Override
	@Transactional
	public Branch update(Integer id, BranchReq req) {
		Branch branch = findById(id);
		String name = req.getName().trim();
		if (branchRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw new BusinessException("Tên chi nhánh \"" + name + "\" đã tồn tại");
		}
		copy(req, branch);
		return branchRepository.save(branch);
	}

	@Override
	@Transactional
	public void changeStatus(Integer id, BranchStatus newStatus) {
		Branch branch = findById(id);
		BranchStatus current = branch.getStatus();

		// Chỉ cho phép các bước chuyển hợp lệ
		boolean allowed = switch (newStatus) {
		case APPROVED ->
			current == BranchStatus.PENDING || current == BranchStatus.SUSPENDED || current == BranchStatus.REJECTED;
		case REJECTED -> current == BranchStatus.PENDING;
		case SUSPENDED -> current == BranchStatus.APPROVED;
		case PENDING -> false;
		};
		if (!allowed) {
			throw new BusinessException("Không thể chuyển chi nhánh từ " + current + " sang " + newStatus);
		}
		branch.setStatus(newStatus);
	}

	@Override
	public BranchReq toReq(Branch b) {
		BranchReq req = new BranchReq();
		req.setName(b.getName());
		req.setAddress(b.getAddress());
		req.setPhone(b.getPhone());
		req.setDescription(b.getDescription());
		req.setOwnerId(b.getOwner().getId());
		req.setCommissionRate(b.getCommissionRate());
		return req;
	}

	@Override
	public List<User> findVendors() {
		return userRepository.findByRole_Name(ROLE_VENDOR);
	}

	/** Chép dữ liệu form sang entity + kiểm tra chủ shop phải là VENDOR */
	private void copy(BranchReq req, Branch branch) {
		User owner = userRepository.findById(req.getOwnerId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản #" + req.getOwnerId()));
		if (!ROLE_VENDOR.equals(owner.getRole().getName())) {
			throw new BusinessException("Chủ shop phải là tài khoản có vai trò VENDOR");
		}
		branch.setOwner(owner);
		branch.setName(req.getName().trim());
		branch.setAddress(req.getAddress().trim());
		branch.setPhone(req.getPhone() == null || req.getPhone().isBlank() ? null : req.getPhone().trim());
		branch.setDescription(req.getDescription());
		branch.setCommissionRate(req.getCommissionRate());
	}
}