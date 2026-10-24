USE [master]
GO
IF DB_ID('UTETra') IS NOT NULL
BEGIN
    ALTER DATABASE [UTETra] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [UTETra];
END
GO
CREATE DATABASE [UTETra]
GO
USE [UTETra]
GO

CREATE TABLE Roles (
    id      INT IDENTITY(1,1) PRIMARY KEY,
    name    VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE Carriers (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL UNIQUE,
    fee         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    per_km_fee  DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (per_km_fee >= 0),
    is_active   BIT           NOT NULL DEFAULT 1
);

CREATE TABLE Users (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    username              VARCHAR(50)   NOT NULL UNIQUE,
    password              VARCHAR(255)  NOT NULL,
    full_name             NVARCHAR(100) NULL,
    email                 VARCHAR(100)  NOT NULL UNIQUE,
    phone                 VARCHAR(15)   NOT NULL UNIQUE,
    avatar_cloudinary_id  VARCHAR(255)  NULL,
    role_id               INT           NOT NULL,
    carrier_id            INT           NULL,
    branch_id             INT           NULL,
    is_verified           BIT           NOT NULL DEFAULT 0,
    is_active             BIT           NOT NULL DEFAULT 1,
    must_change_password  BIT           NOT NULL DEFAULT 0,
    temp_password_expires_at DATETIME2  NULL,
    created_at            DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Users_Roles    FOREIGN KEY (role_id)    REFERENCES Roles(id),
    CONSTRAINT FK_Users_Carriers FOREIGN KEY (carrier_id) REFERENCES Carriers(id)
);

CREATE TABLE RefreshTokens (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT          NOT NULL,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  DATETIME2    NOT NULL,
    revoked     BIT          NOT NULL DEFAULT 0,
    created_at  DATETIME2    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_RefreshTokens_Users FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE
);

CREATE TABLE OtpCodes (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    email       VARCHAR(100) NOT NULL,
    code        VARCHAR(6)   NOT NULL,
    purpose     VARCHAR(20)  NOT NULL CHECK (purpose IN ('REGISTER','RESET_PASSWORD')),
    expires_at  DATETIME2    NOT NULL,
    used        BIT          NOT NULL DEFAULT 0,
    created_at  DATETIME2    NOT NULL DEFAULT SYSDATETIME()
);

CREATE TABLE Addresses (
    id              INT IDENTITY(1,1) PRIMARY KEY,
    user_id         INT           NOT NULL,
    receiver_name   NVARCHAR(100) NOT NULL,
    receiver_phone  VARCHAR(15)   NOT NULL,
    address_line    NVARCHAR(255) NOT NULL,
    province        NVARCHAR(100) NULL,
    ward            NVARCHAR(100) NULL,
    street          NVARCHAR(150) NULL,
    address_type    VARCHAR(10)   NULL
        CONSTRAINT CK_Addresses_Type CHECK (address_type IS NULL OR address_type IN ('HOME', 'OFFICE')),
    note            NVARCHAR(200) NULL,
    latitude        DECIMAL(9,6)  NULL CONSTRAINT CK_Addresses_Lat CHECK (latitude  BETWEEN -90  AND 90),
    longitude       DECIMAL(9,6)  NULL CONSTRAINT CK_Addresses_Lng CHECK (longitude BETWEEN -180 AND 180),
    is_default      BIT           NOT NULL DEFAULT 0,
    CONSTRAINT FK_Addresses_Users FOREIGN KEY (user_id) REFERENCES Users(id) ON DELETE CASCADE
);

CREATE TABLE MemberTiers (
    id                INT IDENTITY(1,1) PRIMARY KEY,
    name              NVARCHAR(50)  NOT NULL UNIQUE,
    min_spent         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (min_spent >= 0),
    discount_percent  DECIMAL(5,2)  NOT NULL DEFAULT 0 CHECK (discount_percent BETWEEN 0 AND 100),
    max_discount      DECIMAL(12,0) NULL,
    sort_order        INT           NOT NULL DEFAULT 0
);

CREATE TABLE Branches (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    owner_id            INT           NOT NULL,
    name                NVARCHAR(100) NOT NULL,
    address             NVARCHAR(255) NOT NULL,
    latitude            DECIMAL(9,6)  NULL CONSTRAINT CK_Branches_Lat CHECK (latitude  BETWEEN -90  AND 90),
    longitude           DECIMAL(9,6)  NULL CONSTRAINT CK_Branches_Lng CHECK (longitude BETWEEN -180 AND 180),
    phone               VARCHAR(15)   NULL,
    description         NVARCHAR(500) NULL,
    image_cloudinary_id VARCHAR(255)  NULL,
    open_time           TIME(0)       NOT NULL DEFAULT '07:00',
    close_time          TIME(0)       NOT NULL DEFAULT '22:00',
    status              VARCHAR(20)   NOT NULL DEFAULT 'APPROVED' CHECK (status IN ('APPROVED','SUSPENDED')),
    created_at          DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Branches_Users FOREIGN KEY (owner_id) REFERENCES Users(id)
);
ALTER TABLE Users ADD CONSTRAINT FK_Users_Branches FOREIGN KEY (branch_id) REFERENCES Branches(id);

CREATE TABLE Categories (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    name                 NVARCHAR(100) NOT NULL UNIQUE,
    image_cloudinary_id  VARCHAR(255)  NULL
);

CREATE TABLE Products (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    category_id          INT           NOT NULL,
    name                 NVARCHAR(150) NOT NULL,
    base_price           DECIMAL(12,0) NOT NULL CHECK (base_price >= 0),
    description          NVARCHAR(MAX) NULL,
    image_cloudinary_id  VARCHAR(255)  NULL,
    sold_count           INT           NOT NULL DEFAULT 0 CHECK (sold_count >= 0),
    is_available         BIT           NOT NULL DEFAULT 1,
    created_at           DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    topping_id           INT           NULL,
    CONSTRAINT FK_Products_Categories FOREIGN KEY (category_id) REFERENCES Categories(id)
);

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

ALTER TABLE Products ADD CONSTRAINT FK_Products_Toppings FOREIGN KEY (topping_id) REFERENCES Toppings(id);
CREATE UNIQUE INDEX UQ_Products_Topping ON Products(topping_id) WHERE topping_id IS NOT NULL;

CREATE TABLE BranchToppings (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    branch_id     INT NOT NULL,
    topping_id    INT NOT NULL,
    is_available  BIT NOT NULL DEFAULT 1,
    CONSTRAINT FK_BranchToppings_Branches FOREIGN KEY (branch_id)  REFERENCES Branches(id) ON DELETE CASCADE,
    CONSTRAINT FK_BranchToppings_Toppings FOREIGN KEY (topping_id) REFERENCES Toppings(id),
    CONSTRAINT UQ_BranchToppings UNIQUE (branch_id, topping_id)
);

CREATE TABLE Vouchers (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    code             VARCHAR(30)   NOT NULL UNIQUE,
    name             NVARCHAR(150) NOT NULL,
    branch_id        INT           NULL,
    apply_to         VARCHAR(10)   NOT NULL CHECK (apply_to IN ('PRODUCT')),
    discount_type    VARCHAR(10)   NOT NULL CHECK (discount_type IN ('PERCENT','AMOUNT')),
    discount_value   DECIMAL(12,0) NOT NULL CHECK (discount_value > 0),
    max_discount     DECIMAL(12,0) NULL,
    min_order_value  DECIMAL(12,0) NOT NULL DEFAULT 0,
    quantity         INT           NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    used_count       INT           NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    start_date       DATETIME2     NOT NULL,
    end_date         DATETIME2     NOT NULL,
    is_active        BIT           NOT NULL DEFAULT 1,
    created_at       DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    per_user_limit   INT           NULL
        CONSTRAINT CK_Vouchers_PerUser CHECK (per_user_limit IS NULL OR per_user_limit > 0),
    audience         VARCHAR(20)   NOT NULL
        CONSTRAINT DF_Vouchers_Audience DEFAULT 'ALL'
        CONSTRAINT CK_Vouchers_Audience CHECK (audience IN ('ALL', 'NEW_USER', 'MIN_TIER', 'SPECIFIC')),
    min_tier_id      INT           NULL,
    payment_method   VARCHAR(20)   NULL
        CONSTRAINT CK_Vouchers_PaymentMethod
        CHECK (payment_method IS NULL OR payment_method IN ('COD', 'VNPAY', 'MOMO')),
    CONSTRAINT FK_Vouchers_Branches FOREIGN KEY (branch_id)   REFERENCES Branches(id),
    CONSTRAINT FK_Vouchers_MinTier  FOREIGN KEY (min_tier_id) REFERENCES MemberTiers(id),
    CONSTRAINT CK_Vouchers_Dates   CHECK (end_date > start_date),
    CONSTRAINT CK_Vouchers_Percent CHECK (discount_type <> 'PERCENT' OR discount_value <= 100)
);

CREATE TABLE CartItems (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    user_id      INT       NOT NULL,
    product_id   INT       NOT NULL,
    size         CHAR(1)   NOT NULL DEFAULT 'M' CHECK (size IN ('S','M','L')),
    sugar_level  TINYINT   NOT NULL DEFAULT 100 CHECK (sugar_level IN (0,30,50,70,100)),
    ice_level    TINYINT   NOT NULL DEFAULT 100 CHECK (ice_level   IN (0,30,50,70,100)),
    quantity     INT       NOT NULL DEFAULT 1 CHECK (quantity > 0),
    note         NVARCHAR(200) NULL,
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
    viewed_at   DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Viewed_Users    FOREIGN KEY (user_id)    REFERENCES Users(id)    ON DELETE CASCADE,
    CONSTRAINT FK_Viewed_Products FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE,
    CONSTRAINT UQ_Viewed UNIQUE (user_id, product_id)
);

CREATE TABLE Orders (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    user_id              INT           NOT NULL,
    branch_id            INT           NOT NULL,
    delivery_type        VARCHAR(10)   NOT NULL
        CONSTRAINT DF_Orders_DeliveryType DEFAULT 'DELIVERY'
        CONSTRAINT CK_Orders_DeliveryType CHECK (delivery_type IN ('DELIVERY', 'PICKUP')),
    carrier_id           INT           NULL,
    shipper_id           INT           NULL,
    manager_id           INT           NULL,
    product_voucher_id   INT           NULL,
    receiver_name        NVARCHAR(100) NOT NULL,
    receiver_phone       VARCHAR(15)   NOT NULL,
    delivery_address     NVARCHAR(255) NULL,
    delivery_note        NVARCHAR(200) NULL,
    note                 NVARCHAR(500) NULL,
    items_total          DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (items_total >= 0),
    product_discount     DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (product_discount >= 0),
    member_discount      DECIMAL(12,0) NOT NULL
        CONSTRAINT DF_Orders_MemberDiscount DEFAULT 0
        CONSTRAINT CK_Orders_MemberDiscount CHECK (member_discount >= 0),
    member_tier_name     NVARCHAR(50)  NULL,
    shipping_fee         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    total_amount         DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('AWAITING_PAYMENT','PENDING','CONFIRMED','READY','PICKED_UP','DELIVERING','DELIVERED','CANCELLED','RETURNED')),
    payment_method       VARCHAR(20)   NOT NULL CHECK (payment_method IN ('COD','VNPAY','MOMO')),
    payment_status       VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID','PAID','REFUND_PENDING','REFUNDED')),
    cancel_reason        NVARCHAR(255) NULL,
    refund_amount        DECIMAL(12,0) NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
    eta_minutes          INT           NULL,
    cancelled_by         VARCHAR(10)   NULL CHECK (cancelled_by IN ('CUSTOMER','STORE','SYSTEM')),
    created_at           DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    updated_at           DATETIME2     NULL,
    delivered_at         DATETIME2     NULL,
    CONSTRAINT FK_Orders_Users       FOREIGN KEY (user_id)             REFERENCES Users(id),
    CONSTRAINT FK_Orders_Branches    FOREIGN KEY (branch_id)           REFERENCES Branches(id),
    CONSTRAINT FK_Orders_Carriers    FOREIGN KEY (carrier_id)          REFERENCES Carriers(id),
    CONSTRAINT FK_Orders_Shippers    FOREIGN KEY (shipper_id)          REFERENCES Users(id),
    CONSTRAINT FK_Orders_Managers    FOREIGN KEY (manager_id)          REFERENCES Users(id),
    CONSTRAINT FK_Orders_ProdVoucher FOREIGN KEY (product_voucher_id)  REFERENCES Vouchers(id)
);

CREATE TABLE OrderDetails (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    order_id     INT           NOT NULL,
    product_id   INT           NOT NULL,
    size         CHAR(1)       NOT NULL DEFAULT 'M' CHECK (size IN ('S','M','L')),
    sugar_level  TINYINT       NOT NULL DEFAULT 100 CHECK (sugar_level IN (0,30,50,70,100)),
    ice_level    TINYINT       NOT NULL DEFAULT 100 CHECK (ice_level   IN (0,30,50,70,100)),
    quantity     INT           NOT NULL CHECK (quantity > 0),
    unit_price   DECIMAL(12,0) NOT NULL CHECK (unit_price >= 0),
    subtotal     DECIMAL(12,0) NOT NULL CHECK (subtotal >= 0),
    note         NVARCHAR(200) NULL,
    CONSTRAINT FK_OrderDetails_Orders   FOREIGN KEY (order_id)   REFERENCES Orders(id) ON DELETE CASCADE,
    CONSTRAINT FK_OrderDetails_Products FOREIGN KEY (product_id) REFERENCES Products(id)
);

CREATE TABLE OrderDetailToppings (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    order_detail_id  INT           NOT NULL,
    topping_id       INT           NOT NULL,
    price            DECIMAL(12,0) NOT NULL CHECK (price >= 0),
    CONSTRAINT FK_ODT_OrderDetails FOREIGN KEY (order_detail_id) REFERENCES OrderDetails(id) ON DELETE CASCADE,
    CONSTRAINT FK_ODT_Toppings     FOREIGN KEY (topping_id)      REFERENCES Toppings(id),
    CONSTRAINT UQ_ODT UNIQUE (order_detail_id, topping_id)
);

CREATE TABLE VoucherUsers (
    voucher_id  INT NOT NULL,
    user_id     INT NOT NULL,
    PRIMARY KEY (voucher_id, user_id),
    CONSTRAINT FK_VoucherUsers_Vouchers FOREIGN KEY (voucher_id) REFERENCES Vouchers(id) ON DELETE CASCADE,
    CONSTRAINT FK_VoucherUsers_Users    FOREIGN KEY (user_id)    REFERENCES Users(id) ON DELETE CASCADE
);

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

CREATE TABLE Payments (
    id                       INT IDENTITY(1,1) PRIMARY KEY,
    order_id                 INT           NOT NULL,
    method                   VARCHAR(20)   NOT NULL CHECK (method IN ('COD','VNPAY','MOMO')),
    txn_ref                  VARCHAR(50)   NULL,
    transaction_code         VARCHAR(100)  NULL,
    amount                   DECIMAL(12,0) NOT NULL CHECK (amount >= 0),
    status                   VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED','REFUND_PENDING','REFUNDED')),
    bank_code                VARCHAR(30)   NULL,
    gateway_date             VARCHAR(14)   NULL,
    response_code            VARCHAR(10)   NULL,
    message                  NVARCHAR(255) NULL,
    paid_at                  DATETIME2     NULL,
    created_at               DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    refund_amount            DECIMAL(12,0) NULL,
    refund_ref               VARCHAR(50)   NULL,
    refund_transaction_code  VARCHAR(100)  NULL,
    refund_requested_at      DATETIME2     NULL,
    refunded_at              DATETIME2     NULL,
    refund_attempts          INT           NOT NULL DEFAULT 0,
    refund_message           NVARCHAR(255) NULL,
    CONSTRAINT FK_Payments_Orders FOREIGN KEY (order_id) REFERENCES Orders(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX UX_Payments_TxnRef ON Payments(txn_ref) WHERE txn_ref IS NOT NULL;

CREATE TABLE Reviews (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    user_id          INT            NOT NULL,
    product_id       INT            NOT NULL,
    order_detail_id  INT            NOT NULL UNIQUE,
    rating           TINYINT        NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content          NVARCHAR(2000) NULL,
    tags             NVARCHAR(255)  NULL,
    created_at       DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Reviews_Users    FOREIGN KEY (user_id)         REFERENCES Users(id),
    CONSTRAINT FK_Reviews_Products FOREIGN KEY (product_id)      REFERENCES Products(id),
    CONSTRAINT FK_Reviews_OrderDet FOREIGN KEY (order_detail_id) REFERENCES OrderDetails(id)
);

CREATE TABLE ReviewMedia (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    review_id      INT          NOT NULL,
    cloudinary_id  VARCHAR(255) NOT NULL,
    media_type     VARCHAR(10)  NOT NULL CHECK (media_type IN ('IMAGE','VIDEO')),
    CONSTRAINT FK_ReviewMedia_Reviews FOREIGN KEY (review_id) REFERENCES Reviews(id) ON DELETE CASCADE
);

CREATE TABLE OrderIssues (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    order_id       INT            NOT NULL UNIQUE,
    user_id        INT            NOT NULL,
    reason         VARCHAR(20)    NOT NULL CHECK (reason IN ('WRONG_ITEM','MISSING_ITEM','SPILLED','QUALITY','OTHER')),
    description    NVARCHAR(500)  NULL,
    images         NVARCHAR(1000) NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','REFUNDED','REMADE','REJECTED')),
    refund_amount  DECIMAL(12,0)  NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
    store_note     NVARCHAR(255)  NULL,
    created_at     DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
    resolved_at    DATETIME2      NULL,
    removed_reviews INT           NOT NULL DEFAULT 0,
    CONSTRAINT FK_OrderIssues_Orders FOREIGN KEY (order_id) REFERENCES Orders(id) ON DELETE CASCADE,
    CONSTRAINT FK_OrderIssues_Users  FOREIGN KEY (user_id)  REFERENCES Users(id)
);

CREATE TABLE OrderReviews (
    id                INT IDENTITY(1,1) PRIMARY KEY,
    order_id          INT            NOT NULL UNIQUE,
    user_id           INT            NOT NULL,
    store_rating      TINYINT        NOT NULL CHECK (store_rating BETWEEN 1 AND 5),
    comment           NVARCHAR(1000) NULL,
    tags              NVARCHAR(255)  NULL,
    created_at        DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_OrderReviews_Orders FOREIGN KEY (order_id) REFERENCES Orders(id),
    CONSTRAINT FK_OrderReviews_Users  FOREIGN KEY (user_id)  REFERENCES Users(id)
);

CREATE TABLE OrderReviewMedia (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    order_review_id  INT          NOT NULL,
    cloudinary_id    VARCHAR(255) NOT NULL,
    media_type       VARCHAR(10)  NOT NULL CHECK (media_type IN ('IMAGE','VIDEO')),
    CONSTRAINT FK_OrderReviewMedia_OrderReviews FOREIGN KEY (order_review_id) REFERENCES OrderReviews(id) ON DELETE CASCADE
);

CREATE TABLE ReviewReplies (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    order_review_id     INT            NULL,
    review_id           INT            NULL,
    replied_by_user_id  INT            NOT NULL,
    reply_content       NVARCHAR(2000) NOT NULL,
    created_at          DATETIME2      NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_ReviewReplies_OrderReviews FOREIGN KEY (order_review_id)    REFERENCES OrderReviews(id) ON DELETE CASCADE,
    CONSTRAINT FK_ReviewReplies_Reviews      FOREIGN KEY (review_id)          REFERENCES Reviews(id)      ON DELETE CASCADE,
    CONSTRAINT FK_ReviewReplies_Users        FOREIGN KEY (replied_by_user_id) REFERENCES Users(id),
    CONSTRAINT CK_ReviewReplies_Target CHECK (
        (order_review_id IS NOT NULL AND review_id IS NULL) OR
        (order_review_id IS NULL AND review_id IS NOT NULL)
    )
);

CREATE TABLE Notifications (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    user_id     INT           NOT NULL,
    order_id    INT           NULL,
    title       NVARCHAR(150) NOT NULL,
    content     NVARCHAR(500) NULL,
    type        VARCHAR(20)   NOT NULL DEFAULT 'ORDER',
    link        VARCHAR(255)  NULL,
    ref_key     VARCHAR(100)  NULL,
    is_read     BIT           NOT NULL DEFAULT 0,
    created_at  DATETIME2     NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Notifications_Users  FOREIGN KEY (user_id)  REFERENCES Users(id) ON DELETE CASCADE,
    CONSTRAINT FK_Notifications_Orders FOREIGN KEY (order_id) REFERENCES Orders(id)
);

CREATE TABLE ActivityLogs (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    actor_id     INT            NULL,
    actor_name   NVARCHAR(150)  NOT NULL,
    actor_role   VARCHAR(20)    NOT NULL,
    action       NVARCHAR(500)  NOT NULL,
    path         VARCHAR(255)   NULL,
    link         VARCHAR(255)   NULL,
    created_at   DATETIME2      NOT NULL DEFAULT SYSDATETIME()
);
CREATE INDEX IX_ActivityLogs_Created ON ActivityLogs(created_at DESC);
GO

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
CREATE INDEX IX_ReviewReplies_OrderReview  ON ReviewReplies(order_review_id);
CREATE INDEX IX_ReviewReplies_Review       ON ReviewReplies(review_id);
CREATE INDEX IX_Notifications_User         ON Notifications(user_id, is_read);
GO

INSERT INTO Roles (name) VALUES ('ADMIN'), ('MANAGER'), ('SHIPPER'), ('USER');

INSERT INTO Carriers (name, fee, per_km_fee) VALUES (N'Shipper UTETra', 15000, 10000);

INSERT INTO Users (username, password, full_name, email, phone, avatar_cloudinary_id, role_id, carrier_id, is_verified) VALUES
 ('admin',    '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Quản trị viên',      'admin@utetra.vn',    '0900000001', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828693/admin.jpg',    1, NULL, 1),
 ('manager3', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Lê Văn Quản Lý',     'manager3@utetra.vn', '0900000002', NULL,                                                                         2, NULL, 1),
 ('manager1', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Nguyễn Quản Lý',     'manager1@utetra.vn', '0900000003', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828737/manager1.jpg', 2, NULL, 1),
 ('manager2', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Trần Quản Lý',       'manager2@utetra.vn', '0900000004', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828736/manager2.jpg', 2, NULL, 1),
 ('shipper1', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Nguyễn Văn Giao',    'shipper1@utetra.vn', '0900000005', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828748/shipper1.jpg', 3, 1,    1),
 ('user1',    '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Trần Thị Khách',     'user1@gmail.com',    '0900000006', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828749/user1.jpg',    4, NULL, 1),
 ('user2',    '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Phạm Văn Mua',       'user2@gmail.com',    '0900000007', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792829123/user2.jpg',    4, NULL, 1);

INSERT INTO Addresses (user_id, receiver_name, receiver_phone, address_line, province, ward, street, address_type, note, latitude, longitude, is_default) VALUES
 (6, N'Trần Thị Khách', '0900000006', N'KTX Khu B ĐHQG, Phường Đông Hòa, Thành phố Hồ Chí Minh',
     N'Thành phố Hồ Chí Minh', N'Phường Đông Hòa', N'KTX Khu B ĐHQG', 'HOME',   N'Toà B3, gọi trước khi tới', 10.882300, 106.782600, 1),
 (6, N'Trần Thị Khách', '0900000006', N'1 Võ Văn Ngân, Phường Thủ Đức, Thành phố Hồ Chí Minh',
     N'Thành phố Hồ Chí Minh', N'Phường Thủ Đức',  N'1 Võ Văn Ngân',  'OFFICE', NULL, 10.850700, 106.771900, 0),
 (7, N'Phạm Văn Mua',   '0900000007', N'12 Nguyễn Huệ, Phường Sài Gòn, Thành phố Hồ Chí Minh',
     N'Thành phố Hồ Chí Minh', N'Phường Sài Gòn',  N'12 Nguyễn Huệ',  'HOME',   NULL, 10.774600, 106.703900, 1);

INSERT INTO MemberTiers (name, min_spent, discount_percent, max_discount, sort_order) VALUES
 (N'Thành viên', 0,       0, NULL,  1),
 (N'Bạc',        300000,  3, 10000, 2),
 (N'Vàng',       1000000, 5, 20000, 3),
 (N'Kim cương',  3000000, 8, 30000, 4);

INSERT INTO Branches (owner_id, name, address, latitude, longitude, phone, description, image_cloudinary_id, status) VALUES
 (3, N'UTETra Võ Văn Ngân', N'1 Võ Văn Ngân, TP. Thủ Đức, TP.HCM', 10.850700, 106.771900, '0281234567', N'Chi nhánh gần ĐH Sư phạm Kỹ thuật', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792829767/branches.VVN.jpg', 'APPROVED'),
 (4, N'UTETra Quận 1',      N'12 Nguyễn Huệ, Quận 1, TP.HCM',       10.774600, 106.703900, '0287654321', N'Chi nhánh trung tâm',               'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792829768/branches.Q1.jpg',  'APPROVED');

UPDATE Users SET branch_id = 1 WHERE username = 'shipper1';

INSERT INTO Categories (name, image_cloudinary_id) VALUES
 (N'Trà sữa',      'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828687/cate.milktea.jpg'),
 (N'Trà trái cây', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828688/cate.tea.jpg'),
 (N'Cà phê',       'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828690/cate.coffee.jpg'),
 (N'Đá xay',       'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828691/cate.freezy.jpg'),
 (N'Topping',      'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828689/cate.topping.jpg');

INSERT INTO Products (category_id, name, base_price, description, image_cloudinary_id, sold_count) VALUES
 (1, N'Trà sữa trân châu đường đen', 35000, N'Best seller của quán',  'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828625/pro1.1.jpg', 152),
 (2, N'Trà đào cam sả',              40000, N'Thanh mát, giải nhiệt', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828637/pro2.1.jpg', 87),
 (3, N'Bạc xỉu',                     29000, NULL,                     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828665/pro3.1.jpg', 45),
 (4, N'Matcha đá xay',               45000, NULL,                     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828686/pro4.1.jpg', 8),
 (1, N'Trà sữa khoai môn',           30000, NULL,                     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828626/pro1.2.jpg', 64),
 (2, N'Trà vải hoa hồng',            38000, NULL,                     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828639/pro2.2.jpg', 23),
 (3, N'Cà phê muối',                 32000, NULL,                     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828666/pro3.2.jpg', 110),
 (4, N'Cookie đá xay',               49000, NULL,                     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828595/pro4.2.jpg', 3);

INSERT INTO Products (category_id, name, base_price, description, image_cloudinary_id, sold_count, created_at) VALUES
 (1, N'Trà sữa Oolong nướng',     39000, N'Oolong rang thơm khói, sữa béo nhẹ',  'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828627/pro1.3.jpg',  96,  '2026-06-05'),
 (1, N'Trà sữa matcha',           37000, N'Matcha Nhật pha sữa tươi',            'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828628/pro1.4.jpg',  58,  '2026-06-12'),
 (1, N'Trà sữa socola',           33000, N'Socola đậm vị, hợp trẻ em',           'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792847072/pro1.5.jpg',  27,  '2026-06-20'),
 (1, N'Hồng trà sữa',             31000, N'Hồng trà Ceylon, vị trà rõ',          'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828630/pro1.6.jpg',  74,  '2026-07-01'),
 (1, N'Trà sữa caramel',          36000, N'Caramel cháy thơm, ngọt dịu',         'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828631/pro1.7.jpg',  15,  '2026-07-15'),
 (1, N'Trà sữa dâu tây',          34000, N'Dâu tây Đà Lạt xay cùng sữa',         'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828633/pro1.8.jpg',  9,   '2026-08-02'),
 (1, N'Trà sữa hạnh nhân',        38000, N'Sữa hạnh nhân, ít béo',               'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828635/pro1.9.jpg',  6,   '2026-09-10'),
 (1, N'Trà sữa kem trứng nướng',  42000, N'Lớp kem trứng khò lửa phía trên',     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828636/pro1.10.jpg', 4,   '2026-10-12'),
 (2, N'Trà chanh dây',            32000, N'Chanh dây tươi, chua ngọt',           'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828640/pro2.3.jpg',  68,  '2026-06-03'),
 (2, N'Trà dứa nhiệt đới',        35000, N'Dứa tươi, thạch dừa',                 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828641/pro2.4.jpg',  41,  '2026-06-18'),
 (2, N'Trà ổi hồng',              36000, N'Ổi hồng ép, thanh mát',               'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828642/pro2.5.jpg',  33,  '2026-07-05'),
 (2, N'Trà xoài chanh leo',       39000, N'Xoài cát chín, chanh leo',            'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828662/pro2.6.jpg',  22,  '2026-07-22'),
 (2, N'Trà táo xanh bạc hà',      34000, N'Táo xanh và lá bạc hà',               'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828661/pro2.7.jpg',  12,  '2026-08-14'),
 (2, N'Trà dưa hấu',              33000, N'Dưa hấu ép mát lạnh',                 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828663/pro2.8.jpg',  8,   '2026-09-02'),
 (2, N'Trà nho xanh',             37000, N'Nho xanh không hạt, thạch nha đam',   'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828668/pro2.9.jpg',  5,   '2026-09-28'),
 (2, N'Trà mãng cầu',             39000, N'Mãng cầu xiêm xay, vị chua dịu',      'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828664/pro2.10.jpg', 2,   '2026-10-15'),
 (3, N'Cà phê sữa đá',            25000, N'Phin truyền thống, sữa đặc',          'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792847117/pro3.3.jpg',  135, '2026-06-01'),
 (3, N'Cà phê đen đá',            22000, N'Robusta đậm vị',                      'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828669/pro3.4.jpg',  88,  '2026-06-08'),
 (3, N'Bạc xỉu dừa',              33000, N'Bạc xỉu với nước cốt dừa',            'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828671/pro3.5.jpg',  47,  '2026-06-25'),
 (3, N'Cappuccino',               39000, N'Espresso, sữa nóng, bọt sữa mịn',     'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828672/pro3.6.jpg',  29,  '2026-07-12'),
 (3, N'Latte caramel',            42000, N'Latte thêm sốt caramel',              'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828673/pro3.7.jpg',  18,  '2026-08-05'),
 (3, N'Americano cam',            36000, N'Americano kèm nước cam tươi',         'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828675/pro3.8.jpg',  11,  '2026-08-25'),
 (3, N'Cà phê kem trứng',         38000, N'Cốt cà phê phủ kem trứng đánh bông',  'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828685/pro3.9.jpg',  7,   '2026-09-18'),
 (3, N'Cold brew đào',            40000, N'Cold brew ủ lạnh 16 giờ, đào ngâm',   'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828684/pro3.10.jpg', 3,   '2026-10-18'),
 (4, N'Socola đá xay',            45000, N'Socola đậm, kem tươi',                'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828596/pro4.3.jpg',  52,  '2026-06-10'),
 (4, N'Cà phê đá xay',            43000, N'Cà phê xay đá, sốt caramel',          'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828597/pro4.4.jpg',  36,  '2026-06-28'),
 (4, N'Khoai môn đá xay',         44000, N'Khoai môn tươi xay mịn',              'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828606/pro4.5.jpg',  24,  '2026-07-18'),
 (4, N'Dâu đá xay',               46000, N'Dâu tây xay cùng sữa chua',           'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828607/pro4.6.jpg',  16,  '2026-08-09'),
 (4, N'Việt quất đá xay',         47000, N'Việt quất và sữa chua',               'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828609/pro4.7.jpg',  10,  '2026-08-30'),
 (4, N'Trà xanh đậu đỏ đá xay',   45000, N'Matcha xay, đậu đỏ ninh',             'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828621/pro4.8.jpg',  6,   '2026-09-22'),
 (4, N'Bơ đá xay',                48000, N'Bơ sáp xay cùng sữa đặc',             'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828622/pro4.9.jpg',  1,   '2026-10-08'),
 (4, N'Xoài đá xay',              46000, N'Xoài cát xay mịn',                    'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828624/pro4.10.jpg', 0,   '2026-10-20');
UPDATE Products SET created_at = '2026-05-01' WHERE id <= 8;

INSERT INTO BranchProducts (branch_id, product_id, is_available) VALUES (2, 4, 0);

INSERT INTO ProductSizes (product_id, size, extra_price)
SELECT p.id, s.size, s.extra
FROM Products p
CROSS JOIN (VALUES ('S', 0), ('M', 5000), ('L', 10000)) AS s(size, extra);

INSERT INTO Toppings (name, price) VALUES
 (N'Trân châu đen', 5000), (N'Thạch phô mai', 8000), (N'Pudding trứng', 7000), (N'Kem cheese', 10000);

INSERT INTO Products (category_id, name, base_price, description, image_cloudinary_id, topping_id, created_at)
SELECT (SELECT id FROM Categories WHERE name = N'Topping'), t.name, t.price, N'Topping ' + t.name + N' mua riêng',
       CASE t.id
           WHEN 1 THEN 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828551/tr%C3%A2n_ch%C3%A2u.jpg'
           WHEN 2 THEN 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828552/vi%C3%AAn_ph%C3%B4_mai.jpg'
           WHEN 3 THEN 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828550/pudding.jpg'
           WHEN 4 THEN 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828550/kemcheese.jpg'
       END,
       t.id, '2026-05-01'
FROM Toppings t ORDER BY t.id;
INSERT INTO ProductSizes (product_id, size, extra_price) SELECT id, 'M', 0 FROM Products WHERE topping_id IS NOT NULL;

INSERT INTO Vouchers (code, name, branch_id, apply_to, discount_type, discount_value, max_discount, min_order_value,
                      quantity, used_count, start_date, end_date, per_user_limit, audience, min_tier_id, payment_method) VALUES
 ('UTETRA20',   N'Giảm 20% tối đa 30K toàn app',                     NULL, 'PRODUCT',  'PERCENT', 20,    30000, 80000,  50,     0, '2026-01-01', '2026-12-31', 1, 'ALL',      NULL, NULL),
 ('VVN15',      N'Shop Võ Văn Ngân giảm 15K',                        1,    'PRODUCT',  'AMOUNT',  15000, NULL,  60000,  30,     1, '2026-01-01', '2026-12-31', 1, 'ALL',      NULL, NULL),
 ('CHAOBANMOI', N'Bạn mới: giảm 30% tối đa 25K cho đơn đầu tiên',    NULL, 'PRODUCT',  'PERCENT', 30,    25000, 0,      100000, 0, '2026-01-01', '2027-12-31', 1, 'NEW_USER', NULL, NULL),
 ('VANG30K',    N'Hạng Vàng trở lên: giảm 30K cho đơn từ 150K',      NULL, 'PRODUCT',  'AMOUNT',  30000, NULL,  150000, 500,    0, '2026-01-01', '2027-12-31', 2, 'MIN_TIER', 3,    NULL),
 ('MOMO15K',    N'Thanh toán MoMo: giảm 15K cho đơn từ 70K',         NULL, 'PRODUCT',  'AMOUNT',  15000, NULL,  70000,  200,    0, '2026-01-01', '2027-12-31', 2, 'ALL',      NULL, 'MOMO');

INSERT INTO CartItems (user_id, product_id, size, sugar_level, ice_level, quantity) VALUES
 (6, 3, 'M', 50, 70, 1),
 (7, 7, 'L', 70, 50, 2);
INSERT INTO CartItemToppings (cart_item_id, topping_id) VALUES (2, 4);

INSERT INTO Favorites (user_id, product_id) VALUES (6, 1), (6, 7), (7, 1);
INSERT INTO ViewedProducts (user_id, product_id) VALUES (6, 1), (6, 2), (6, 5), (7, 7);

INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, product_voucher_id, receiver_name, receiver_phone, delivery_address,
                    items_total, product_discount, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, delivered_at)
VALUES (6, 1, 'DELIVERY', 1, 5, 2, N'Trần Thị Khách', '0900000006', N'KTX Khu B, Dĩ An, Bình Dương',
        148000, 15000, 0, N'Thành viên', 15000, 148000, 'DELIVERED', 'VNPAY', 'PAID', SYSDATETIME());

INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount, status, payment_method)
VALUES (6, 2, 'DELIVERY', 1, N'Trần Thị Khách', '0900000006', N'1 Võ Văn Ngân, TP. Thủ Đức, TP.HCM',
        35000, 0, N'Thành viên', 15000, 50000, 'PENDING', 'COD');

INSERT INTO OrderDetails (order_id, product_id, size, sugar_level, ice_level, quantity, unit_price, subtotal) VALUES
 (1, 1, 'L', 70, 50,  1, 58000, 58000),
 (1, 2, 'M', 50, 100, 2, 45000, 90000),
 (2, 5, 'M', 100, 100, 1, 35000, 35000);

INSERT INTO OrderDetailToppings (order_detail_id, topping_id, price) VALUES (1, 1, 5000), (1, 2, 8000);

INSERT INTO VoucherUsages (voucher_id, user_id, order_id) VALUES (2, 6, 1);

INSERT INTO Payments (order_id, method, transaction_code, amount, status, paid_at)
VALUES (1, 'VNPAY', 'VNP14567890', 148000, 'SUCCESS', SYSDATETIME());

INSERT INTO Reviews (user_id, product_id, order_detail_id, rating, content) VALUES
 (6, 1, 1, 5, N'Trà sữa trân châu đường đen rất ngon, trân châu dẻo, vị ngọt vừa phải, giao hàng nhanh, sẽ ủng hộ quán dài dài.');

INSERT INTO Users (username, password, full_name, email, phone, avatar_cloudinary_id, role_id, carrier_id, is_verified) VALUES
 ('user3', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Lê Minh Anh',   'user3@gmail.com', '0900000008', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792829126/user3.jpg', 4, NULL, 1),
 ('user4', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Ngô Gia Bảo',   'user4@gmail.com', '0900000009', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792829122/user4.jpg', 4, NULL, 1),
 ('user5', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Võ Thanh Hà',   'user5@gmail.com', '0900000010', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792829077/user5.jpg', 4, NULL, 1),
 ('user6', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Đặng Quốc Huy', 'user6@gmail.com', '0900000011', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828750/user6.jpg', 4, NULL, 1);

INSERT INTO Users (username, password, full_name, email, phone, avatar_cloudinary_id, role_id, carrier_id, branch_id, is_verified) VALUES
 ('shipper2', '$2a$10$t2Kg3w0n0BnQouy8HN4a7OUL/tCTEAkQRHFLr7cwHeCWS12K0zUM2', N'Lê Văn Chở', 'shipper2@utetra.vn', '0900000012', 'https://res.cloudinary.com/mxo7ulcv/image/upload/v1792828747/shipper2.jpg', 3, 1, 2, 1);

INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (8, 1, 'DELIVERY', 1, 5, N'Lê Minh Anh', '0900000008', N'12 Đặng Văn Bi, Phường Thủ Đức, Thành phố Hồ Chí Minh',
        122000, 0, N'Thành viên', 15000, 137000, 'DELIVERED', 'COD', 'PAID', '2026-09-05 09:15', DATEADD(MINUTE, 45, '2026-09-05 09:15'), DATEADD(MINUTE, 45, '2026-09-05 09:15'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (9, 1, 'PICKUP', NULL, NULL, N'Ngô Gia Bảo', '0900000009', NULL,
        72000, 0, N'Thành viên', 0, 72000, 'DELIVERED', 'VNPAY', 'PAID', '2026-09-08 14:30', DATEADD(MINUTE, 45, '2026-09-08 14:30'), DATEADD(MINUTE, 45, '2026-09-08 14:30'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (10, 2, 'DELIVERY', 1, 5, N'Võ Thanh Hà', '0900000010', N'45 Lê Lợi, Phường Sài Gòn, Thành phố Hồ Chí Minh',
        94000, 0, N'Thành viên', 15000, 109000, 'DELIVERED', 'MOMO', 'PAID', '2026-09-12 19:05', DATEADD(MINUTE, 45, '2026-09-12 19:05'), DATEADD(MINUTE, 45, '2026-09-12 19:05'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (11, 2, 'DELIVERY', 1, 5, N'Đặng Quốc Huy', '0900000011', N'45 Lê Lợi, Phường Sài Gòn, Thành phố Hồ Chí Minh',
        135000, 0, N'Thành viên', 15000, 150000, 'DELIVERED', 'COD', 'PAID', '2026-09-15 10:40', DATEADD(MINUTE, 45, '2026-09-15 10:40'), DATEADD(MINUTE, 45, '2026-09-15 10:40'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (8, 2, 'PICKUP', NULL, NULL, N'Lê Minh Anh', '0900000008', NULL,
        76000, 0, N'Thành viên', 0, 76000, 'DELIVERED', 'VNPAY', 'PAID', '2026-09-20 16:20', DATEADD(MINUTE, 45, '2026-09-20 16:20'), DATEADD(MINUTE, 45, '2026-09-20 16:20'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (9, 1, 'DELIVERY', 1, 5, N'Ngô Gia Bảo', '0900000009', N'12 Đặng Văn Bi, Phường Thủ Đức, Thành phố Hồ Chí Minh',
        86000, 0, N'Thành viên', 15000, 101000, 'DELIVERED', 'COD', 'PAID', '2026-09-24 08:50', DATEADD(MINUTE, 45, '2026-09-24 08:50'), DATEADD(MINUTE, 45, '2026-09-24 08:50'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (10, 1, 'DELIVERY', 1, 5, N'Võ Thanh Hà', '0900000010', N'12 Đặng Văn Bi, Phường Thủ Đức, Thành phố Hồ Chí Minh',
        92000, 0, N'Thành viên', 15000, 107000, 'DELIVERED', 'VNPAY', 'PAID', '2026-09-29 20:10', DATEADD(MINUTE, 45, '2026-09-29 20:10'), DATEADD(MINUTE, 45, '2026-09-29 20:10'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (11, 1, 'PICKUP', NULL, NULL, N'Đặng Quốc Huy', '0900000011', NULL,
        71000, 0, N'Thành viên', 0, 71000, 'DELIVERED', 'COD', 'PAID', '2026-10-03 07:45', DATEADD(MINUTE, 45, '2026-10-03 07:45'), DATEADD(MINUTE, 45, '2026-10-03 07:45'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (8, 1, 'DELIVERY', 1, 5, N'Lê Minh Anh', '0900000008', N'12 Đặng Văn Bi, Phường Thủ Đức, Thành phố Hồ Chí Minh',
        132000, 0, N'Thành viên', 15000, 147000, 'DELIVERED', 'MOMO', 'PAID', '2026-10-08 18:30', DATEADD(MINUTE, 45, '2026-10-08 18:30'), DATEADD(MINUTE, 45, '2026-10-08 18:30'));
INSERT INTO Orders (user_id, branch_id, delivery_type, carrier_id, shipper_id, receiver_name, receiver_phone, delivery_address,
                    items_total, member_discount, member_tier_name, shipping_fee, total_amount,
                    status, payment_method, payment_status, created_at, updated_at, delivered_at)
VALUES (10, 2, 'DELIVERY', 1, 5, N'Võ Thanh Hà', '0900000010', N'45 Lê Lợi, Phường Sài Gòn, Thành phố Hồ Chí Minh',
        94000, 0, N'Thành viên', 15000, 109000, 'DELIVERED', 'COD', 'PAID', '2026-10-14 12:00', DATEADD(MINUTE, 45, '2026-10-14 12:00'), DATEADD(MINUTE, 45, '2026-10-14 12:00'));

INSERT INTO OrderDetails (order_id, product_id, size, sugar_level, ice_level, quantity, unit_price, subtotal) VALUES
 (3, 1, 'M', 70, 70, 2, 40000, 80000),
 (3, 17, 'L', 70, 70, 1, 42000, 42000),
 (4, 7, 'M', 70, 70, 1, 37000, 37000),
 (4, 25, 'L', 70, 70, 1, 35000, 35000),
 (5, 9, 'L', 70, 70, 1, 49000, 49000),
 (5, 2, 'M', 70, 70, 1, 45000, 45000),
 (6, 33, 'M', 70, 70, 2, 50000, 100000),
 (6, 18, 'S', 70, 70, 1, 35000, 35000),
 (7, 12, 'M', 70, 70, 1, 36000, 36000),
 (7, 5, 'L', 70, 70, 1, 40000, 40000),
 (8, 19, 'M', 70, 70, 1, 41000, 41000),
 (8, 1, 'L', 70, 70, 1, 45000, 45000),
 (9, 25, 'S', 70, 70, 2, 25000, 50000),
 (9, 10, 'M', 70, 70, 1, 42000, 42000),
 (10, 3, 'M', 70, 70, 1, 34000, 34000),
 (10, 17, 'M', 70, 70, 1, 37000, 37000),
 (11, 7, 'L', 70, 70, 2, 42000, 84000),
 (11, 34, 'M', 70, 70, 1, 48000, 48000),
 (12, 2, 'L', 70, 70, 1, 50000, 50000),
 (12, 9, 'M', 70, 70, 1, 44000, 44000);

UPDATE Orders SET manager_id = (SELECT b.owner_id FROM Branches b WHERE b.id = Orders.branch_id) WHERE manager_id IS NULL;

INSERT INTO Payments (order_id, method, transaction_code, amount, status, paid_at) VALUES
 (4, 'VNPAY', 'VNP2026100004', 72000, 'SUCCESS', '2026-09-08 14:30'),
 (5, 'MOMO', 'MOM2026100005', 109000, 'SUCCESS', '2026-09-12 19:05'),
 (7, 'VNPAY', 'VNP2026100007', 76000, 'SUCCESS', '2026-09-20 16:20'),
 (9, 'VNPAY', 'VNP2026100009', 107000, 'SUCCESS', '2026-09-29 20:10'),
 (11, 'MOMO', 'MOM2026100011', 147000, 'SUCCESS', '2026-10-08 18:30');

INSERT INTO Reviews (user_id, product_id, order_detail_id, rating, content) VALUES
 (8, 1, 4, 5, N'Uống rất ngon, vị vừa miệng, đá không bị nhạt, nhân viên đóng gói cẩn thận, giao hàng đúng giờ, sẽ đặt tiếp.'),
 (8, 17, 5, 5, N'Món này tuyệt vời, thơm và đậm vị đúng như mô tả, size L uống đã, giá hợp lý so với chất lượng của quán.'),
 (9, 7, 6, 4, N'Khá ngon, vị đậm và thơm, chỉ hơi ngọt một chút so với khẩu vị của mình, lần sau sẽ chọn ít đường hơn.'),
 (9, 25, 7, 5, N'Uống rất ngon, vị vừa miệng, đá không bị nhạt, nhân viên đóng gói cẩn thận, giao hàng đúng giờ, sẽ đặt tiếp.'),
 (10, 9, 8, 5, N'Món này tuyệt vời, thơm và đậm vị đúng như mô tả, size L uống đã, giá hợp lý so với chất lượng của quán.'),
 (10, 2, 9, 4, N'Đồ uống ổn, đóng gói kỹ, giao hơi trễ khoảng mười phút nhưng món vẫn còn lạnh, nhìn chung là hài lòng.'),
 (11, 33, 10, 3, N'Vị tạm được, không đặc sắc lắm, đá tan hơi nhanh làm nhạt nước, hy vọng quán cải thiện thêm lần sau nhé.'),
 (11, 18, 11, 5, N'Món này tuyệt vời, thơm và đậm vị đúng như mô tả, size L uống đã, giá hợp lý so với chất lượng của quán.'),
 (8, 5, 13, 4, N'Khá ngon, vị đậm và thơm, chỉ hơi ngọt một chút so với khẩu vị của mình, lần sau sẽ chọn ít đường hơn.'),
 (9, 19, 14, 5, N'Uống rất ngon, vị vừa miệng, đá không bị nhạt, nhân viên đóng gói cẩn thận, giao hàng đúng giờ, sẽ đặt tiếp.'),
 (9, 1, 15, 5, N'Món này tuyệt vời, thơm và đậm vị đúng như mô tả, size L uống đã, giá hợp lý so với chất lượng của quán.'),
 (10, 25, 16, 4, N'Đồ uống ổn, đóng gói kỹ, giao hơi trễ khoảng mười phút nhưng món vẫn còn lạnh, nhìn chung là hài lòng.'),
 (10, 10, 17, 5, N'Uống rất ngon, vị vừa miệng, đá không bị nhạt, nhân viên đóng gói cẩn thận, giao hàng đúng giờ, sẽ đặt tiếp.'),
 (11, 17, 19, 5, N'Món này tuyệt vời, thơm và đậm vị đúng như mô tả, size L uống đã, giá hợp lý so với chất lượng của quán.'),
 (8, 7, 20, 4, N'Khá ngon, vị đậm và thơm, chỉ hơi ngọt một chút so với khẩu vị của mình, lần sau sẽ chọn ít đường hơn.'),
 (10, 2, 22, 5, N'Uống rất ngon, vị vừa miệng, đá không bị nhạt, nhân viên đóng gói cẩn thận, giao hàng đúng giờ, sẽ đặt tiếp.');

INSERT INTO Favorites (user_id, product_id) VALUES
 (8, 1), (8, 9), (8, 17), (8, 25), (8, 33), (8, 7),
 (9, 1), (9, 17), (9, 25), (9, 19), (9, 2), (9, 12),
 (10, 1), (10, 9), (10, 2), (10, 17), (10, 26), (10, 10), (10, 34),
 (11, 1), (11, 17), (11, 33), (11, 7), (11, 9), (11, 25), (11, 13), (11, 20), (11, 27);

INSERT INTO OrderReviews (order_id, user_id, store_rating, comment) VALUES
 (1, 6, 5, N'Quán đóng gói kỹ, nhân viên dễ thương');

INSERT INTO OrderReviews (order_id, user_id, store_rating, comment, tags) VALUES
 (3, 8,  5, N'Quán làm nước nhanh, đóng gói chắc chắn không bị đổ giọt nào.', N'Làm món nhanh|Đóng gói kỹ'),
 (4, 9,  4, N'Đến lấy tại quầy nhân viên nhiệt tình, không gian sạch sẽ.', N'Nhân viên thân thiện'),
 (5, 10, 5, N'Chi nhánh Quận 1 view đẹp, shipper giao tới đá vẫn còn nguyên.', N'Đóng gói kỹ|Nhân viên thân thiện'),
 (6, 11, 4, N'Phục vụ tốt, đóng gói cẩn thận nhưng giờ cao điểm chờ hơi lâu xíu.', N'Đóng gói kỹ');

INSERT INTO ReviewReplies (order_review_id, review_id, replied_by_user_id, reply_content) VALUES
 (1, NULL, 3, N'Cảm ơn bạn Trần Thị Khách đã ủng hộ UTETra Võ Văn Ngân! Mong sớm được phục vụ bạn lần tới ạ.'),
 (4, NULL, 4, N'UTETra Quận 1 cảm ơn bạn Thanh Hà rất nhiều! Chúc bạn một ngày tốt lành nhé.'),
 (NULL, 1, 1, N'UTETra cảm ơn đánh giá 5 sao của bạn dành cho món Trà sữa trân châu đường đen ạ!');

INSERT INTO Notifications (user_id, order_id, type, title, content, link, is_read) VALUES
 (6, 1, 'REVIEW', N'Đơn #1 đã giao thành công', N'Bạn thấy món và quán thế nào? Đánh giá ngay nhé', '/orders/1/review', 0),
 (4, 2, 'ORDER',  N'Có đơn hàng mới #2',        N'Giao tận nơi · 1 ly · 50.000đ',            '/manager/orders/2', 0),
 (1, NULL, 'USER', N'Khách mới đăng ký: user2',  N'Phạm Văn Mua · user2@gmail.com',           '/admin/users', 0);
GO

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
SELECT * FROM OrderReviews;
SELECT * FROM ReviewReplies;