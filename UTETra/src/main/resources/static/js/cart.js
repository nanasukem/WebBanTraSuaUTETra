/**
 * cart.js - Giỏ hàng UTETra (tuần 2: lưu DATABASE qua API /api/cart)
 * - Modal chọn món (fragments/product-modal.html): mở bằng nút có class .js-open-product
 *     data-id, data-name, data-price, data-image
 * - Trang giỏ hàng (cart/index.html)
 * - Badge số ly: phần tử #cart-count hoặc .cart-count
 * Giá trong modal chỉ để xem trước - giá thật do server tính lại từ database.
 */
(function() {
    'use strict';

    const API = '/api/cart';
    const LOGIN_URL = '/auth/login';
    const MAX_QTY = 50;

    // Ảnh mặc định khi món chưa có ảnh (vẽ sẵn bằng SVG, không cần tải từ trang khác)
    const NO_IMAGE = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300">' +
        '<rect width="100%" height="100%" fill="#e8f5ee"/>' +
        '<text x="50%" y="54%" font-family="Arial, sans-serif" font-size="40" font-weight="bold" ' +
        'fill="#198754" text-anchor="middle">UTETra</text></svg>');

    // ================== Tiện ích ==================
    const formatVnd = (n) => Number(n || 0).toLocaleString('vi-VN') + 'đ';

    // Chống XSS: escape chữ trước khi chèn vào HTML
    const escapeHtml = (s) => String(s ?? '')
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');

    const clampQty = (q) => Math.min(MAX_QTY, Math.max(1, parseInt(q, 10) || 1));

    // ================== Gọi API ==================
    /**
     * goLogin = true: chưa đăng nhập thì chuyển sang trang đăng nhập
     * goLogin = false: im lặng (dùng cho badge ở trang khách cũng xem được)
     */
    async function api(method, url, body, { goLogin = true } = {}) {
        const hasBody = body !== undefined;
        const res = await fetch(url, {
            method,
            credentials: 'same-origin',
            headers: hasBody
                ? { 'Content-Type': 'application/json', 'Accept': 'application/json' }
                : { 'Accept': 'application/json' },
            body: hasBody ? JSON.stringify(body) : undefined
        });
        const type = res.headers.get('content-type') || '';

        // Chưa đăng nhập: Spring Security chuyển hướng sang trang login (trả về HTML)
        if (res.status === 401 || res.redirected || (res.ok && !type.includes('application/json'))) {
            if (goLogin) window.location.href = LOGIN_URL;
            throw new Error('Vui lòng đăng nhập để dùng giỏ hàng');
        }

        // Đã đăng nhập nhưng không phải khách hàng (Admin/Manager/Shipper): không được mua hàng
        if (res.status === 403) {
            throw new Error('Tài khoản này không dùng để mua hàng. Vui lòng đăng nhập bằng tài khoản khách hàng.');
        }

        const data = type.includes('application/json') ? await res.json() : null;
        if (!res.ok) throw new Error((data && data.message) || ('Lỗi máy chủ (' + res.status + ')'));
        return data;
    }

    // ================== Giỏ hàng (dữ liệu từ server) ==================
    const Cart = {
        data: { items: [], totalQty: 0, subtotal: 0 },

        set(data) {
            Cart.data = data || { items: [], totalQty: 0, subtotal: 0 };
            Cart.updateBadge();
            document.dispatchEvent(new CustomEvent('cart:changed', { detail: Cart.data }));
        },

        async load(opts) { Cart.set(await api('GET', API, undefined, opts)); return Cart.data; },
        async add(req) { Cart.set(await api('POST', API, req)); },
        async updateQty(id, qty) { Cart.set(await api('PUT', `${API}/${id}`, { quantity: clampQty(qty) })); },
        async remove(id) { Cart.set(await api('DELETE', `${API}/${id}`)); },
        async clear() { Cart.set(await api('DELETE', API)); },

        updateBadge() {
            document.querySelectorAll('#cart-count, .cart-count').forEach(el => {
                el.textContent = Cart.data.totalQty || 0;
            });
        }
    };

    window.UtetraCart = Cart; // trang checkout dùng lại

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

        function renderSizes(sizes) {
            const box = $('pmSizes');
            box.innerHTML = '';
            ['S', 'M', 'L'].filter(s => s in sizes).forEach(s => {
                const id = 'pmSize' + s;
                box.insertAdjacentHTML('beforeend', `
                    <input type="radio" class="btn-check" name="pmSize" id="${id}" value="${s}" ${s === 'S' ? 'checked' : ''}>
                    <label class="btn btn-outline-success" for="${id}">
                        ${s}${sizes[s] > 0 ? ' <small>(+' + formatVnd(sizes[s]) + ')</small>' : ''}
                    </label>`);
            });
            if (!box.querySelector('input:checked')) {
                const first = box.querySelector('input');
                if (first) first.checked = true;
            }
        }

        function renderToppings(toppings, stopped, outHere) {
            const box = $('pmToppings');
            // Topping Admin đã ngừng bán: vẫn hiện nhưng mờ, không chọn được
            const stoppedHtml = (stopped || []).map(t => `
                <div class="form-check d-flex justify-content-between opacity-50">
                    <div>
                        <input class="form-check-input" type="checkbox" disabled>
                        <label class="form-check-label text-decoration-line-through">${escapeHtml(t.name)}</label>
                    </div>
                    <small class="text-danger">Đã ngừng bán</small>
                </div>`).join('') + (outHere || []).map(t => `
                <div class="form-check d-flex justify-content-between opacity-50">
                    <div>
                        <input class="form-check-input" type="checkbox" disabled>
                        <label class="form-check-label">${escapeHtml(t.name)}</label>
                    </div>
                    <small class="text-warning-emphasis">Hết ở chi nhánh</small>
                </div>`).join('');
            if ((!toppings || !toppings.length) && !stoppedHtml && !(outHere || []).length) {
                box.innerHTML = '<div class="text-muted small">Chưa có topping</div>';
                return;
            }
            box.innerHTML = (toppings || []).map(t => `
                <div class="form-check d-flex justify-content-between">
                    <div>
                        <input class="form-check-input pm-topping" type="checkbox" id="top${t.id}" value="${t.id}"
                               data-name="${escapeHtml(t.name)}" data-price="${Number(t.price)}">
                        <label class="form-check-label" for="top${t.id}">${escapeHtml(t.name)}</label>
                    </div>
                    <small class="text-muted">+${formatVnd(t.price)}</small>
                </div>`).join('') + stoppedHtml;
        }

        function resetOptions() {
            modalEl.querySelectorAll('input[name="pmSugar"][value="100"], input[name="pmIce"][value="100"]')
                .forEach(r => (r.checked = true));
            modalEl.querySelectorAll('.pm-topping, .pm-note-tag').forEach(c => (c.checked = false));
            if ($('pmNote')) { $('pmNote').value = ''; $('pmNoteCount').textContent = '0'; }
            $('pmQty').value = 1;
        }

        function readSelection() {
            const size = modalEl.querySelector('input[name="pmSize"]:checked')?.value || 'S'; // mặc định size S (đúng giá trên thẻ món)
            const sugarLevel = parseInt(modalEl.querySelector('input[name="pmSugar"]:checked')?.value ?? 100, 10);
            const iceLevel = parseInt(modalEl.querySelector('input[name="pmIce"]:checked')?.value ?? 100, 10);
            const checked = [...modalEl.querySelectorAll('.pm-topping:checked')];
            const toppingIds = checked.map(c => parseInt(c.value, 10));
            const toppingSum = checked.reduce((s, c) => s + Number(c.dataset.price), 0);
            const quantity = clampQty($('pmQty').value);
            const unitPrice = current.basePrice + Number(current.sizes[size] || 0) + toppingSum; // chỉ để xem trước
            // Ghi chú = các gợi ý đã chọn + phần khách tự gõ, vd "Đá để riêng, Ít sữa · cho thêm đá"
            const tags = [...modalEl.querySelectorAll('.pm-note-tag:checked')].map(c => c.value);
            const extra = ($('pmNote')?.value || '').trim();
            const note = [tags.join(', '), extra].filter(Boolean).join(' · ').slice(0, 200);
            return { size, sugarLevel, iceLevel, toppingIds, quantity, unitPrice, note };
        }

        function refreshTotal() {
            if (!current) return;
            const sel = readSelection();
            $('pmTotal').textContent = formatVnd(sel.unitPrice * sel.quantity);
        }

        // Mở modal cho 1 món. preset != null: SỬA 1 dòng trong giỏ (điền sẵn size, đường, đá, topping, ghi chú, số lượng)
        async function openProduct(info, preset) {
            const opt = await api('GET', `${API}/options/${info.id}`);
            const sizes = {};
            Object.entries(opt.sizes).forEach(([k, v]) => (sizes[k] = Number(v)));
            current = {
                productId: parseInt(info.id, 10),
                name: info.name,
                image: info.image || NO_IMAGE,
                basePrice: Number(info.price),
                sizes,
                toppingItem: !!opt.toppingItem, // topping mua riêng: không chọn size / đường / đá
                editId: preset ? preset.id : null
            };
            $('pmDrinkOptions')?.classList.toggle('d-none', current.toppingItem);
            $('pmToppingNotice')?.classList.toggle('d-none', !current.toppingItem);
            $('pmName').textContent = current.name;
            $('pmBranch').textContent = opt.branchName ? 'Đặt tại ' + opt.branchName : 'Chọn chi nhánh ở bước thanh toán';
            $('pmImage').src = current.image;
            $('pmImage').alt = current.name;
            renderSizes(current.sizes);
            renderToppings(opt.toppings, opt.stoppedToppings, opt.outToppings);
            resetOptions();
            if (preset) {
                // giá gốc = giá 1 ly - size - topping (giỏ chỉ có giá 1 ly)
                const toppingSum = (preset.toppings || []).reduce((s, t) => s + Number(t.price), 0);
                current.basePrice = Number(preset.unitPrice) - Number(sizes[preset.size] || 0) - toppingSum;
                const pick = (sel) => { const el = modalEl.querySelector(sel); if (el) el.checked = true; };
                pick(`input[name="pmSize"][value="${preset.size}"]`);
                pick(`input[name="pmSugar"][value="${preset.sugarLevel}"]`);
                pick(`input[name="pmIce"][value="${preset.iceLevel}"]`);
                (preset.toppings || []).forEach(t => pick(`.pm-topping[value="${t.id}"]`));
                // Ghi chú "gợi ý, gợi ý · phần tự gõ" -> tô lại gợi ý + điền phần còn lại
                let rest = preset.note || '';
                modalEl.querySelectorAll('.pm-note-tag').forEach(c => {
                    if (rest.includes(c.value)) { c.checked = true; rest = rest.replace(c.value, ''); }
                });
                rest = rest.replace(/^[\s,·]+|[\s,·]+$/g, '').replace(/,\s*,/g, ',');
                if ($('pmNote')) { $('pmNote').value = rest; $('pmNoteCount').textContent = rest.length; }
                $('pmQty').value = clampQty(preset.quantity);
            }
            $('pmBasePrice').textContent = formatVnd(current.basePrice);
            $('pmAddToCart').innerHTML = preset
                ? '<i class="bi bi-check-lg"></i> Lưu thay đổi'
                : '<i class="bi bi-cart-plus"></i> Thêm vào giỏ';
            refreshTotal();
            modal.show();
        }
        window.UtetraOpenProduct = openProduct; // trang giỏ hàng dùng để sửa món

        // Bấm nút "+" (class js-open-product) -> tải size/topping từ server -> mở modal
        document.addEventListener('click', async (e) => {
            const btn = e.target.closest('.js-open-product');
            if (!btn) return;
            e.preventDefault();
            btn.disabled = true;
            try {
                await openProduct({ id: btn.dataset.id, name: btn.dataset.name, image: btn.dataset.image,
                    price: btn.dataset.price }, null);
            } catch (err) {
                if (!err.message.startsWith('Vui lòng đăng nhập')) toast(err.message, 'danger');
            } finally {
                btn.disabled = false;
            }
        });

        modalEl.addEventListener('change', refreshTotal);
        $('pmQty').addEventListener('input', refreshTotal);
        $('pmNote')?.addEventListener('input', () => { $('pmNoteCount').textContent = $('pmNote').value.length; });
        $('pmMinus').addEventListener('click', () => { $('pmQty').value = clampQty($('pmQty').value - 1); refreshTotal(); });
        $('pmPlus').addEventListener('click', () => { $('pmQty').value = clampQty(+$('pmQty').value + 1); refreshTotal(); });

        $('pmAddToCart').addEventListener('click', async () => {
            if (!current) return;
            const sel = readSelection();
            const addBtn = $('pmAddToCart');
            addBtn.disabled = true;
            try {
                // Chỉ gửi lựa chọn, KHÔNG gửi giá
                const req = {
                    productId: current.productId,
                    size: sel.size,
                    sugarLevel: sel.sugarLevel,
                    iceLevel: sel.iceLevel,
                    toppingIds: sel.toppingIds,
                    quantity: sel.quantity,
                    note: sel.note || null
                };
                if (current.editId) {
                    // Sửa 1 dòng trong giỏ: đổi size / đường / đá / topping / ghi chú / số lượng
                    Cart.set(await api('PUT', `${API}/${current.editId}/options`, req));
                    modal.hide();
                    toast(`Đã cập nhật ${current.name}`);
                } else {
                    await Cart.add(req);
                    modal.hide();
                    toast(`Đã thêm ${sel.quantity} × ${current.name} vào giỏ`);
                }
            } catch (err) {
                toast(err.message, 'danger');
            } finally {
                addBtn.disabled = false;
            }
        });
    }

    // ================== Trang giỏ hàng ==================
    function initCartPage() {
        const page = document.getElementById('cart-page');
        if (!page) return false;
        const list = document.getElementById('cart-items');
        const empty = document.getElementById('cart-empty');
        const checkoutBtn = document.getElementById('btn-checkout');
        const CONFIRM_REMOVE = 'Bạn muốn xoá món này khỏi giỏ hàng?';

        function optionText(i) {
            if (i.toppingItem) return 'Topping mua riêng' + (i.note ? ' · Ghi chú: ' + i.note : '');
            const parts = [`Size ${i.size}`, `${i.sugarLevel}% đường`, `${i.iceLevel}% đá`];
            if (i.toppings.length) parts.push('Topping: ' + i.toppings.map(t => t.name).join(', '));
            if (i.note) parts.push('Ghi chú: ' + i.note);
            return parts.join(' · ');
        }

        function render() {
            const { items, totalQty, subtotal } = Cart.data;
            empty.classList.toggle('d-none', items.length > 0);
            page.classList.toggle('d-none', items.length === 0);

            list.innerHTML = `
                <div class="card border-0 shadow-sm rounded-4 overflow-hidden">
                    <div class="card-header bg-white fw-bold text-success py-3">
                        <i class="bi bi-cup-straw me-1"></i>Món đã chọn
                    </div>
                    <ul class="list-group list-group-flush">
                    ${items.map(i => `
                        <li class="list-group-item py-3" data-id="${i.id}">
                            <div class="d-flex gap-3">
                                <img src="${escapeHtml(i.image || NO_IMAGE)}" alt="" class="rounded-3 object-fit-cover flex-shrink-0"
                                     width="72" height="72">
                                <div class="flex-grow-1">
                                    <div class="d-flex justify-content-between gap-2">
                                        <button type="button" class="btn btn-link p-0 text-start text-decoration-none text-dark fw-semibold js-edit"
                                                title="Bấm để đổi size, đường, đá, topping">${escapeHtml(i.productName)}
                                            <i class="bi bi-pencil-square text-success small ms-1"></i></button>
                                        <button class="btn btn-sm btn-link text-danger p-0 js-remove" title="Xoá">
                                            <i class="bi bi-trash"></i></button>
                                    </div>
                                    <div class="small text-muted js-edit" role="button" title="Bấm để điều chỉnh">${escapeHtml(optionText(i))}
                                        <span class="text-success ms-1">· Điều chỉnh</span></div>
                                    ${i.stopNote ? `<div class="small text-danger fw-semibold mt-1"><i class="bi bi-slash-circle me-1"></i>${escapeHtml(i.stopNote)}</div>` : ''}
                                    ${i.outNote ? `<div class="small ${i.blocked ? 'text-danger fw-semibold' : 'text-warning-emphasis'} mt-1"><i class="bi bi-exclamation-circle me-1"></i>${escapeHtml(i.outNote)}${i.blocked ? '' : ' · vui lòng điều chỉnh hoặc chọn chi nhánh khác khi thanh toán'}</div>` : ''}
                                    ${i.blocked ? '<div class="small text-danger mt-1">Vui lòng xoá món này khỏi giỏ để thanh toán</div>' : ''}
                                    <div class="d-flex justify-content-between align-items-center mt-2 flex-wrap gap-2">
                                        <div class="input-group input-group-sm" style="width: 120px">
                                            <button class="btn btn-outline-success js-minus" type="button">−</button>
                                            <input type="number" class="form-control text-center js-qty"
                                                   value="${i.quantity}" min="1" max="${MAX_QTY}">
                                            <button class="btn btn-outline-success js-plus" type="button">+</button>
                                        </div>
                                        <div class="text-end">
                                            <div class="small text-muted">${formatVnd(i.unitPrice)} / ${i.toppingItem ? 'phần' : 'ly'}</div>
                                            <div class="fw-bold text-danger">${formatVnd(i.lineTotal)}</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </li>`).join('')}
                    </ul>
                </div>`;

            document.getElementById('cart-total-qty').textContent = totalQty;
            document.getElementById('cart-subtotal').textContent = formatVnd(subtotal);
            // Còn món ngừng bán / hết ở mọi chi nhánh -> phải xoá mới thanh toán được
            const blocked = items.filter(i => i.blocked).length;
            if (checkoutBtn) checkoutBtn.classList.toggle('disabled', items.length === 0 || blocked > 0);
            let note = document.getElementById('cart-blocked-note');
            if (!note && checkoutBtn) {
                checkoutBtn.insertAdjacentHTML('beforebegin', '<div id="cart-blocked-note" class="alert alert-danger small py-2 d-none"></div>');
                note = document.getElementById('cart-blocked-note');
            }
            if (note) {
                note.classList.toggle('d-none', blocked === 0);
                note.innerHTML = `<i class="bi bi-exclamation-triangle me-1"></i>Có ${blocked} món đã ngừng bán / hết hàng. Xoá khỏi giỏ để tiếp tục thanh toán.`;
            }
        }

        // Chạy 1 thao tác; lỗi thì báo và vẽ lại theo dữ liệu cũ
        async function run(action) {
            try { await action(); } catch (err) { toast(err.message, 'danger'); render(); }
        }

        // Bấm nút trong từng dòng: xoá / giảm / tăng
        list.addEventListener('click', (e) => {
            const li = e.target.closest('li[data-id]');
            if (!li) return;
            const id = li.dataset.id;
            const qty = +li.querySelector('.js-qty').value;

            if (e.target.closest('.js-edit')) {
                // Mở modal chọn món với lựa chọn hiện tại để điều chỉnh
                const item = Cart.data.items.find(x => String(x.id) === String(id));
                if (item && window.UtetraOpenProduct) {
                    window.UtetraOpenProduct({ id: item.productId, name: item.productName, image: item.image,
                        price: item.unitPrice }, item).catch(err => toast(err.message, 'danger'));
                }
                return;
            }
            if (e.target.closest('.js-remove')) {
                if (confirm(CONFIRM_REMOVE)) run(() => Cart.remove(id));
            } else if (e.target.closest('.js-minus')) {
                if (qty > 1) {
                    run(() => Cart.updateQty(id, qty - 1));
                } else if (confirm(CONFIRM_REMOVE)) {
                    run(() => Cart.remove(id)); // còn 1 ly mà bấm "−" thì hỏi xoá món
                }
            } else if (e.target.closest('.js-plus')) {
                if (qty < MAX_QTY) run(() => Cart.updateQty(id, qty + 1));
            }
        });

        // Gõ tay số lượng
        list.addEventListener('change', (e) => {
            if (!e.target.classList.contains('js-qty')) return;
            const id = e.target.closest('li[data-id]').dataset.id;
            const qty = parseInt(e.target.value, 10) || 0;
            if (qty < 1) {
                // Gõ 0 (hoặc xoá trống) -> hỏi xoá món; bấm Huỷ thì trả lại số lượng cũ
                if (confirm(CONFIRM_REMOVE)) run(() => Cart.remove(id));
                else render();
                return;
            }
            run(() => Cart.updateQty(id, qty));
        });

        document.getElementById('btn-clear-cart')?.addEventListener('click', () => {
            if (confirm('Xoá toàn bộ giỏ hàng?')) run(() => Cart.clear());
        });

        document.addEventListener('cart:changed', render);
        Cart.load().catch(err => toast(err.message, 'danger')); // trang giỏ bắt buộc đăng nhập
        return true;
    }

    // ================== Khởi động ==================
    document.addEventListener('DOMContentLoaded', () => {
        initProductModal();
        const isCartPage = initCartPage();
        // Trang khác: chỉ cập nhật badge, chưa đăng nhập thì im lặng (không chuyển trang)
        if (!isCartPage && document.querySelector('#cart-count, .cart-count')) {
            Cart.load({ goLogin: false }).catch(() => {});
        }
    });
})();