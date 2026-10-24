package vn.utetra.service;

import vn.utetra.dto.request.BranchReq;
import vn.utetra.dto.response.NearbyBranchRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface BranchService {

	List<Branch> search(String keyword, BranchStatus status);

	List<Branch> findApproved();

	Branch findById(Integer id);

	Branch create(BranchReq req);

	Branch update(Integer id, BranchReq req);

	void changeStatus(Integer id, BranchStatus newStatus);

	BranchReq toReq(Branch branch);

	List<User> findManagers();

	List<NearbyBranchRes> findNearby(Double lat, Double lng);

	boolean isOwner(Integer branchId, String username);

	Branch updateManagerHomepage(Integer branchId, String username, String description, MultipartFile imageFile);
}