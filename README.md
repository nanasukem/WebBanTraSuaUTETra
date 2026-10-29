# Tổng quan về hệ thống UTETra
UTETra là một nền tảng web thương mại điện tử dành cho mô hình chuỗi cửa hàng đồ uống đa chi nhánh. Dự án được phát triển nhằm tối ưu hóa quy trình từ khâu đặt món trực tuyến của khách hàng, điều phối đơn hàng thời gian thực tại quầy pha chế, vận chuyển nội bộ cho đến công tác quản trị tài chính và báo cáo toàn chuỗi. Bên cạnh đó, hệ thống còn ứng dụng các giải pháp công nghệ hiện đại như xác thực bảo mật, cập nhật trạng thái đơn hàng theo thời gian thực qua giao thức WebSocket và hỗ trợ thanh toán đa kênh linh hoạt nhằm mang đến trải nghiệm liền mạch, minh bạch và tối ưu hóa hiệu quả vận hành cho toàn bộ mô hình kinh doanh.

# Hướng dẫn cài đặt và chạy ứng dụng UTETra

Website chuỗi cửa hàng trà sữa UTETra (Đề 11): Spring Boot + Thymeleaf + Bootstrap 5 + Spring Data JPA + SQL Server + JWT + WebSocket + Cloudinary.

## 1. Phần mềm cần cài

| Phần mềm | Phiên bản | Ghi chú |
|---|---|---|
| JDK | 27 | Đúng với `java.version` trong `pom.xml` |
| Spring Tools for Eclipse (STS) hoặc IntelliJ / VS Code | bản mới | Có sẵn Maven, hoặc dùng `mvnw` đi kèm project |
| SQL Server | 2019 trở lên (bản Express được) | Bật đăng nhập SQL Server Authentication, cổng 1433 |
| SQL Server Management Studio (SSMS) | bản mới | Để chạy script tạo database |
| Trình duyệt | Chrome / Edge | |

Lombok: project dùng Lombok; IDE cần bật annotation processing (STS/IntelliJ tự nhận khi import Maven).

## 2. Tạo database

1. Mở SSMS, đăng nhập SQL Server.
2. Mở file `database/UTETra.sql` trong project.
3. Bấm **Execute**. Script tự tạo database `UTETra`, 33 bảng và dữ liệu mẫu (chi nhánh, món, voucher, tài khoản, đơn hàng).
4. Kiểm tra: mở database `UTETra`, thấy các bảng `Users`, `Orders`, `Products`…

## 3. Tạo file `.env`

Tạo file `.env` ở thư mục gốc project (cùng chỗ với `pom.xml`). Ứng dụng tự đọc file này khi khởi động (`spring.config.import=optional:file:.env[.properties]`).

```properties
SERVER_PORT=8080

DB_URL=jdbc:sqlserver://localhost:1433;databaseName=UTETra;encrypt=true;trustServerCertificate=true
DB_USERNAME=sa
DB_PASSWORD=<mật khẩu SQL Server>
DDL_AUTO=none
SHOW_SQL=true

JWT_SECRET=<chuỗi bí mật dài ít nhất 32 ký tự>
JWT_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<gmail dùng để gửi OTP>
MAIL_PASSWORD=<App Password 16 ký tự của Gmail>

CLOUDINARY_CLOUD_NAME=<cloud name>
CLOUDINARY_API_KEY=<api key>
CLOUDINARY_API_SECRET=<api secret>

VNPAY_TMN_CODE=<mã website sandbox VNPAY>
VNPAY_HASH_SECRET=<chuỗi bí mật sandbox VNPAY>
PAYMENT_TIMEOUT_MINUTES=15
PAYMENT_IPN_BASE_URL=
```

| Biến | Ý nghĩa |
|---|---|
| `DB_*` | Kết nối SQL Server |
| `JWT_SECRET` | Khoá ký JWT |
| `JWT_EXPIRATION` | Thời hạn access token, mili giây (900000 = 15 phút) |
| `JWT_REFRESH_EXPIRATION` | Thời hạn refresh token, mili giây (604800000 = 7 ngày) |
| `MAIL_*` | Gmail SMTP gửi OTP đăng ký, quên mật khẩu, mật khẩu tạm của nhân viên. Dùng App Password, không dùng mật khẩu Gmail thường |
| `CLOUDINARY_*` | Lưu ảnh món, ảnh/video đánh giá và khiếu nại |
| `VNPAY_*` | Cổng VNPAY môi trường sandbox |
| `PAYMENT_IPN_BASE_URL` | Địa chỉ public (ví dụ ngrok) để VNPAY/MoMo gọi IPN về máy; để trống khi chạy local thì hệ thống tự đồng bộ trạng thái thanh toán |

MoMo dùng sẵn tài khoản test công khai cấu hình trong `application.properties`, không cần khai báo thêm. File `.env` chứa khoá bí mật nên **không đưa lên git**.

## 4. Chạy ứng dụng

### Cách 1: Spring Tools for Eclipse (STS)

1. **File → Import → Maven → Existing Maven Projects**, chọn thư mục `UTETra` (chỗ có `pom.xml`).
2. Đợi Maven tải thư viện xong. Nếu báo lỗi: chuột phải project → **Maven → Update Project…** → tick **Force Update** → OK.
3. Chuột phải `UteTraApplication.java` (package `vn.utetra`) → **Run As → Spring Boot App**.
4. Console hiện `Started UteTraApplication` là chạy thành công.

Nếu gặp lỗi `Could not find or load main class vn.utetra.UteTraApplication`: bấm **Project → Clean…** (Build Automatically phải được tick) rồi chạy lại.

### Cách 2: Dòng lệnh

```bash
cd UTETra
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

## 5. Truy cập và tài khoản mẫu

Mở `http://localhost:8080`. Tất cả tài khoản mẫu có mật khẩu **123456**. Đăng nhập được bằng tên đăng nhập, email hoặc số điện thoại.

| Vai trò | Tài khoản | Trang sau khi đăng nhập |
|---|---|---|
| Admin | `admin` | `/admin/dashboard` |
| Manager chi nhánh Võ Văn Ngân | `manager1` | `/manager/dashboard` |
| Manager chi nhánh Quận 1 | `manager2` | `/manager/dashboard` |
| Manager chưa có chi nhánh | `manager3` | `/manager/dashboard` |
| Shipper chi nhánh Võ Văn Ngân | `shipper1` | `/shipper/orders` |
| Shipper chi nhánh Quận 1 | `shipper2` | `/shipper/orders` |
| Khách hàng | `user1` … `user6` | Trang chủ |

## 6. Thử trên điện thoại (cùng mạng Wi-Fi)

1. Xem IP máy tính: mở Command Prompt, gõ `ipconfig`, lấy dòng IPv4 (ví dụ `192.168.1.15`).
2. Thêm vào `.env`: `APP_BASE_URL=http://192.168.1.15:8080`, rồi chạy lại ứng dụng.
3. Trên điện thoại mở `http://192.168.1.15:8080`. Nếu không vào được, cho phép Java qua Windows Firewall.

## 7. Thanh toán thử

- **COD:** đặt hàng là xong, đơn hiện ngay ở Quầy đơn của manager.
- **VNPAY sandbox:** chọn ngân hàng NCB, dùng thẻ test do VNPAY cung cấp trong trang hướng dẫn sandbox.
- **MoMo test:** quét mã bằng app MoMo bản test, hoặc làm theo hướng dẫn trên trang thanh toán test của MoMo.
- Quá 15 phút không thanh toán, đơn tự huỷ và trả lại lượt voucher.

## 8. Lỗi thường gặp

| Lỗi | Cách xử lý |
|---|---|
| `Login failed for user 'sa'` | Kiểm tra `DB_USERNAME`, `DB_PASSWORD`; bật SQL Server Authentication |
| `The TCP/IP connection to the host localhost, port 1433 has failed` | Mở SQL Server Configuration Manager, bật TCP/IP và cổng 1433, khởi động lại dịch vụ SQL Server |
| `Could not resolve placeholder 'JWT_SECRET'` | Thiếu file `.env` hoặc thiếu biến; `.env` phải nằm cạnh `pom.xml` |
| Không nhận được email OTP | Dùng App Password của Gmail và bật xác minh 2 bước |
| Ảnh món không hiện | Kiểm tra `CLOUDINARY_*` và kết nối Internet |
| Port 8080 đã được dùng | Đổi `SERVER_PORT` trong `.env` |
