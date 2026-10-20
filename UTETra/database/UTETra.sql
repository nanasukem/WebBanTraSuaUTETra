/* =========================================================
   UTETra - DATABASE ĐẦY ĐỦ (bản gộp)
   Gộp sẵn: UTETra.sql (gốc, menu chung)
          + v2: hạng thành viên, giới hạn voucher, ưu đãi tài khoản mới
          + v3: giao tận nơi / đến lấy, voucher theo phương thức thanh toán, tọa độ chi nhánh & địa chỉ
          + v4: địa chỉ kiểu Shopee (Tỉnh/Thành, Phường/Xã sau sáp nhập 2025, Tên đường, Loại địa chỉ)
          + v5: ghi chú địa chỉ (Addresses.note, Orders.delivery_note)
   CHỈ CẦN CHẠY FILE NÀY (không chạy thêm file v2, v3 nữa).
   LƯU Ý: nếu máy đã có database UTETra, file này sẽ XOÁ và tạo lại từ đầu (mất dữ liệu đang có).
   ========================================================= */

USE [master]
GO
IF DB_ID('UTETra') IS NOT NULL
BEGIN
    ALTER DATABASE [UTETra] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; -- ngắt các kết nối đang mở (app, SSMS)
    DROP DATABASE [UTETra];
END
GO
CREATE DATABASE [UTETra]
GO
USE [UTETra]
GO

/* =========================================================
   1. NGƯỜI DÙNG, PHÂN QUYỀN, XÁC THỰC
   ========================================================= */

-- Roles: ADMIN, MANAGER, VENDOR, SHIPPER, USER (Guest = chưa đăng nhập, không cần role)
CREATE TABLE Roles (
    id      INT IDENTITY(1,1) PRIMARY KEY,
    name    VARCHAR(30) NOT NULL UNIQUE
);

-- Nhà vận chuyển (Manager/Admin quản lý)
CREATE TABLE Carriers (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL UNIQUE,
    fee         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    is_active   BIT           NOT NULL DEFAULT 1
);

CREATE TABLE Users (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    username              VARCHAR(50)   NOT NULL UNIQUE,
    password              VARCHAR(255)  NOT NULL,              -- BCrypt hash
    full_name             NVARCHAR(100) NULL,
    email                 VARCHAR(100)  NOT NULL UNIQUE,       -- bắt buộc để gửi OTP
    phone                 VARCHAR(15)   NULL,
    avatar_cloudinary_id  VARCHAR(255)  NULL,
    role_id               INT           NOT NULL,
    carrier_id            INT           NULL,                  -- chỉ dùng cho SHIPPER
    is_verified           BIT           NOT NULL DEFAULT 0,    -- đã kích hoạt bằng OTP
    is_active             BIT           NOT NULL DEFAULT 1,    -- 0 = bị khoá
    created_at            DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Users_Roles    FOREIGN KEY (role_id)    REFERENCES Roles(id),
    CONSTRAINT FK_Users_Carriers FOREIGN KEY (carrier_id) REFERENCES Carriers(id)
);

-- JWT refresh token
CREATE TABLE RefreshTokens (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT          NOT NULL,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  DATETIME2    NOT NULL,
    revoked     BIT          NOT NULL DEFAULT 0,
    created_at  DATETIME2    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_RefreshTokens_Users FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE
);

-- OTP gửi qua email: kích hoạt tài khoản / quên mật khẩu
CREATE TABLE OtpCodes (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    email       VARCHAR(100) NOT NULL,
    code        VARCHAR(6)   NOT NULL,
    purpose     VARCHAR(20)  NOT NULL CHECK (purpose IN ('REGISTER','RESET_PASSWORD')),
    expires_at  DATETIME2    NOT NULL,
    used        BIT          NOT NULL DEFAULT 0,
    created_at  DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

-- Nhiều địa chỉ nhận hàng cho mỗi user (kiểu Shopee: Tỉnh/Thành + Phường/Xã sau sáp nhập 2025 + Tên đường/Số nhà)
-- address_line = street + ', ' + ward + ', ' + province (server tự ghép; đơn hàng chép dòng này lúc đặt)
-- tọa độ: ghim trên bản đồ, dùng gợi ý chi nhánh gần nhất
CREATE TABLE Addresses (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    user_id         INT           NOT NULL,
    receiver_name   NVARCHAR(100) NOT NULL,
    receiver_phone  VARCHAR(15)   NOT NULL,
    address_line    NVARCHAR(255) NOT NULL,
    province        NVARCHAR(100) NULL,                     -- Tỉnh/Thành phố
    ward            NVARCHAR(100) NULL,                     -- Phường/Xã/Đặc khu
    street          NVARCHAR(150) NULL,                     -- Tên đường, Toà nhà, Số nhà
    address_type    VARCHAR(10)   NULL
        CONSTRAINT CK_Addresses_Type CHECK (address_type IS NULL OR address_type IN ('HOME', 'OFFICE')), -- Nhà riêng / Văn phòng
    note            NVARCHAR(200) NULL,                     -- Ghi chú địa chỉ: cổng sau, gửi bảo vệ...
    latitude        DECIMAL(9,6)  NULL CONSTRAINT CK_Addresses_Lat CHECK (latitude  BETWEEN -90  AND 90),
    longitude       DECIMAL(9,6)  NULL CONSTRAINT CK_Addresses_Lng CHECK (longitude BETWEEN -180 AND 180),
    is_default      BIT           NOT NULL DEFAULT 0,
    CONSTRAINT FK_Addresses_Users FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE
);

-- Hạng thành viên (Admin sửa được). Xét theo tổng tiền các đơn ĐÃ GIAO trong 12 tháng gần nhất
CREATE TABLE MemberTiers (
    id                INT IDENTITY(1,1) PRIMARY KEY,
    name              NVARCHAR(50)  NOT NULL UNIQUE,
    min_spent         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (min_spent >= 0),                     -- chi tiêu tối thiểu để đạt hạng
    discount_percent  DECIMAL(5,2)  NOT NULL DEFAULT 0 CHECK (discount_percent BETWEEN 0 AND 100), -- % giảm tiền món mỗi đơn
    max_discount      DECIMAL(12,0) NULL,                                                          -- giảm tối đa mỗi đơn
    sort_order        INT           NOT NULL DEFAULT 0
);

/* =========================================================
   2. CHI NHÁNH & MENU CHUNG CỦA CHUỖI
   Admin quản lý 1 menu chung (Products) cho tất cả chi nhánh.
   Mỗi chi nhánh chỉ bật/tắt món còn bán (BranchProducts).
   ========================================================= */

-- Branches = chi nhánh (shop). Vendor đăng ký quản lý -> PENDING -> Manager/Admin duyệt
CREATE TABLE Branches (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    owner_id            INT           NOT NULL,                -- Vendor sở hữu
    name                NVARCHAR(100) NOT NULL,
    address             NVARCHAR(255) NOT NULL,
    latitude            DECIMAL(9,6)  NULL CONSTRAINT CK_Branches_Lat CHECK (latitude  BETWEEN -90  AND 90),   -- vĩ độ
    longitude           DECIMAL(9,6)  NULL CONSTRAINT CK_Branches_Lng CHECK (longitude BETWEEN -180 AND 180),  -- kinh độ
    phone               VARCHAR(15)   NULL,
    description         NVARCHAR(500) NULL,
    image_cloudinary_id VARCHAR(255)  NULL,
    commission_rate     DECIMAL(5,2)  NOT NULL DEFAULT 5.00 CHECK (commission_rate BETWEEN 0 AND 100), -- % chiết khấu app
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','SUSPENDED')),
    created_at          DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Branches_Users FOREIGN KEY (owner_id) REFERENCES Users(id)
);

CREATE TABLE Categories (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    name                 NVARCHAR(100) NOT NULL UNIQUE,
    image_cloudinary_id  VARCHAR(255)  NULL
);

-- Menu chung của cả chuỗi (Admin quản lý). is_available = 0: ngừng bán toàn chuỗi
CREATE TABLE Products (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    category_id          INT           NOT NULL,
    name                 NVARCHAR(150) NOT NULL,
    base_price           DECIMAL(12,0) NOT NULL CHECK (base_price >= 0),
    description          NVARCHAR(MAX) NULL,
    image_cloudinary_id  VARCHAR(255)  NULL,
    sold_count           INT           NOT NULL DEFAULT 0 CHECK (sold_count >= 0), -- tăng khi đơn DELIVERED
    is_available         BIT           NOT NULL DEFAULT 1,
    created_at           DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Products_Categories FOREIGN KEY (category_id) REFERENCES Categories(id)
);

-- Tình trạng món theo từng chi nhánh (Vendor bật/tắt món hết hàng).
-- Không có dòng cho (chi nhánh, món) = món đang bán ở chi nhánh đó.
CREATE TABLE BranchProducts (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    branch_id     INT NOT NULL,
    product_id    INT NOT NULL,
    is_available  BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_BranchProducts_Branches FOREIGN KEY (branch_id)  REFERENCES Branches(id) ON DELETE CASCADE,
    CONSTRAINT FK_BranchProducts_Products FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE,
    CONSTRAINT UQ_BranchProducts UNIQUE (branch_id, product_id)
);

CREATE TABLE ProductSizes (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    product_id   INT           NOT NULL,
    size         CHAR(1)       NOT NULL CHECK (size IN ('S','M','L')),
    extra_price  DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (extra_price >= 0),
    CONSTRAINT FK_ProductSizes_Products FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE,
    CONSTRAINT UQ_ProductSizes UNIQUE (product_id, size)
);

CREATE TABLE Toppings (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    name          NVARCHAR(100) NOT NULL UNIQUE,
    price         DECIMAL(12,0) NOT NULL CHECK (price >= 0),
    is_available  BIT           NOT NULL DEFAULT 1
);

/* =========================================================
   3. KHUYẾN MÃI
   branch_id NULL  = khuyến mãi toàn chuỗi (Manager/Admin tạo)
   branch_id có giá trị = khuyến mãi riêng của chi nhánh (Vendor tạo)
   Giới hạn: quantity = tổng lượt toàn hệ thống, per_user_limit = lượt mỗi tài khoản
   ========================================================= */
CREATE TABLE Vouchers (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    code             VARCHAR(30)   NOT NULL UNIQUE,
    name             NVARCHAR(150) NOT NULL,
    branch_id        INT           NULL,
    apply_to         VARCHAR(10)   NOT NULL CHECK (apply_to IN ('PRODUCT','SHIPPING')),     -- giảm tiền món / giảm phí ship
    discount_type    VARCHAR(10)   NOT NULL CHECK (discount_type IN ('PERCENT','AMOUNT')),
    discount_value   DECIMAL(12,0) NOT NULL CHECK (discount_value > 0),
    max_discount     DECIMAL(12,0) NULL,                                                     -- trần giảm khi PERCENT
    min_order_value  DECIMAL(12,0) NOT NULL DEFAULT 0,
    quantity         INT           NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    used_count       INT           NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    start_date       DATETIME2     NOT NULL,
    end_date         DATETIME2     NOT NULL,
    is_active        BIT           NOT NULL DEFAULT 1,
    created_at       DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    -- v2: giới hạn theo người dùng
    per_user_limit   INT           NULL
        CONSTRAINT CK_Vouchers_PerUser CHECK (per_user_limit IS NULL OR per_user_limit > 0),      -- NULL = không giới hạn mỗi người
    audience         VARCHAR(20)   NOT NULL
        CONSTRAINT DF_Vouchers_Audience DEFAULT 'ALL'
        CONSTRAINT CK_Vouchers_Audience CHECK (audience IN ('ALL', 'NEW_USER', 'MIN_TIER')),
    min_tier_id      INT           NULL,                                                     -- dùng khi audience = MIN_TIER
    -- v3: chỉ áp dụng cho 1 phương thức thanh toán
    payment_method   VARCHAR(20)   NULL
        CONSTRAINT CK_Vouchers_PaymentMethod
        CHECK (payment_method IS NULL OR payment_method IN ('COD', 'VNPAY', 'MOMO')),           -- NULL = mọi phương thức
    CONSTRAINT FK_Vouchers_Branches FOREIGN KEY (branch_id)   REFERENCES Branches(id),
    CONSTRAINT FK_Vouchers_MinTier  FOREIGN KEY (min_tier_id) REFERENCES MemberTiers(id),
    CONSTRAINT CK_Vouchers_Dates   CHECK (end_date > start_date),
    CONSTRAINT CK_Vouchers_Percent CHECK (discount_type <> 'PERCENT' OR discount_value <= 100)
);

/* =========================================================
   4. GIỎ HÀNG (lưu DB), YÊU THÍCH, ĐÃ XEM
   ========================================================= */
CREATE TABLE CartItems (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    user_id      INT       NOT NULL,
    product_id   INT       NOT NULL,
    size         CHAR(1)   NOT NULL DEFAULT 'M' CHECK (size IN ('S','M','L')),
    sugar_level  TINYINT   NOT NULL DEFAULT 100 CHECK (sugar_level IN (0,30,50,70,100)),
    ice_level    TINYINT   NOT NULL DEFAULT 100 CHECK (ice_level   IN (0,30,50,70,100)),
    quantity     INT       NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at   DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_CartItems_Users    FOREIGN KEY (user_id)    REFERENCES Users(id)    ON DELETE CASCADE,
    CONSTRAINT FK_CartItems_Products FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE
);

CREATE TABLE CartItemToppings (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    cart_item_id  INT NOT NULL,
    topping_id    INT NOT NULL,
    CONSTRAINT FK_CIT_CartItems FOREIGN KEY (cart_item_id) REFERENCES CartItems(id) ON DELETE CASCADE,
    CONSTRAINT FK_CIT_Toppings  FOREIGN KEY (topping_id)   REFERENCES Toppings(id),
    CONSTRAINT UQ_CIT UNIQUE (cart_item_id, topping_id)
);

CREATE TABLE Favorites (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT       NOT NULL,
    product_id  INT       NOT NULL,
    created_at  DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Favorites_Users    FOREIGN KEY (user_id)    REFERENCES Users(id)    ON DELETE CASCADE,
    CONSTRAINT FK_Favorites_Products FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE,
    CONSTRAINT UQ_Favorites UNIQUE (user_id, product_id)
);

CREATE TABLE ViewedProducts (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT       NOT NULL,
    product_id  INT       NOT NULL,
    viewed_at   DATETIME2 NOT NULL DEFAULT SYSDATETIME(),   -- xem lại thì cập nhật thời gian
    CONSTRAINT FK_Viewed_Users    FOREIGN KEY (user_id)    REFERENCES Users(id)    ON DELETE CASCADE,
    CONSTRAINT FK_Viewed_Products FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE,
    CONSTRAINT UQ_Viewed UNIQUE (user_id, product_id)
);

/* =========================================================
   5. ĐƠN HÀNG & THANH TOÁN
   Mỗi đơn thuộc 1 chi nhánh (khách chọn chi nhánh khi Checkout).
   total_amount = items_total - product_discount - member_discount + shipping_fee - shipping_discount
   delivery_type: DELIVERY = giao tận nơi (có carrier, địa chỉ, phí ship)
                  PICKUP   = khách đến chi nhánh lấy (không carrier, không địa chỉ, ship = 0)
   ========================================================= */
CREATE TABLE Orders (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    user_id              INT           NOT NULL,
    branch_id            INT           NOT NULL,
    delivery_type        VARCHAR(10)   NOT NULL
        CONSTRAINT DF_Orders_DeliveryType DEFAULT 'DELIVERY'
        CONSTRAINT CK_Orders_DeliveryType CHECK (delivery_type IN ('DELIVERY', 'PICKUP')),
    carrier_id           INT           NULL,                -- NULL khi đến lấy
    shipper_id           INT           NULL,
    product_voucher_id   INT           NULL,
    shipping_voucher_id  INT           NULL,
    receiver_name        NVARCHAR(100) NOT NULL,            -- chép từ Addresses lúc đặt (đến lấy: khách nhập)
    receiver_phone       VARCHAR(15)   NOT NULL,
    delivery_address     NVARCHAR(255) NULL,                -- NULL khi đến lấy
    delivery_note        NVARCHAR(200) NULL,                -- ghi chú địa chỉ, chép từ Addresses lúc đặt
    note                 NVARCHAR(500) NULL,
    items_total          DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (items_total >= 0),
    product_discount     DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (product_discount >= 0),
    member_discount      DECIMAL(12,0) NOT NULL
        CONSTRAINT DF_Orders_MemberDiscount DEFAULT 0
        CONSTRAINT CK_Orders_MemberDiscount CHECK (member_discount >= 0),                  -- giảm theo hạng thành viên
    member_tier_name     NVARCHAR(50)  NULL,                -- hạng lúc đặt (chốt lại)
    shipping_fee         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    shipping_discount    DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (shipping_discount >= 0),
    total_amount         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    commission_amount    DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (commission_amount >= 0), -- tiền app thu của shop
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','CONFIRMED','PICKED_UP','DELIVERING','DELIVERED','CANCELLED','RETURNED')),
    payment_method       VARCHAR(20)   NOT NULL CHECK (payment_method IN ('COD','VNPAY','MOMO')),
    payment_status       VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PAID','REFUNDED')),
    cancel_reason        NVARCHAR(255) NULL,
    created_at           DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    updated_at           DATETIME2     NULL,
    delivered_at         DATETIME2     NULL,
    CONSTRAINT FK_Orders_Users       FOREIGN KEY (user_id)             REFERENCES Users(id),
    CONSTRAINT FK_Orders_Branches    FOREIGN KEY (branch_id)           REFERENCES Branches(id),
    CONSTRAINT FK_Orders_Carriers    FOREIGN KEY (carrier_id)          REFERENCES Carriers(id),
    CONSTRAINT FK_Orders_Shippers    FOREIGN KEY (shipper_id)          REFERENCES Users(id),
    CONSTRAINT FK_Orders_ProdVoucher FOREIGN KEY (product_voucher_id)  REFERENCES Vouchers(id),
    CONSTRAINT FK_Orders_ShipVoucher FOREIGN KEY (shipping_voucher_id) REFERENCES Vouchers(id)
);

CREATE TABLE OrderDetails (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    order_id     INT           NOT NULL,
    product_id   INT           NOT NULL,
    size         CHAR(1)       NOT NULL DEFAULT 'M' CHECK (size IN ('S','M','L')),
    sugar_level  TINYINT       NOT NULL DEFAULT 100 CHECK (sugar_level IN (0,30,50,70,100)),
    ice_level    TINYINT       NOT NULL DEFAULT 100 CHECK (ice_level   IN (0,30,50,70,100)),
    quantity     INT           NOT NULL CHECK (quantity > 0),
    unit_price   DECIMAL(12,0) NOT NULL CHECK (unit_price >= 0),  -- base + size + topping lúc đặt
    subtotal     DECIMAL(12,0) NOT NULL CHECK (subtotal >= 0),    -- unit_price * quantity
    CONSTRAINT FK_OrderDetails_Orders   FOREIGN KEY (order_id)   REFERENCES Orders(id) ON DELETE CASCADE,
    CONSTRAINT FK_OrderDetails_Products FOREIGN KEY (product_id) REFERENCES Products(id)
);

CREATE TABLE OrderDetailToppings (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    order_detail_id  INT           NOT NULL,
    topping_id       INT           NOT NULL,
    price            DECIMAL(12,0) NOT NULL CHECK (price >= 0),  -- giá topping lúc đặt
    CONSTRAINT FK_ODT_OrderDetails FOREIGN KEY (order_detail_id) REFERENCES OrderDetails(id) ON DELETE CASCADE,
    CONSTRAINT FK_ODT_Toppings     FOREIGN KEY (topping_id)      REFERENCES Toppings(id),
    CONSTRAINT UQ_ODT UNIQUE (order_detail_id, topping_id)
);

-- Lịch sử dùng voucher: đếm lượt mỗi người, hoàn lượt khi huỷ đơn
CREATE TABLE VoucherUsages (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    voucher_id  INT       NOT NULL,
    user_id     INT       NOT NULL,
    order_id    INT       NOT NULL,
    used_at     DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_VoucherUsages_Vouchers FOREIGN KEY (voucher_id) REFERENCES Vouchers(id),
    CONSTRAINT FK_VoucherUsages_Users    FOREIGN KEY (user_id)    REFERENCES Users(id),
    CONSTRAINT FK_VoucherUsages_Orders   FOREIGN KEY (order_id)   REFERENCES Orders(id) ON DELETE CASCADE
);

-- Kết quả thanh toán VNPAY / MOMO (COD cũng ghi 1 dòng khi giao xong)
CREATE TABLE Payments (
    id                INT IDENTITY(1,1) PRIMARY KEY,
    order_id          INT           NOT NULL,
    method            VARCHAR(20)   NOT NULL CHECK (method IN ('COD','VNPAY','MOMO')),
    transaction_code  VARCHAR(100)  NULL,
    amount            DECIMAL(12,0) NOT NULL CHECK (amount >= 0),
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SUCCESS','FAILED','REFUNDED')),
    paid_at           DATETIME2     NULL,
    created_at        DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Payments_Orders FOREIGN KEY (order_id) REFERENCES Orders(id) ON DELETE CASCADE
);

/* =========================================================
   6. ĐÁNH GIÁ & BÌNH LUẬN (chỉ món đã mua, mỗi ly đã mua đánh giá 1 lần)
   ========================================================= */
CREATE TABLE Reviews (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    user_id          INT            NOT NULL,
    product_id       INT            NOT NULL,
    order_detail_id  INT            NOT NULL UNIQUE,
    rating           TINYINT        NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content          NVARCHAR(2000) NOT NULL,
    created_at       DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT CK_Reviews_Content   CHECK (LEN(content) >= 50),
    CONSTRAINT FK_Reviews_Users     FOREIGN KEY (user_id)         REFERENCES Users(id),
    CONSTRAINT FK_Reviews_Products  FOREIGN KEY (product_id)      REFERENCES Products(id),
    CONSTRAINT FK_Reviews_OrderDet  FOREIGN KEY (order_detail_id) REFERENCES OrderDetails(id)
);

CREATE TABLE ReviewMedia (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    review_id      INT          NOT NULL,
    cloudinary_id  VARCHAR(255) NOT NULL,
    media_type     VARCHAR(10)  NOT NULL CHECK (media_type IN ('IMAGE','VIDEO')),
    CONSTRAINT FK_ReviewMedia_Reviews FOREIGN KEY (review_id) REFERENCES Reviews(id) ON DELETE CASCADE
);

/* =========================================================
   7. THÔNG BÁO (WebSocket đẩy realtime + lưu DB)
   ========================================================= */
CREATE TABLE Notifications (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT           NOT NULL,
    order_id    INT           NULL,
    title       NVARCHAR(150) NOT NULL,
    content     NVARCHAR(500) NULL,
    is_read     BIT           NOT NULL DEFAULT 0,
    created_at  DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Notifications_Users  FOREIGN KEY (user_id)  REFERENCES Users(id) ON DELETE CASCADE,
    CONSTRAINT FK_Notifications_Orders FOREIGN KEY (order_id) REFERENCES Orders(id)
);
GO

/* ---------- Index ---------- */
CREATE INDEX IX_Users_Role                 ON Users(role_id);
CREATE INDEX IX_OtpCodes_Email             ON OtpCodes(email, purpose);
CREATE INDEX IX_Addresses_User             ON Addresses(user_id);
CREATE INDEX IX_Branches_Owner             ON Branches(owner_id);
CREATE INDEX IX_Products_Category          ON Products(category_id);
CREATE INDEX IX_Products_SoldCount         ON Products(sold_count DESC);
CREATE INDEX IX_Products_CreatedAt         ON Products(created_at DESC);
CREATE INDEX IX_CartItems_User             ON CartItems(user_id);
CREATE INDEX IX_Orders_User_Status         ON Orders(user_id, status);
CREATE INDEX IX_Orders_Branch_Status       ON Orders(branch_id, status);
CREATE INDEX IX_Orders_Shipper_Status      ON Orders(shipper_id, status);
CREATE INDEX IX_Orders_CreatedAt           ON Orders(created_at);
CREATE INDEX IX_OrderDetails_Order         ON OrderDetails(order_id);
CREATE INDEX IX_VoucherUsages_Voucher_User ON VoucherUsages(voucher_id, user_id);
CREATE INDEX IX_Reviews_Product            ON Reviews(product_id);
CREATE INDEX IX_Notifications_User         ON Notifications(user_id, is_read);
GO

/* =========================================================
   DỮ LIỆU MẪU
   Mật khẩu của TẤT CẢ tài khoản mẫu: 123456 (đã mã hoá BCrypt)
   ========================================================= */
INSERT INTO Roles (name) VALUES ('ADMIN'), ('MANAGER'), ('VENDOR'), ('SHIPPER'), ('USER');
-- 1 ADMIN, 2 MANAGER, 3 VENDOR, 4 SHIPPER, 5 USER

INSERT INTO Carriers (name, fee) VALUES (N'Giao Hàng Nhanh', 15000), (N'Giao Hàng Tiết Kiệm', 12000);

INSERT INTO Users (username, password, full_name, email, phone, role_id, carrier_id, is_verified) VALUES
 ('admin',    '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Quản trị viên',      'admin@utetra.vn',    '0900000001', 1, NULL, 1),
 ('manager1', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Lê Văn Quản Lý',     'manager1@utetra.vn', '0900000002', 2, NULL, 1),
 ('vendor1',  '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Nguyễn Chủ Shop',    'vendor1@utetra.vn',  '0900000003', 3, NULL, 1),
 ('vendor2',  '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Trần Chủ Shop',      'vendor2@utetra.vn',  '0900000004', 3, NULL, 1),
 ('shipper1', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Nguyễn Văn Giao',    'shipper1@utetra.vn', '0900000005', 4, 1,    1),
 ('user1',    '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Trần Thị Khách',     'user1@gmail.com',    '0900000006', 5, NULL, 1),
 ('user2',    '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Phạm Văn Mua',       'user2@gmail.com',    '0900000007', 5, NULL, 1);

-- Địa chỉ kiểu mới (phường sau sáp nhập 01/07/2025) kèm tọa độ (gần đúng)
--  KTX Khu B (P. Đông Hòa, Dĩ An cũ)  | 1 Võ Văn Ngân (P. Linh Chiểu cũ -> P. Thủ Đức) | 12 Nguyễn Huệ (P. Bến Nghé cũ -> P. Sài Gòn)
INSERT INTO Addresses (user_id, receiver_name, receiver_phone, address_line, province, ward, street, address_type, note, latitude, longitude, is_default) VALUES
 (6, N'Trần Thị Khách', '0900000006', N'KTX Khu B ĐHQG, Phường Đông Hòa, Thành phố Hồ Chí Minh',
     N'Thành phố Hồ Chí Minh', N'Phường Đông Hòa', N'KTX Khu B ĐHQG', 'HOME',   N'Toà B3, gọi trước khi tới', 10.882300, 106.782600, 1),
 (6, N'Trần Thị Khách', '0900000006', N'1 Võ Văn Ngân, Phường Thủ Đức, Thành phố Hồ Chí Minh',
     N'Thành phố Hồ Chí Minh', N'Phường Thủ Đức',  N'1 Võ Văn Ngân',  'OFFICE', NULL, 10.850700, 106.771900, 0),
 (7, N'Phạm Văn Mua',   '0900000007', N'12 Nguyễn Huệ, Phường Sài Gòn, Thành phố Hồ Chí Minh',
     N'Thành phố Hồ Chí Minh', N'Phường Sài Gòn',  N'12 Nguyễn Huệ',  'HOME',   NULL, 10.774600, 106.703900, 1);

-- Hạng thành viên: id 1 Thành viên, 2 Bạc, 3 Vàng, 4 Kim cương
INSERT INTO MemberTiers (name, min_spent, discount_percent, max_discount, sort_order) VALUES
 (N'Thành viên', 0,       0, NULL,  1),
 (N'Bạc',        300000,  3, 10000, 2),
 (N'Vàng',       1000000, 5, 20000, 3),
 (N'Kim cương',  3000000, 8, 30000, 4);

-- Chi nhánh kèm tọa độ (gần đúng)
INSERT INTO Branches (owner_id, name, address, latitude, longitude, phone, description, commission_rate, status) VALUES
 (3, N'UTETra Võ Văn Ngân', N'1 Võ Văn Ngân, TP. Thủ Đức, TP.HCM', 10.850700, 106.771900, '0281234567', N'Chi nhánh gần ĐH Sư phạm Kỹ thuật', 5.00, 'APPROVED'),
 (4, N'UTETra Quận 1',      N'12 Nguyễn Huệ, Quận 1, TP.HCM',       10.774600, 106.703900, '0287654321', N'Chi nhánh trung tâm',              5.00, 'APPROVED');

INSERT INTO Categories (name) VALUES (N'Trà sữa'), (N'Trà trái cây'), (N'Cà phê'), (N'Đá xay');

-- Menu chung: cả 2 chi nhánh đều bán 8 món này
INSERT INTO Products (category_id, name, base_price, description, sold_count) VALUES
 (1, N'Trà sữa trân châu đường đen', 35000, N'Best seller của quán', 152),
 (2, N'Trà đào cam sả',              40000, N'Thanh mát, giải nhiệt', 87),
 (3, N'Bạc xỉu',                     29000, NULL, 45),
 (4, N'Matcha đá xay',               45000, NULL, 8),
 (1, N'Trà sữa khoai môn',           30000, NULL, 64),
 (2, N'Trà vải hoa hồng',            38000, NULL, 23),
 (3, N'Cà phê muối',                 32000, NULL, 110),
 (4, N'Cookie đá xay',               49000, NULL, 3);

-- Demo: chi nhánh Quận 1 (id 2) đang hết Matcha đá xay (id 4); các món khác bán bình thường
INSERT INTO BranchProducts (branch_id, product_id, is_available) VALUES (2, 4, 0);

INSERT INTO ProductSizes (product_id, size, extra_price)
SELECT p.id, s.size, s.extra
FROM Products p
CROSS JOIN (VALUES ('S', 0), ('M', 5000), ('L', 10000)) AS s(size, extra);

INSERT INTO Toppings (name, price) VALUES
 (N'Trân châu đen', 5000), (N'Thạch phô mai', 8000), (N'Pudding trứng', 7000), (N'Kem cheese', 10000);

-- Voucher: id 1 FREESHIP10K, 2 UTETRA20, 3 VVN15, 4 CHAOBANMOI, 5 FREESHIPMOI, 6 VANG30K, 7 MOMO15K
INSERT INTO Vouchers (code, name, branch_id, apply_to, discount_type, discount_value, max_discount, min_order_value,
                      quantity, used_count, start_date, end_date, per_user_limit, audience, min_tier_id, payment_method) VALUES
 -- Mã thường (có giới hạn mỗi người)
 ('FREESHIP10K', N'Giảm 10K phí ship toàn app',                        NULL, 'SHIPPING', 'AMOUNT',  10000, NULL,  50000,  100,    1, '2026-01-01', '2026-12-31', 3, 'ALL',      NULL, NULL),
 ('UTETRA20',    N'Giảm 20% tối đa 30K toàn app',                      NULL, 'PRODUCT',  'PERCENT', 20,    30000, 80000,  50,     0, '2026-01-01', '2026-12-31', 1, 'ALL',      NULL, NULL),
 ('VVN15',       N'Shop Võ Văn Ngân giảm 15K',                         1,    'PRODUCT',  'AMOUNT',  15000, NULL,  60000,  30,     0, '2026-01-01', '2026-12-31', 1, 'ALL',      NULL, NULL),
 -- Ưu đãi tài khoản mới: chỉ đơn đầu tiên, mỗi người 1 lần
 ('CHAOBANMOI',  N'Bạn mới: giảm 30% tối đa 25K cho đơn đầu tiên',     NULL, 'PRODUCT',  'PERCENT', 30,    25000, 0,      100000, 0, '2026-01-01', '2027-12-31', 1, 'NEW_USER', NULL, NULL),
 ('FREESHIPMOI', N'Bạn mới: miễn phí ship tối đa 15K cho đơn đầu tiên', NULL, 'SHIPPING', 'AMOUNT',  15000, NULL,  0,      100000, 0, '2026-01-01', '2027-12-31', 1, 'NEW_USER', NULL, NULL),
 -- Theo hạng: từ hạng Vàng (id 3) trở lên, mỗi người 2 lần
 ('VANG30K',     N'Hạng Vàng trở lên: giảm 30K cho đơn từ 150K',       NULL, 'PRODUCT',  'AMOUNT',  30000, NULL,  150000, 500,    0, '2026-01-01', '2027-12-31', 2, 'MIN_TIER', 3,    NULL),
 -- Theo phương thức thanh toán: chỉ khi trả bằng MoMo, mỗi người 2 lần
 ('MOMO15K',     N'Thanh toán MoMo: giảm 15K cho đơn từ 70K',          NULL, 'PRODUCT',  'AMOUNT',  15000, NULL,  70000,  200,    0, '2026-01-01', '2027-12-31', 2, 'ALL',      NULL, 'MOMO');

INSERT INTO CartItems (user_id, product_id, size, sugar_level, ice_level, quantity) VALUES
 (6, 3, 'M', 50, 70, 1),
 (7, 7, 'L', 70, 50, 2);
INSERT INTO CartItemToppings (cart_item_id, topping_id) VALUES (2, 4);

INSERT INTO Favorites (user_id, product_id) VALUES (6, 1), (6, 7), (7, 1);
INSERT INTO ViewedProducts (user_id, product_id) VALUES (6, 1), (6, 2), (6, 5), (7, 7);

-- Đơn 1 (đã giao, giao tận nơi): (35000+10000+5000+8000)*1 + (40000+5000)*2 = 148000
--                  + ship 15000 - FREESHIP10K 10000 = 153000; chiết khấu 5% * 148000 = 7400
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, shipping_voucher_id, receiver_name, receiver_phone, delivery_address,
                    items_total, product_discount, member_discount, member_tier_name, shipping_fee, shipping_discount, total_amount, commission_amount,
                    status, payment_method, payment_status, delivered_at)
VALUES (6, 1, 'DELIVERY', 1, 5, 1, N'Trần Thị Khách', '0900000006', N'KTX Khu B, Dĩ An, Bình Dương',
        148000, 0, 0, N'Thành viên', 15000, 10000, 153000, 7400, 'DELIVERED', 'VNPAY', 'PAID', SYSDATETIME());

-- Đơn 2 (mới đặt, giao tận nơi): (30000+5000)*1 + ship 15000 = 50000; chiết khấu 5% * 35000 = 1750
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount, commission_amount, status, payment_method)
VALUES (6, 2, 'DELIVERY', 1, N'Trần Thị Khách', '0900000006', N'1 Võ Văn Ngân, TP. Thủ Đức, TP.HCM',
        35000, 0, N'Thành viên', 15000, 50000, 1750, 'PENDING', 'COD');

INSERT INTO OrderDetails (order_id, product_id, size, sugar_level, ice_level, quantity, unit_price, subtotal) VALUES
 (1, 1, 'L', 70, 50,  1, 58000, 58000),
 (1, 2, 'M', 50, 100, 2, 45000, 90000),
 (2, 5, 'M', 100, 100, 1, 35000, 35000);

INSERT INTO OrderDetailToppings (order_detail_id, topping_id, price) VALUES (1, 1, 5000), (1, 2, 8000);

-- Đơn #1 của user1 đã dùng FREESHIP10K -> ghi lịch sử dùng mã
INSERT INTO VoucherUsages (voucher_id, user_id, order_id) VALUES (1, 6, 1);

INSERT INTO Payments (order_id, method, transaction_code, amount, status, paid_at)
VALUES (1, 'VNPAY', 'VNP14567890', 153000, 'SUCCESS', SYSDATETIME());

INSERT INTO Reviews (user_id, product_id, order_detail_id, rating, content) VALUES
 (6, 1, 1, 5, N'Trà sữa trân châu đường đen rất ngon, trân châu dẻo, vị ngọt vừa phải, giao hàng nhanh, sẽ ủng hộ quán dài dài.');

INSERT INTO Notifications (user_id, order_id, title, content) VALUES
 (6, 1, N'Đơn hàng #1 đã giao thành công', N'Cảm ơn bạn đã mua hàng tại UTETra!'),
 (3, 2, N'Có đơn hàng mới #2',             N'Shop UTETra Quận 1 vừa nhận đơn mới.');
GO

/* ---------- Kiểm tra nhanh ---------- */
SELECT o.id, u.full_name AS customer, b.name AS shop, o.delivery_type, p.name AS product, od.size,
       t.name AS topping, od.subtotal, o.total_amount, o.status
FROM Orders o
JOIN Users u          ON u.id = o.user_id
JOIN Branches b       ON b.id = o.branch_id
JOIN OrderDetails od  ON od.order_id = o.id
JOIN Products p       ON p.id = od.product_id
LEFT JOIN OrderDetailToppings odt ON odt.order_detail_id = od.id
LEFT JOIN Toppings t  ON t.id = odt.topping_id;

SELECT * FROM MemberTiers ORDER BY min_spent;
SELECT id, code, apply_to, quantity, used_count, per_user_limit, audience, min_tier_id, payment_method FROM Vouchers;
SELECT * FROM VoucherUsages;
SELECT id, name, latitude, longitude, status FROM Branches;
SELECT id, user_id, street, ward, province, address_type, address_line, latitude, longitude FROM Addresses;