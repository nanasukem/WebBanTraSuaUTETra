/**
 * cart.js - Giỏ hàng phía client (UTETra, tuần 1)
 * - Lưu giỏ trong localStorage với key "utetra_cart".
 * - Điều khiển modal chọn món (fragments/product-modal.html).
 * - Vẽ trang giỏ hàng (cart/index.html).
 * Món chọn y hệt cấu hình (size, đường, đá, topping) thì cộng dồn số lượng.
 */
(function() {
    'use strict';

    const STORAGE_KEY = 'utetra_cart';
    const MAX_QTY = 50;
    const DEFAULT_SIZES = { S: 0, M: 5000, L: 10000 };
    const NO_IMAGE = '/images/no-image.png'; // ảnh mặc định khi món chưa có ảnh

    // ================== Tiện ích ==================
    const formatVnd = (n) => Number(n || 0).toLocaleString('vi-VN') + 'đ';

    // Chống XSS: escape chữ trước khi chèn vào HTML
    const escapeHtml = (s) => String(s ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');

    const clampQty = (q) => Math.min(MAX_QTY, Math.max(1, parseInt(q, 10) || 1));

    // ================== Kho giỏ hàng ==================
    const Cart = {
        getItems() {
            try {
                const data = JSON.parse(localStorage.getItem(STORAGE_KEY));
                return Array.isArray(data) ? data : [];
            } catch (e) {
                return [];
            }
        },

        save(items) {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
            Cart.updateBadge();
            document.dispatchEvent(new CustomEvent('cart:changed', { detail: items }));
        },

        buildKey(item) {
            const tops = item.toppings.map(t => t.id).sort((a, b) => a - b).join('.');
            return [item.productId, item.size, item.sugar, item.ice, tops].join('|');
        },

        add(item) {
            const items = Cart.getItems();
            item.key = Cart.buildKey(item);
            const existed = items.find(i => i.key === item.key);
            if (existed) {
                existed.quantity = clampQty(existed.quantity + item.quantity);
            } else {
                items.push(item);
            }
            Cart.save(items);
        },

        updateQty(key, qty) {
            const items = Cart.getItems();
            const it = items.find(i => i.key === key);
            if (!it) return;
            it.quantity = clampQty(qty);
            Cart.save(items);
        },

        remove(key) {
            Cart.save(Cart.getItems().filter(i => i.key !== key));
        },

        clear() {
            Cart.save([]);
        },

        totalQty() {
            return Cart.getItems().reduce((s, i) => s + i.quantity, 0);
        },

        subtotal() {
            return Cart.getItems().reduce((s, i) => s + i.unitPrice * i.quantity, 0);
        },

        updateBadge() {
            document.querySelectorAll('#cart-count, .cart-count').forEach(el => {
                el.textContent = Cart.totalQty();
            });
        }
    };

    window.UtetraCart = Cart; // cho trang checkout dùng lại

    // ================== Toast thông báo ==================
    function toast(message, type = 'success') {
        let box = document.getElementById('utetra-toast-box');
        if (!box) {
            box = document.createElement('div');
            box.id = 'utetra-toast-box';
            box.className = 'toast-container position-fixed bottom-0 end-0 p-3';
            box.style.zIndex = 1080;
            document.body.appendChild(box);
        }
        const el = document.createElement('div');
        el.className = `toast align-items-center text-bg-${type} border-0`;
        el.setAttribute('role', 'alert');
        el.innerHTML = `<div class="d-flex"><div class="toast-body">${escapeHtml(message)}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>`;
        box.appendChild(el);
        const t = new bootstrap.Toast(el, { delay: 2500 });
        el.addEventListener('hidden.bs.toast', () => el.remove());
        t.show();
    }

    // ================== Modal chọn món ==================
    function initProductModal() {
        const modalEl = document.getElementById('productModal');
        if (!modalEl) return;
        const modal = bootstrap.Modal.getOrCreateInstance(modalEl);

        const $ = (id) => document.getElementById(id);
        let current = null; // món đang mở

        function readSizes(btn) {
            try {
                const s = JSON.parse(btn.dataset.sizes || 'null');
                return s && typeof s === 'object' ? s : DEFAULT_SIZES;
            } catch (e) {
                return DEFAULT_SIZES;
            }
        }

        function renderSizes(sizes) {
            const box = $('pmSizes');
            box.innerHTML = '';
            ['S', 'M', 'L'].filter(s => s in sizes).forEach(s => {
                const id = 'pmSize' + s;
                box.insertAdjacentHTML('beforeend', `
                    <input type="radio" class="btn-check" name="pmSize" id="${id}" value="${s}" ${s === 'M' ? 'checked' : ''}>
                    <label class="btn btn-outline-primary" for="${id}">
                        ${s}${sizes[s] > 0 ? ' <small>(+' + formatVnd(sizes[s]) + ')</small>' : ''}
                    </label>`);
            });
            if (!box.querySelector('input:checked')) {
                const first = box.querySelector('input');
                if (first) first.checked = true;
            }
        }

        function resetOptions() {
            modalEl.querySelectorAll('input[name="pmSugar"][value="100"], input[name="pmIce"][value="100"]')
                .forEach(r => (r.checked = true));
            modalEl.querySelectorAll('.pm-topping').forEach(c => (c.checked = false));
            $('pmQty').value = 1;
        }

        function readSelection() {
            const size = modalEl.querySelector('input[name="pmSize"]:checked')?.value || 'M';
            const sugar = parseInt(modalEl.querySelector('input[name="pmSugar"]:checked')?.value ?? 100, 10);
            const ice = parseInt(modalEl.querySelector('input[name="pmIce"]:checked')?.value ?? 100, 10);
            const toppings = [...modalEl.querySelectorAll('.pm-topping:checked')].map(c => ({
                id: parseInt(c.value, 10),
                name: c.dataset.name,
                price: Number(c.dataset.price)
            }));
            const quantity = clampQty($('pmQty').value);
            const sizeExtra = Number(current.sizes[size] || 0);
            const unitPrice = current.basePrice + sizeExtra + toppings.reduce((s, t) => s + t.price, 0);
            return { size, sizeExtra, sugar, ice, toppings, quantity, unitPrice };
        }

        function refreshTotal() {
            if (!current) return;
            const sel = readSelection();
            $('pmTotal').textContent = formatVnd(sel.unitPrice * sel.quantity);
        }

        // Mở modal khi bấm nút có class js-open-product
        document.addEventListener('click', (e) => {
            const btn = e.target.closest('.js-open-product');
            if (!btn) return;
            current = {
                productId: parseInt(btn.dataset.id, 10),
                name: btn.dataset.name,
                image: btn.dataset.image || NO_IMAGE,
                basePrice: Number(btn.dataset.price),
                branchId: parseInt(btn.dataset.branchId, 10),
                branchName: btn.dataset.branchName || '',
                sizes: readSizes(btn)
            };
            $('pmName').textContent = current.name;
            $('pmBranch').textContent = current.branchName;
            $('pmBasePrice').textContent = formatVnd(current.basePrice);
            $('pmImage').src = current.image;
            $('pmImage').alt = current.name;
            renderSizes(current.sizes);
            resetOptions();
            refreshTotal();
            modal.show();
        });

        modalEl.addEventListener('change', refreshTotal);
        $('pmQty').addEventListener('input', refreshTotal);
        $('pmMinus').addEventListener('click', () => { $('pmQty').value = clampQty($('pmQty').value - 1); refreshTotal(); });
        $('pmPlus').addEventListener('click', () => { $('pmQty').value = clampQty(+$('pmQty').value + 1); refreshTotal(); });

        $('pmAddToCart').addEventListener('click', () => {
            if (!current) return;
            const sel = readSelection();
            Cart.add({
                productId: current.productId,
                name: current.name,
                image: current.image,
                branchId: current.branchId,
                branchName: current.branchName,
                basePrice: current.basePrice,
                ...sel
            });
            modal.hide();
            toast(`Đã thêm ${sel.quantity} × ${current.name} vào giỏ`);
        });
    }

    // ================== Trang giỏ hàng ==================
    function initCartPage() {
        const page = document.getElementById('cart-page');
        if (!page) return;
        const list = document.getElementById('cart-items');
        const empty = document.getElementById('cart-empty');

        function optionText(i) {
            const parts = [`Size ${i.size}`, `${i.sugar}% đường`, `${i.ice}% đá`];
            if (i.toppings.length) parts.push('Topping: ' + i.toppings.map(t => t.name).join(', '));
            return parts.join(' · ');
        }

        function render() {
            const items = Cart.getItems();
            empty.classList.toggle('d-none', items.length > 0);
            page.classList.toggle('d-none', items.length === 0);

            // Nhóm theo shop vì mỗi shop sẽ thành 1 đơn riêng
            const groups = {};
            items.forEach(i => {
                (groups[i.branchId] ||= { name: i.branchName, items: [] }).items.push(i);
            });

            list.innerHTML = Object.values(groups).map(g => `
                <div class="card mb-3">
                    <div class="card-header bg-white fw-semibold"><i class="bi bi-shop me-1"></i>${escapeHtml(g.name)}</div>
                    <ul class="list-group list-group-flush">
                    ${g.items.map(i => `
                        <li class="list-group-item" data-key="${escapeHtml(i.key)}">
                            <div class="d-flex gap-3">
                                <img src="${escapeHtml(i.image || NO_IMAGE)}" alt="" class="rounded object-fit-cover flex-shrink-0"
                                     width="72" height="72">
                                <div class="flex-grow-1">
                                    <div class="d-flex justify-content-between gap-2">
                                        <span class="fw-semibold">${escapeHtml(i.name)}</span>
                                        <button class="btn btn-sm btn-link text-danger p-0 js-remove" title="Xoá">
                                            <i class="bi bi-trash"></i></button>
                                    </div>
                                    <div class="small text-muted">${escapeHtml(optionText(i))}</div>
                                    <div class="d-flex justify-content-between align-items-center mt-2 flex-wrap gap-2">
                                        <div class="input-group input-group-sm" style="width: 120px">
                                            <button class="btn btn-outline-secondary js-minus" type="button">−</button>
                                            <input type="number" class="form-control text-center js-qty"
                                                   value="${i.quantity}" min="1" max="${MAX_QTY}">
                                            <button class="btn btn-outline-secondary js-plus" type="button">+</button>
                                        </div>
                                        <div class="text-end">
                                            <div class="small text-muted">${formatVnd(i.unitPrice)} / ly</div>
                                            <div class="fw-bold text-danger">${formatVnd(i.unitPrice * i.quantity)}</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </li>`).join('')}
                    </ul>
                </div>`).join('');

            document.getElementById('cart-total-qty').textContent = Cart.totalQty();
            document.getElementById('cart-subtotal').textContent = formatVnd(Cart.subtotal());
        }

        list.addEventListener('click', (e) => {
            const li = e.target.closest('li[data-key]');
            if (!li) return;
            const key = li.dataset.key;
            const qtyInput = li.querySelector('.js-qty');
            if (e.target.closest('.js-remove')) {
                if (confirm('Xoá món này khỏi giỏ?')) Cart.remove(key);
            } else if (e.target.closest('.js-minus')) {
                Cart.updateQty(key, +qtyInput.value - 1);
            } else if (e.target.closest('.js-plus')) {
                Cart.updateQty(key, +qtyInput.value + 1);
            }
        });
        list.addEventListener('change', (e) => {
            if (!e.target.classList.contains('js-qty')) return;
            Cart.updateQty(e.target.closest('li[data-key]').dataset.key, e.target.value);
        });

        document.getElementById('btn-clear-cart')?.addEventListener('click', () => {
            if (confirm('Xoá toàn bộ giỏ hàng?')) Cart.clear();
        });

        document.addEventListener('cart:changed', render);
        window.addEventListener('storage', (e) => { if (e.key === STORAGE_KEY) render(); });
        render();
    }

    // ================== Khởi động ==================
    document.addEventListener('DOMContentLoaded', () => {
        Cart.updateBadge();
        initProductModal();
        initCartPage();
    });
})();