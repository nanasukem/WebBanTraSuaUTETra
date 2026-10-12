package vn.utetra.exception;

public class AccountNotVerifiedException extends RuntimeException {

	private final String email;

	public AccountNotVerifiedException(String email) {
		super("Tài khoản chưa kích hoạt OTP! Vui lòng kiểm tra Email.");
		this.email = email;
	}

	public String getEmail() {
		return email;
	}
}