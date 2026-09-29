USE [master]
GO
CREATE DATABASE [UTETra]
GO
USE [UTETra]
GO
/****** Object:  Table [dbo].[Branches]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Branches](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[name] [nvarchar](100) NOT NULL,
	[address] [nvarchar](255) NOT NULL,
	[phone] [varchar](15) NULL,
	[is_active] [bit] NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[BranchProducts]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[BranchProducts](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[branch_id] [int] NOT NULL,
	[product_id] [int] NOT NULL,
	[is_available] [bit] NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Categories]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Categories](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[name] [nvarchar](100) NOT NULL,
	[image_cloudinary_id] [varchar](255) NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Notifications]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Notifications](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[user_id] [int] NOT NULL,
	[order_id] [int] NULL,
	[title] [nvarchar](150) NOT NULL,
	[content] [nvarchar](500) NULL,
	[is_read] [bit] NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[OrderDetails]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[OrderDetails](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[order_id] [int] NOT NULL,
	[product_id] [int] NOT NULL,
	[size] [char](1) NOT NULL,
	[sugar_level] [tinyint] NOT NULL,
	[ice_level] [tinyint] NOT NULL,
	[quantity] [int] NOT NULL,
	[unit_price] [decimal](12, 0) NOT NULL,
	[subtotal] [decimal](12, 0) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[OrderDetailToppings]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[OrderDetailToppings](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[order_detail_id] [int] NOT NULL,
	[topping_id] [int] NOT NULL,
	[price] [decimal](12, 0) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Orders]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Orders](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[user_id] [int] NOT NULL,
	[branch_id] [int] NOT NULL,
	[shipper_id] [int] NULL,
	[order_type] [varchar](10) NOT NULL,
	[receiver_name] [nvarchar](100) NULL,
	[receiver_phone] [varchar](15) NULL,
	[delivery_address] [nvarchar](255) NULL,
	[note] [nvarchar](500) NULL,
	[shipping_fee] [decimal](12, 0) NOT NULL,
	[total_amount] [decimal](12, 0) NOT NULL,
	[status] [varchar](20) NOT NULL,
	[payment_method] [varchar](20) NOT NULL,
	[payment_status] [varchar](20) NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
	[updated_at] [datetime2](7) NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Products]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Products](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[name] [nvarchar](150) NOT NULL,
	[base_price] [decimal](12, 0) NOT NULL,
	[description] [nvarchar](max) NULL,
	[image_cloudinary_id] [varchar](255) NULL,
	[category_id] [int] NOT NULL,
	[is_available] [bit] NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ProductSizes]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ProductSizes](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[product_id] [int] NOT NULL,
	[size] [char](1) NOT NULL,
	[extra_price] [decimal](12, 0) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[RefreshTokens]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[RefreshTokens](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[user_id] [int] NOT NULL,
	[token] [varchar](512) NOT NULL,
	[expires_at] [datetime2](7) NOT NULL,
	[revoked] [bit] NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Roles]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Roles](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[name] [varchar](30) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Toppings]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Toppings](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[name] [nvarchar](100) NOT NULL,
	[price] [decimal](12, 0) NOT NULL,
	[is_available] [bit] NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[Users]    Script Date: 29/09/2026 6:18:45 PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[Users](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[username] [varchar](50) NOT NULL,
	[password] [varchar](255) NOT NULL,
	[full_name] [nvarchar](100) NULL,
	[email] [varchar](100) NULL,
	[phone] [varchar](15) NULL,
	[avatar_cloudinary_id] [varchar](255) NULL,
	[role_id] [int] NOT NULL,
	[branch_id] [int] NULL,
	[is_active] [bit] NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
SET IDENTITY_INSERT [dbo].[Branches] ON 

INSERT [dbo].[Branches] ([id], [name], [address], [phone], [is_active]) VALUES (1, N'UTETra Võ Văn Ngân', N'1 Võ Văn Ngân, TP. Thủ Đức, TP.HCM', N'0281234567', 1)
INSERT [dbo].[Branches] ([id], [name], [address], [phone], [is_active]) VALUES (2, N'UTETra Quận 1', N'12 Nguyễn Huệ, Quận 1, TP.HCM', N'0287654321', 1)
SET IDENTITY_INSERT [dbo].[Branches] OFF
GO
SET IDENTITY_INSERT [dbo].[BranchProducts] ON 

INSERT [dbo].[BranchProducts] ([id], [branch_id], [product_id], [is_available]) VALUES (1, 1, 1, 1)
INSERT [dbo].[BranchProducts] ([id], [branch_id], [product_id], [is_available]) VALUES (2, 1, 2, 1)
INSERT [dbo].[BranchProducts] ([id], [branch_id], [product_id], [is_available]) VALUES (3, 1, 3, 1)
INSERT [dbo].[BranchProducts] ([id], [branch_id], [product_id], [is_available]) VALUES (4, 2, 1, 1)
INSERT [dbo].[BranchProducts] ([id], [branch_id], [product_id], [is_available]) VALUES (5, 2, 2, 0)
INSERT [dbo].[BranchProducts] ([id], [branch_id], [product_id], [is_available]) VALUES (6, 2, 3, 1)
SET IDENTITY_INSERT [dbo].[BranchProducts] OFF
GO
SET IDENTITY_INSERT [dbo].[Categories] ON 

INSERT [dbo].[Categories] ([id], [name], [image_cloudinary_id]) VALUES (1, N'Trà sữa', NULL)
INSERT [dbo].[Categories] ([id], [name], [image_cloudinary_id]) VALUES (2, N'Trà trái cây', NULL)
INSERT [dbo].[Categories] ([id], [name], [image_cloudinary_id]) VALUES (3, N'Cà phê', NULL)
SET IDENTITY_INSERT [dbo].[Categories] OFF
GO
SET IDENTITY_INSERT [dbo].[Notifications] ON 

INSERT [dbo].[Notifications] ([id], [user_id], [order_id], [title], [content], [is_read], [created_at]) VALUES (1, 4, 1, N'Đơn hàng #1 đã giao thành công', N'Cảm ơn bạn đã mua hàng tại UTETra!', 0, CAST(N'2026-09-29T18:10:55.6533388' AS DateTime2))
SET IDENTITY_INSERT [dbo].[Notifications] OFF
GO
SET IDENTITY_INSERT [dbo].[OrderDetails] ON 

INSERT [dbo].[OrderDetails] ([id], [order_id], [product_id], [size], [sugar_level], [ice_level], [quantity], [unit_price], [subtotal]) VALUES (1, 1, 1, N'L', 70, 50, 1, CAST(58000 AS Decimal(12, 0)), CAST(58000 AS Decimal(12, 0)))
INSERT [dbo].[OrderDetails] ([id], [order_id], [product_id], [size], [sugar_level], [ice_level], [quantity], [unit_price], [subtotal]) VALUES (2, 1, 2, N'M', 50, 100, 1, CAST(45000 AS Decimal(12, 0)), CAST(45000 AS Decimal(12, 0)))
SET IDENTITY_INSERT [dbo].[OrderDetails] OFF
GO
SET IDENTITY_INSERT [dbo].[OrderDetailToppings] ON 

INSERT [dbo].[OrderDetailToppings] ([id], [order_detail_id], [topping_id], [price]) VALUES (1, 1, 1, CAST(5000 AS Decimal(12, 0)))
INSERT [dbo].[OrderDetailToppings] ([id], [order_detail_id], [topping_id], [price]) VALUES (2, 1, 2, CAST(8000 AS Decimal(12, 0)))
SET IDENTITY_INSERT [dbo].[OrderDetailToppings] OFF
GO
SET IDENTITY_INSERT [dbo].[Orders] ON 

INSERT [dbo].[Orders] ([id], [user_id], [branch_id], [shipper_id], [order_type], [receiver_name], [receiver_phone], [delivery_address], [note], [shipping_fee], [total_amount], [status], [payment_method], [payment_status], [created_at], [updated_at]) VALUES (1, 4, 1, 3, N'DELIVERY', N'Trần Thị Khách', N'0900000004', N'KTX Khu B, Dĩ An, Bình Dương', NULL, CAST(15000 AS Decimal(12, 0)), CAST(118000 AS Decimal(12, 0)), N'COMPLETED', N'MOMO', N'PAID', CAST(N'2026-09-29T18:10:55.6521687' AS DateTime2), NULL)
SET IDENTITY_INSERT [dbo].[Orders] OFF
GO
SET IDENTITY_INSERT [dbo].[Products] ON 

INSERT [dbo].[Products] ([id], [name], [base_price], [description], [image_cloudinary_id], [category_id], [is_available], [created_at]) VALUES (1, N'Trà sữa trân châu đường đen', CAST(35000 AS Decimal(12, 0)), N'Best seller', NULL, 1, 1, CAST(N'2026-09-29T18:10:55.6503714' AS DateTime2))
INSERT [dbo].[Products] ([id], [name], [base_price], [description], [image_cloudinary_id], [category_id], [is_available], [created_at]) VALUES (2, N'Trà đào cam sả', CAST(40000 AS Decimal(12, 0)), NULL, NULL, 2, 1, CAST(N'2026-09-29T18:10:55.6503714' AS DateTime2))
INSERT [dbo].[Products] ([id], [name], [base_price], [description], [image_cloudinary_id], [category_id], [is_available], [created_at]) VALUES (3, N'Bạc xỉu', CAST(29000 AS Decimal(12, 0)), NULL, NULL, 3, 1, CAST(N'2026-09-29T18:10:55.6503714' AS DateTime2))
SET IDENTITY_INSERT [dbo].[Products] OFF
GO
SET IDENTITY_INSERT [dbo].[ProductSizes] ON 

INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (1, 1, N'S', CAST(0 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (2, 1, N'M', CAST(5000 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (3, 1, N'L', CAST(10000 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (4, 2, N'S', CAST(0 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (5, 2, N'M', CAST(5000 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (6, 2, N'L', CAST(10000 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (7, 3, N'S', CAST(0 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (8, 3, N'M', CAST(5000 AS Decimal(12, 0)))
INSERT [dbo].[ProductSizes] ([id], [product_id], [size], [extra_price]) VALUES (9, 3, N'L', CAST(10000 AS Decimal(12, 0)))
SET IDENTITY_INSERT [dbo].[ProductSizes] OFF
GO
SET IDENTITY_INSERT [dbo].[Roles] ON 

INSERT [dbo].[Roles] ([id], [name]) VALUES (1, N'ADMIN')
INSERT [dbo].[Roles] ([id], [name]) VALUES (5, N'CUSTOMER')
INSERT [dbo].[Roles] ([id], [name]) VALUES (2, N'MANAGER')
INSERT [dbo].[Roles] ([id], [name]) VALUES (4, N'SHIPPER')
INSERT [dbo].[Roles] ([id], [name]) VALUES (3, N'STAFF')
SET IDENTITY_INSERT [dbo].[Roles] OFF
GO
SET IDENTITY_INSERT [dbo].[Toppings] ON 

INSERT [dbo].[Toppings] ([id], [name], [price], [is_available]) VALUES (1, N'Trân châu đen', CAST(5000 AS Decimal(12, 0)), 1)
INSERT [dbo].[Toppings] ([id], [name], [price], [is_available]) VALUES (2, N'Thạch phô mai', CAST(8000 AS Decimal(12, 0)), 1)
INSERT [dbo].[Toppings] ([id], [name], [price], [is_available]) VALUES (3, N'Pudding trứng', CAST(7000 AS Decimal(12, 0)), 1)
SET IDENTITY_INSERT [dbo].[Toppings] OFF
GO
SET IDENTITY_INSERT [dbo].[Users] ON 

INSERT [dbo].[Users] ([id], [username], [password], [full_name], [email], [phone], [avatar_cloudinary_id], [role_id], [branch_id], [is_active], [created_at]) VALUES (1, N'admin', N'$2a$10$REPLACE_WITH_BCRYPT_HASH', N'Quản trị viên', N'admin@utetra.vn', N'0900000001', NULL, 1, NULL, 1, CAST(N'2026-09-29T18:10:55.6488263' AS DateTime2))
INSERT [dbo].[Users] ([id], [username], [password], [full_name], [email], [phone], [avatar_cloudinary_id], [role_id], [branch_id], [is_active], [created_at]) VALUES (2, N'manager1', N'$2a$10$REPLACE_WITH_BCRYPT_HASH', N'Quản lý chi nhánh', N'manager1@utetra.vn', N'0900000002', NULL, 2, 1, 1, CAST(N'2026-09-29T18:10:55.6488263' AS DateTime2))
INSERT [dbo].[Users] ([id], [username], [password], [full_name], [email], [phone], [avatar_cloudinary_id], [role_id], [branch_id], [is_active], [created_at]) VALUES (3, N'shipper1', N'$2a$10$REPLACE_WITH_BCRYPT_HASH', N'Nguyễn Văn Giao', N'shipper1@utetra.vn', N'0900000003', NULL, 4, 1, 1, CAST(N'2026-09-29T18:10:55.6488263' AS DateTime2))
INSERT [dbo].[Users] ([id], [username], [password], [full_name], [email], [phone], [avatar_cloudinary_id], [role_id], [branch_id], [is_active], [created_at]) VALUES (4, N'khach1', N'$2a$10$REPLACE_WITH_BCRYPT_HASH', N'Trần Thị Khách', N'khach1@gmail.com', N'0900000004', NULL, 5, NULL, 1, CAST(N'2026-09-29T18:10:55.6488263' AS DateTime2))
SET IDENTITY_INSERT [dbo].[Users] OFF
GO
/****** Object:  Index [UQ_BranchProducts]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[BranchProducts] ADD  CONSTRAINT [UQ_BranchProducts] UNIQUE NONCLUSTERED 
(
	[branch_id] ASC,
	[product_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [UQ__Categori__72E12F1B1C151BDC]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[Categories] ADD UNIQUE NONCLUSTERED 
(
	[name] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_Notifications_User]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_Notifications_User] ON [dbo].[Notifications]
(
	[user_id] ASC,
	[is_read] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_OrderDetails_Order]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_OrderDetails_Order] ON [dbo].[OrderDetails]
(
	[order_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [UQ_ODT]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[OrderDetailToppings] ADD  CONSTRAINT [UQ_ODT] UNIQUE NONCLUSTERED 
(
	[order_detail_id] ASC,
	[topping_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [IX_Orders_Branch_Status]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_Orders_Branch_Status] ON [dbo].[Orders]
(
	[branch_id] ASC,
	[status] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_Orders_CreatedAt]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_Orders_CreatedAt] ON [dbo].[Orders]
(
	[created_at] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_Orders_Shipper]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_Orders_Shipper] ON [dbo].[Orders]
(
	[shipper_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_Orders_User]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_Orders_User] ON [dbo].[Orders]
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_Products_Category]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_Products_Category] ON [dbo].[Products]
(
	[category_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [UQ_ProductSizes]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[ProductSizes] ADD  CONSTRAINT [UQ_ProductSizes] UNIQUE NONCLUSTERED 
(
	[product_id] ASC,
	[size] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [UQ__RefreshT__CA90DA7AB3813DD2]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[RefreshTokens] ADD UNIQUE NONCLUSTERED 
(
	[token] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_RefreshTokens_User]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_RefreshTokens_User] ON [dbo].[RefreshTokens]
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [UQ__Roles__72E12F1BF367FD70]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[Roles] ADD UNIQUE NONCLUSTERED 
(
	[name] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [UQ__Toppings__72E12F1B1A5D3325]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[Toppings] ADD UNIQUE NONCLUSTERED 
(
	[name] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [UQ__Users__AB6E61646136C1DA]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[Users] ADD UNIQUE NONCLUSTERED 
(
	[email] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
SET ANSI_PADDING ON
GO
/****** Object:  Index [UQ__Users__F3DBC57246052C9D]    Script Date: 29/09/2026 6:18:46 PM ******/
ALTER TABLE [dbo].[Users] ADD UNIQUE NONCLUSTERED 
(
	[username] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_Users_Branch]    Script Date: 29/09/2026 6:18:46 PM ******/
CREATE NONCLUSTERED INDEX [IX_Users_Branch] ON [dbo].[Users]
(
	[branch_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[Branches] ADD  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[BranchProducts] ADD  DEFAULT ((1)) FOR [is_available]
GO
ALTER TABLE [dbo].[Notifications] ADD  DEFAULT ((0)) FOR [is_read]
GO
ALTER TABLE [dbo].[Notifications] ADD  DEFAULT (sysdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[OrderDetails] ADD  DEFAULT ('M') FOR [size]
GO
ALTER TABLE [dbo].[OrderDetails] ADD  DEFAULT ((100)) FOR [sugar_level]
GO
ALTER TABLE [dbo].[OrderDetails] ADD  DEFAULT ((100)) FOR [ice_level]
GO
ALTER TABLE [dbo].[Orders] ADD  DEFAULT ('DELIVERY') FOR [order_type]
GO
ALTER TABLE [dbo].[Orders] ADD  DEFAULT ((0)) FOR [shipping_fee]
GO
ALTER TABLE [dbo].[Orders] ADD  DEFAULT ((0)) FOR [total_amount]
GO
ALTER TABLE [dbo].[Orders] ADD  DEFAULT ('PENDING') FOR [status]
GO
ALTER TABLE [dbo].[Orders] ADD  DEFAULT ('UNPAID') FOR [payment_status]
GO
ALTER TABLE [dbo].[Orders] ADD  DEFAULT (sysdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[Products] ADD  DEFAULT ((1)) FOR [is_available]
GO
ALTER TABLE [dbo].[Products] ADD  DEFAULT (sysdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[ProductSizes] ADD  DEFAULT ((0)) FOR [extra_price]
GO
ALTER TABLE [dbo].[RefreshTokens] ADD  DEFAULT ((0)) FOR [revoked]
GO
ALTER TABLE [dbo].[RefreshTokens] ADD  DEFAULT (sysdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[Toppings] ADD  DEFAULT ((1)) FOR [is_available]
GO
ALTER TABLE [dbo].[Users] ADD  DEFAULT ((1)) FOR [is_active]
GO
ALTER TABLE [dbo].[Users] ADD  DEFAULT (sysdatetime()) FOR [created_at]
GO
ALTER TABLE [dbo].[BranchProducts]  WITH CHECK ADD  CONSTRAINT [FK_BranchProducts_Branches] FOREIGN KEY([branch_id])
REFERENCES [dbo].[Branches] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[BranchProducts] CHECK CONSTRAINT [FK_BranchProducts_Branches]
GO
ALTER TABLE [dbo].[BranchProducts]  WITH CHECK ADD  CONSTRAINT [FK_BranchProducts_Products] FOREIGN KEY([product_id])
REFERENCES [dbo].[Products] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[BranchProducts] CHECK CONSTRAINT [FK_BranchProducts_Products]
GO
ALTER TABLE [dbo].[Notifications]  WITH CHECK ADD  CONSTRAINT [FK_Notifications_Orders] FOREIGN KEY([order_id])
REFERENCES [dbo].[Orders] ([id])
GO
ALTER TABLE [dbo].[Notifications] CHECK CONSTRAINT [FK_Notifications_Orders]
GO
ALTER TABLE [dbo].[Notifications]  WITH CHECK ADD  CONSTRAINT [FK_Notifications_Users] FOREIGN KEY([user_id])
REFERENCES [dbo].[Users] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[Notifications] CHECK CONSTRAINT [FK_Notifications_Users]
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD  CONSTRAINT [FK_OrderDetails_Orders] FOREIGN KEY([order_id])
REFERENCES [dbo].[Orders] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[OrderDetails] CHECK CONSTRAINT [FK_OrderDetails_Orders]
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD  CONSTRAINT [FK_OrderDetails_Products] FOREIGN KEY([product_id])
REFERENCES [dbo].[Products] ([id])
GO
ALTER TABLE [dbo].[OrderDetails] CHECK CONSTRAINT [FK_OrderDetails_Products]
GO
ALTER TABLE [dbo].[OrderDetailToppings]  WITH CHECK ADD  CONSTRAINT [FK_ODT_OrderDetails] FOREIGN KEY([order_detail_id])
REFERENCES [dbo].[OrderDetails] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[OrderDetailToppings] CHECK CONSTRAINT [FK_ODT_OrderDetails]
GO
ALTER TABLE [dbo].[OrderDetailToppings]  WITH CHECK ADD  CONSTRAINT [FK_ODT_Toppings] FOREIGN KEY([topping_id])
REFERENCES [dbo].[Toppings] ([id])
GO
ALTER TABLE [dbo].[OrderDetailToppings] CHECK CONSTRAINT [FK_ODT_Toppings]
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD  CONSTRAINT [FK_Orders_Branches] FOREIGN KEY([branch_id])
REFERENCES [dbo].[Branches] ([id])
GO
ALTER TABLE [dbo].[Orders] CHECK CONSTRAINT [FK_Orders_Branches]
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD  CONSTRAINT [FK_Orders_Shippers] FOREIGN KEY([shipper_id])
REFERENCES [dbo].[Users] ([id])
GO
ALTER TABLE [dbo].[Orders] CHECK CONSTRAINT [FK_Orders_Shippers]
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD  CONSTRAINT [FK_Orders_Users] FOREIGN KEY([user_id])
REFERENCES [dbo].[Users] ([id])
GO
ALTER TABLE [dbo].[Orders] CHECK CONSTRAINT [FK_Orders_Users]
GO
ALTER TABLE [dbo].[Products]  WITH CHECK ADD  CONSTRAINT [FK_Products_Categories] FOREIGN KEY([category_id])
REFERENCES [dbo].[Categories] ([id])
GO
ALTER TABLE [dbo].[Products] CHECK CONSTRAINT [FK_Products_Categories]
GO
ALTER TABLE [dbo].[ProductSizes]  WITH CHECK ADD  CONSTRAINT [FK_ProductSizes_Products] FOREIGN KEY([product_id])
REFERENCES [dbo].[Products] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[ProductSizes] CHECK CONSTRAINT [FK_ProductSizes_Products]
GO
ALTER TABLE [dbo].[RefreshTokens]  WITH CHECK ADD  CONSTRAINT [FK_RefreshTokens_Users] FOREIGN KEY([user_id])
REFERENCES [dbo].[Users] ([id])
ON DELETE CASCADE
GO
ALTER TABLE [dbo].[RefreshTokens] CHECK CONSTRAINT [FK_RefreshTokens_Users]
GO
ALTER TABLE [dbo].[Users]  WITH CHECK ADD  CONSTRAINT [FK_Users_Branches] FOREIGN KEY([branch_id])
REFERENCES [dbo].[Branches] ([id])
GO
ALTER TABLE [dbo].[Users] CHECK CONSTRAINT [FK_Users_Branches]
GO
ALTER TABLE [dbo].[Users]  WITH CHECK ADD  CONSTRAINT [FK_Users_Roles] FOREIGN KEY([role_id])
REFERENCES [dbo].[Roles] ([id])
GO
ALTER TABLE [dbo].[Users] CHECK CONSTRAINT [FK_Users_Roles]
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD CHECK  (([ice_level]=(100) OR [ice_level]=(70) OR [ice_level]=(50) OR [ice_level]=(30) OR [ice_level]=(0)))
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD CHECK  (([quantity]>(0)))
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD CHECK  (([subtotal]>=(0)))
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD CHECK  (([sugar_level]=(100) OR [sugar_level]=(70) OR [sugar_level]=(50) OR [sugar_level]=(30) OR [sugar_level]=(0)))
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD CHECK  (([unit_price]>=(0)))
GO
ALTER TABLE [dbo].[OrderDetails]  WITH CHECK ADD CHECK  (([size]='L' OR [size]='M' OR [size]='S'))
GO
ALTER TABLE [dbo].[OrderDetailToppings]  WITH CHECK ADD CHECK  (([price]>=(0)))
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD CHECK  (([order_type]='PICKUP' OR [order_type]='DELIVERY'))
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD CHECK  (([payment_status]='REFUNDED' OR [payment_status]='PAID' OR [payment_status]='UNPAID'))
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD CHECK  (([payment_method]='BANKING' OR [payment_method]='VNPAY' OR [payment_method]='MOMO' OR [payment_method]='CASH'))
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD CHECK  (([shipping_fee]>=(0)))
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD CHECK  (([status]='CANCELLED' OR [status]='COMPLETED' OR [status]='DELIVERING' OR [status]='PREPARING' OR [status]='CONFIRMED' OR [status]='PENDING'))
GO
ALTER TABLE [dbo].[Orders]  WITH CHECK ADD CHECK  (([total_amount]>=(0)))
GO
ALTER TABLE [dbo].[Products]  WITH CHECK ADD CHECK  (([base_price]>=(0)))
GO
ALTER TABLE [dbo].[ProductSizes]  WITH CHECK ADD CHECK  (([extra_price]>=(0)))
GO
ALTER TABLE [dbo].[ProductSizes]  WITH CHECK ADD CHECK  (([size]='L' OR [size]='M' OR [size]='S'))
GO
ALTER TABLE [dbo].[Toppings]  WITH CHECK ADD CHECK  (([price]>=(0)))
GO
USE [master]
GO
ALTER DATABASE [UTETra] SET  READ_WRITE 
GO