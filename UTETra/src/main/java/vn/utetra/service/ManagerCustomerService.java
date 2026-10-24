package vn.utetra.service;

import vn.utetra.dto.response.ManagerCustomerDetailRes;
import vn.utetra.dto.response.ManagerCustomerRes;

import java.util.List;

public interface ManagerCustomerService {

	List<ManagerCustomerRes> customers(Integer managerId, Integer branchId, String keyword);

	ManagerCustomerDetailRes customer(Integer managerId, Integer branchId, Integer userId);
}
