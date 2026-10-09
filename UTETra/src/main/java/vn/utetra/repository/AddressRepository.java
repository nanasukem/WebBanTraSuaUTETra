package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Address;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Integer> {
	List<Address> findByUserId(Integer userId);

	Optional<Address> findByUserIdAndIsDefaultTrue(Integer userId);
}