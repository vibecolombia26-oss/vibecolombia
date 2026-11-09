/* ============================================================
   FLOW CART — carrito compartido (vanilla JS, sin dependencias)
   - localStorage: misma clave y misma forma de item que antes
   - Drawer lateral en escritorio, toast con acciones y modal de salida
   ============================================================ */
(function (window, document) {
    'use strict';

    var STORAGE_KEY = 'carritoFlowColombia';
    var DESKTOP = '(min-width: 960px)';
    var drawer = null;
    var drawerOpener = null;
    var toastWrap = null;
    var exitModal = null;

    /* ------------------------------------------------------------
       Estado del carrito
       ------------------------------------------------------------ */
    function read() {
        try {
            var raw = localStorage.getItem(STORAGE_KEY);
            var data = raw ? JSON.parse(raw) : [];
            return Array.isArray(data) ? data : [];
        } catch (e) {
            console.error('No se pudo leer el carrito:', e);
            return [];
        }
    }

    function write(items) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
        } catch (e) {
            console.error('No se pudo guardar el carrito:', e);
        }
        document.dispatchEvent(new CustomEvent('flowcart:change', { detail: { items: read() } }));
        refresh();
    }

    function normalize(item) {
        item = item || {};
        return {
            sku: item.sku ? String(item.sku) : '',
            nombre: String(item.nombre || 'Producto'),
            precio: Number(item.precio) || 0,
            imagen: String(item.imagen || ''),
            cantidad: Math.max(1, parseInt(item.cantidad, 10) || 1),
            color: String(item.color || ''),
            talla: String(item.talla || ''),
            productoId: (item.productoId === 0 || item.productoId) ? item.productoId : null,
            url: item.url ? String(item.url) : ''
        };
    }

    /**
     * Identidad de la línea del carrito.
     *
     * Antes era `sku || nombre|color|talla`, y eso generaba líneas duplicadas
     * del mismo producto: la portada añade sin SKU (el dato no está en la
     * tarjeta) y la ficha añade con SKU, así que el mismo artículo entraba dos
     * veces y el resumen se leía como "2 unidades".
     *
     * Ahora manda el productoId (que ambas rutas sí conocen) junto con la
     * variante elegida. El SKU queda como respaldo para carritos antiguos.
     */
    function keyOf(item) {
        var productoId = item.productoId;
        if (productoId !== null && productoId !== undefined && productoId !== '') {
            return 'p:' + productoId + '|' + (item.color || '') + '|' + (item.talla || '');
        }
        if (item.sku) {
            return 'sku:' + item.sku;
        }
        return 'n:' + item.nombre + '|' + (item.color || '') + '|' + (item.talla || '');
    }

    /** ¿Ese artículo (misma variante) ya está en el carrito? */
    function has(item) {
        var clave = keyOf(normalize(item));
        return read().some(function (linea) {
            return keyOf(normalize(linea)) === clave;
        });
    }

    function count(items) {
        items = items || read();
        return items.length;
    }

    function subtotal(items) {
        items = items || read();
        return items.reduce(function (acc, item) {
            return acc + (Number(item.precio) || 0) * (Number(item.cantidad) || 1);
        }, 0);
    }

    function formatPrice(value) {
        return '$' + (Number(value) || 0).toLocaleString('es-CO');
    }

    function variantText(item) {
        var partes = [];
        if (item.color) partes.push(item.color);
        if (item.talla) partes.push('Talla ' + item.talla);
        return partes.join(' · ');
    }

    function itemUrl(item) {
        if (item.url) return item.url;
        if (item.productoId !== null && item.productoId !== undefined) {
            return '/producto/' + item.productoId;
        }
        return '';
    }

    /* ------------------------------------------------------------
       Meta Pixel (opcional)
       ------------------------------------------------------------
       Un fallo del píxel NUNCA debe afectar al carrito ni a la compra.
       Si el script de Meta no cargó (bloqueador, CSP, red), fbq no
       existe y simplemente no se envía nada.
       ------------------------------------------------------------ */
    function trackMeta(evento, datos, eventId) {
        try {
            if (typeof window.fbq !== 'function') return;
            if (eventId) {
                window.fbq('track', evento, datos || {}, { eventID: eventId });
            } else {
                window.fbq('track', evento, datos || {});
            }
        } catch (e) {
            console.warn('Meta Pixel: no se pudo registrar ' + evento, e);
        }
    }

    /* ------------------------------------------------------------
       Google Analytics 4 (opcional)
       ------------------------------------------------------------
       Misma filosofía que el píxel de Meta: la analítica jamás puede
       romper la tienda. Si GA4 no cargó —bloqueador de anuncios, CSP,
       red o consentimiento— `window.gtag` no existe y el evento
       simplemente no se envía.

       Por contrato todo va dentro de try/catch: un fallo al medir no
       puede tumbar un "añadir al carrito".
       ------------------------------------------------------------ */
    /**
     * Envía un evento a GA4 si la librería está disponible.
     *
     * @param {string} eventName nombre del evento (view_item, add_to_cart…)
     * @param {object} params    parámetros del evento, en el esquema de GA4
     * @returns {boolean} true si el evento se pudo enviar
     */
    function trackGA4Event(eventName, params) {
        try {
            if (!eventName) return false;
            if (typeof window.gtag !== 'function') return false;
            window.gtag('event', String(eventName), params || {});
            return true;
        } catch (e) {
            console.warn('GA4: no se pudo registrar ' + eventName, e);
            return false;
        }
    }

    /**
     * Convierte una línea del carrito al formato `items[]` de GA4.
     *
     * `item_id` usa el SKU (es el dato que cruza con el catálogo de
     * Merchant Center) y cae al productoId y al nombre si faltara, para
     * que el evento nunca viaje sin identificador.
     */
    function ga4Item(item) {
        item = normalize(item);

        var id = item.sku;
        if (!id) {
            id = (item.productoId !== null && item.productoId !== undefined)
                ? String(item.productoId)
                : item.nombre;
        }

        var datos = {
            item_id: id,
            item_name: item.nombre,
            price: item.precio,
            quantity: item.cantidad
        };

        var variante = [item.color, item.talla].filter(Boolean).join(' · ');
        if (variante) datos.item_variant = variante;

        return datos;
    }

    /* ------------------------------------------------------------
       Operaciones
       ------------------------------------------------------------ */
    /* Ventana anti-doble-disparo. El mismo artículo dos veces en menos de
       600 ms es un doble clic (o un handler duplicado, o `touchend` + `click`),
       no la intención real de comprar 2 unidades. Aquí es donde se cortaba el
       bug "1 se convierte en 2". */
    var DOBLE_DISPARO_MS = 600;
    var ultimoAgregado = { clave: null, cuando: 0 };

    function add(item, options) {
        options = options || {};
        var nuevo = normalize(item);
        var clave = keyOf(nuevo);

        var ahora = Date.now();
        var esDobleDisparo = options.force !== true
            && clave === ultimoAgregado.clave
            && (ahora - ultimoAgregado.cuando) < DOBLE_DISPARO_MS;

        if (esDobleDisparo) {
            return nuevo;
        }
        ultimoAgregado.clave = clave;
        ultimoAgregado.cuando = ahora;

        var items = read();
        var index = -1;

        for (var i = 0; i < items.length; i++) {
            if (keyOf(normalize(items[i])) === clave) { index = i; break; }
        }

        if (index >= 0) {
            items[index] = normalize(items[index]);
            items[index].cantidad += nuevo.cantidad;
            items[index].precio = nuevo.precio || items[index].precio;
            items[index].imagen = nuevo.imagen || items[index].imagen;
            items[index].url = items[index].url || nuevo.url;
            items[index].productoId = items[index].productoId !== null ? items[index].productoId : nuevo.productoId;
        } else {
            items.push(nuevo);
        }

        write(items);

        // Meta Pixel: AddToCart (solo cuando el alta es real; el guard de
        // doble disparo de arriba ya descartó los clics repetidos).
        trackMeta('AddToCart', {
            content_ids: [nuevo.sku || nuevo.nombre],
            content_name: nuevo.nombre,
            content_type: 'product',
            value: (Number(nuevo.precio) || 0) * (Number(nuevo.cantidad) || 1),
            currency: 'COP',
            num_items: Number(nuevo.cantidad) || 1
        });

        // GA4: add_to_cart. Va en su propia llamada (y con sus propios
        // try/catch) a propósito: si Meta falla, GA4 sigue midiendo; y si
        // GA4 no cargó, el píxel ya registró el evento.
        trackGA4Event('add_to_cart', {
            currency: 'COP',
            value: (Number(nuevo.precio) || 0) * (Number(nuevo.cantidad) || 1),
            items: [ga4Item(nuevo)]
        });

        if (options.toast !== false) {
            toast({
                title: options.title || 'Añadido al carrito',
                message: [nuevo.nombre, variantText(nuevo)].filter(Boolean).join(' · '),
                image: nuevo.imagen
            });
        }
        if (options.openDrawer === true) openDrawer();

        return nuevo;
    }

    function removeAt(index) {
        var items = read();
        if (index < 0 || index >= items.length) return;
        items.splice(index, 1);
        write(items);
    }

    function setQty(index, qty) {
        var items = read();
        if (index < 0 || index >= items.length) return;
        items[index] = normalize(items[index]);
        items[index].cantidad = Math.max(1, parseInt(qty, 10) || 1);
        write(items);
    }

    function clear() {
        write([]);
    }

    /* ------------------------------------------------------------
       Refresco de contadores y barra flotante
       ------------------------------------------------------------ */
    function syncBarHeight() {
        var bar = document.getElementById('cartFloat');
        if (!bar) return;
        var visible = bar.classList.contains('show');
        var alto = visible ? Math.ceil(bar.getBoundingClientRect().height) : 0;
        document.documentElement.style.setProperty('--cart-bar-h', alto + 'px');
        document.body.classList.toggle('has-cart-bar', alto > 0);
    }

    function refresh() {
        var items = read();
        var total = count(items);

        document.querySelectorAll('[data-flow-cart-count], #cartCount').forEach(function (el) {
            el.textContent = total;
        });

        var bar = document.getElementById('cartFloat');
        if (bar) {
            var countEl = document.getElementById('cartFloatCount');
            var labelEl = document.getElementById('cartFloatCountLabel');
            var totalEl = document.getElementById('cartFloatTotal');

            if (total === 0) {
                bar.classList.remove('show');
            } else {
                if (countEl) countEl.textContent = total;
                if (labelEl) labelEl.textContent = total === 1 ? 'producto' : 'productos';
                if (totalEl) totalEl.textContent = subtotal(items).toLocaleString('es-CO');
                bar.classList.add('show');
            }
            syncBarHeight();
        }
    }

    /* ------------------------------------------------------------
       Toast
       ------------------------------------------------------------ */
    function ensureToastWrap() {
        if (toastWrap) return toastWrap;
        toastWrap = document.createElement('div');
        toastWrap.className = 'flow-toast-wrap';
        toastWrap.setAttribute('aria-live', 'polite');
        document.body.appendChild(toastWrap);
        return toastWrap;
    }

    function toast(options) {
        options = options || {};
        var wrap = ensureToastWrap();
        var el = document.createElement('div');
        el.className = 'flow-toast';
        el.setAttribute('role', 'status');

        var top = document.createElement('div');
        top.className = 'flow-toast__top';

        if (options.image) {
            var img = document.createElement('img');
            img.className = 'flow-toast__img';
            img.src = options.image;
            img.alt = '';
            img.loading = 'lazy';
            top.appendChild(img);
        } else {
            var icon = document.createElement('span');
            icon.className = 'flow-toast__icon';
            icon.textContent = '✓';
            top.appendChild(icon);
        }

        var body = document.createElement('div');
        body.className = 'flow-toast__body';

        var title = document.createElement('strong');
        title.className = 'flow-toast__title';
        title.textContent = options.title || 'Añadido al carrito';
        body.appendChild(title);

        if (options.message) {
            var msg = document.createElement('span');
            msg.className = 'flow-toast__message';
            msg.textContent = options.message;
            body.appendChild(msg);
        }
        top.appendChild(body);

        var close = document.createElement('button');
        close.type = 'button';
        close.className = 'flow-toast__close';
        close.setAttribute('aria-label', 'Cerrar aviso');
        close.textContent = '✕';
        top.appendChild(close);

        var actions = document.createElement('div');
        actions.className = 'flow-toast__actions';

        var stay = document.createElement('button');
        stay.type = 'button';
        stay.className = 'flow-btn flow-btn--ghost flow-btn--sm';
        stay.textContent = 'Seguir comprando';

        var go = document.createElement('button');
        go.type = 'button';
        go.className = 'flow-btn flow-btn--primary flow-btn--sm';
        go.textContent = 'Ir al carrito';

        actions.appendChild(stay);
        actions.appendChild(go);

        el.appendChild(top);
        el.appendChild(actions);
        wrap.appendChild(el);

        var timer = null;

        function hide() {
            if (!el.parentNode) return;
            clearTimeout(timer);
            el.classList.add('is-hiding');
            setTimeout(function () {
                if (el.parentNode) el.parentNode.removeChild(el);
            }, 210);
        }

        close.addEventListener('click', hide);
        stay.addEventListener('click', hide);
        go.addEventListener('click', function () {
            hide();
            goToCart();
        });

        el.addEventListener('mouseenter', function () { clearTimeout(timer); });
        el.addEventListener('mouseleave', function () { timer = setTimeout(hide, 3800); });

        timer = setTimeout(hide, 7000);
        return el;
    }

    /* ------------------------------------------------------------
       Drawer
       ------------------------------------------------------------ */
    function buildDrawer() {
        var root = document.createElement('div');
        root.className = 'flow-drawer';
        root.id = 'flowCartDrawer';
        root.setAttribute('aria-hidden', 'true');

        root.innerHTML =
            '<div class="flow-drawer__overlay" data-drawer-close></div>' +
            '<aside class="flow-drawer__panel" role="dialog" aria-modal="true" aria-label="Carrito de compras">' +
            '  <header class="flow-drawer__head">' +
            '    <h2>Tu carrito <span data-drawer-count></span></h2>' +
            '    <button type="button" class="flow-drawer__close" data-drawer-close aria-label="Cerrar carrito">✕</button>' +
            '  </header>' +
            '  <div class="flow-drawer__items" data-drawer-items></div>' +
            '  <footer class="flow-drawer__foot">' +
            '    <div class="flow-drawer__row"><span>Envío</span><strong>Gratis</strong></div>' +
            '    <div class="flow-drawer__row flow-drawer__row--total"><span>Total</span><strong data-drawer-total>$0</strong></div>' +
            '    <p class="flow-drawer__note">Pago contra entrega en efectivo. Sin costos adicionales.</p>' +
            '    <a class="flow-btn flow-btn--primary flow-btn--block" href="/carrito?checkout=1" data-drawer-checkout>Continuar con la compra</a>' +
            '  </footer>' +
            '</aside>';

        document.body.appendChild(root);

        root.querySelectorAll('[data-drawer-close]').forEach(function (el) {
            el.addEventListener('click', closeDrawer);
        });

        root.querySelector('[data-drawer-items]').addEventListener('click', function (event) {
            var target = event.target;
            var row = target.closest('[data-drawer-item]');
            if (!row) return;
            var index = parseInt(row.getAttribute('data-drawer-item'), 10);

            if (target.closest('[data-qty-plus]')) {
                setQty(index, (Number(read()[index] && read()[index].cantidad) || 1) + 1);
            } else if (target.closest('[data-qty-minus]')) {
                var actual = Number(read()[index] && read()[index].cantidad) || 1;
                if (actual > 1) setQty(index, actual - 1);
            } else if (target.closest('[data-remove]')) {
                removeAt(index);
            }
        });

        root.querySelector('[data-drawer-checkout]').addEventListener('click', function () {
            closeDrawer();
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && root.classList.contains('is-open')) closeDrawer();
        });

        return root;
    }

    function renderDrawer() {
        if (!drawer) return;
        var items = read();
        var list = drawer.querySelector('[data-drawer-items]');
        var totalEl = drawer.querySelector('[data-drawer-total]');
        var countEl = drawer.querySelector('[data-drawer-count]');

        list.innerHTML = '';

        if (!items.length) {
            countEl.textContent = '';
            var empty = document.createElement('div');
            empty.className = 'flow-drawer__empty';
            empty.innerHTML = '<p style="margin:0 0 14px;">Tu carrito está vacío.</p>' +
                '<a class="flow-btn flow-btn--ghost flow-btn--sm" href="/">Explorar productos</a>';
            list.appendChild(empty);
            totalEl.textContent = '$0';
            return;
        }

        countEl.textContent = '(' + items.length + ')';

        items.forEach(function (rawItem, index) {
            var item = normalize(rawItem);
            var row = document.createElement('article');
            row.className = 'flow-line';
            row.setAttribute('data-drawer-item', String(index));

            var img = document.createElement('img');
            img.className = 'flow-line__img';
            img.src = item.imagen || '';
            img.alt = item.nombre;
            img.loading = 'lazy';
            row.appendChild(img);

            var body = document.createElement('div');

            var url = itemUrl(item);
            var name;
            if (url) {
                name = document.createElement('a');
                name.href = url;
                name.className = 'flow-line__name';
            } else {
                name = document.createElement('span');
                name.className = 'flow-line__name';
            }
            name.textContent = item.nombre;
            body.appendChild(name);

            if (variantText(item)) {
                var variant = document.createElement('p');
                variant.className = 'flow-line__variant';
                variant.textContent = variantText(item);
                body.appendChild(variant);
            }

            var meta = document.createElement('div');
            meta.className = 'flow-line__meta';

            var qty = document.createElement('div');
            qty.className = 'flow-qty';
            qty.innerHTML = '<button type="button" data-qty-minus aria-label="Quitar una unidad"' + (item.cantidad <= 1 ? ' disabled' : '') + '>−</button>' +
                '<span>' + item.cantidad + '</span>' +
                '<button type="button" data-qty-plus aria-label="Añadir una unidad">+</button>';
            meta.appendChild(qty);

            var price = document.createElement('div');
            price.className = 'flow-line__price';
            price.innerHTML = '<strong>' + formatPrice(item.precio) + '</strong> c/u · <span>' +
                formatPrice(item.precio * item.cantidad) + '</span>';
            meta.appendChild(price);
            body.appendChild(meta);

            var actions = document.createElement('div');
            actions.className = 'flow-line__actions';

            if (url) {
                var edit = document.createElement('a');
                edit.className = 'flow-link';
                edit.href = url;
                edit.textContent = 'Editar';
                actions.appendChild(edit);
            } else {
                actions.appendChild(document.createElement('span'));
            }

            var remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'flow-link';
            remove.setAttribute('data-remove', '');
            remove.textContent = 'Eliminar';
            actions.appendChild(remove);

            body.appendChild(actions);
            row.appendChild(body);
            list.appendChild(row);
        });

        totalEl.textContent = formatPrice(subtotal(items));
    }

    function openDrawer() {
        if (!window.matchMedia || !window.matchMedia(DESKTOP).matches) {
            window.location.href = '/carrito';
            return;
        }
        if (!drawer) drawer = buildDrawer();
        drawerOpener = document.activeElement;
        renderDrawer();
        drawer.classList.add('is-open');
        drawer.setAttribute('aria-hidden', 'false');
        document.body.classList.add('flow-lock');
        var closeBtn = drawer.querySelector('.flow-drawer__close');
        if (closeBtn) closeBtn.focus();
    }

    function closeDrawer() {
        if (!drawer) return;
        drawer.classList.remove('is-open');
        drawer.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('flow-lock');
        if (drawerOpener && typeof drawerOpener.focus === 'function') drawerOpener.focus();
    }

    function goToCart() {
        if (window.matchMedia && window.matchMedia(DESKTOP).matches) {
            openDrawer();
        } else {
            window.location.href = '/carrito';
        }
    }

    /* ------------------------------------------------------------
       Exit intent (una sola vez por sesión, solo escritorio)
       ------------------------------------------------------------ */
    function showExitModal() {
        if (!exitModal) {
            exitModal = document.createElement('div');
            exitModal.className = 'flow-modal';
            exitModal.setAttribute('role', 'dialog');
            exitModal.setAttribute('aria-modal', 'true');
            exitModal.setAttribute('aria-label', 'Carrito guardado');
            exitModal.innerHTML =
                '<div class="flow-modal__card">' +
                '  <h2>¿Te vas sin terminar?</h2>' +
                '  <p>Guardamos tu carrito automáticamente, puedes volver cuando quieras y retomar la compra.</p>' +
                '  <div class="flow-modal__actions">' +
                '    <button type="button" class="flow-btn flow-btn--primary" data-exit-cart>Ver mi carrito</button>' +
                '    <button type="button" class="flow-btn flow-btn--ghost" data-exit-close>Seguir explorando</button>' +
                '  </div>' +
                '</div>';
            document.body.appendChild(exitModal);

            exitModal.addEventListener('click', function (event) {
                if (event.target === exitModal || event.target.closest('[data-exit-close]')) {
                    hideExitModal();
                } else if (event.target.closest('[data-exit-cart]')) {
                    hideExitModal();
                    goToCart();
                }
            });
            document.addEventListener('keydown', function (event) {
                if (event.key === 'Escape' && exitModal.classList.contains('is-open')) hideExitModal();
            });
        }
        exitModal.classList.add('is-open');
        document.body.classList.add('flow-lock');
        var first = exitModal.querySelector('[data-exit-cart]');
        if (first) first.focus();
    }

    function hideExitModal() {
        if (!exitModal) return;
        exitModal.classList.remove('is-open');
        // Si el drawer sigue abierto, se mantiene el bloqueo de scroll.
        var drawerAbierto = !!drawer && drawer.classList.contains('is-open');
        document.body.classList.toggle('flow-lock', drawerAbierto);
    }

    function initExitIntent() {
        if (document.body.hasAttribute('data-no-exit-intent')) return;
        if (!window.matchMedia || !window.matchMedia(DESKTOP).matches) return;
        document.documentElement.addEventListener('mouseleave', function (event) {
            if (event.clientY > 0) return;
            if (!read().length) return;
            try {
                if (sessionStorage.getItem('flowExitIntent')) return;
                sessionStorage.setItem('flowExitIntent', '1');
            } catch (e) { /* modo privado: se muestra solo una vez por carga */ }
            showExitModal();
        });
    }

    /* ------------------------------------------------------------
       Inicialización
       ------------------------------------------------------------ */
    function init() {
        refresh();
        initExitIntent();

        document.addEventListener('click', function (event) {
            var trigger = event.target.closest ? event.target.closest('[data-flow-cart-open]') : null;
            if (!trigger) return;
            event.preventDefault();
            goToCart();
        });

        window.addEventListener('storage', function (event) {
            if (event.key === STORAGE_KEY) refresh();
        });

        window.addEventListener('resize', syncBarHeight);

        document.addEventListener('flowcart:change', function () {
            if (drawer && drawer.classList.contains('is-open')) renderDrawer();
        });
    }

    window.FlowCart = {
        read: read,
        add: add,
        has: has,
        removeAt: removeAt,
        setQty: setQty,
        clear: clear,
        count: count,
        subtotal: subtotal,
        formatPrice: formatPrice,
        variantText: variantText,
        itemUrl: itemUrl,
        refresh: refresh,
        openDrawer: openDrawer,
        closeDrawer: closeDrawer,
        goToCart: goToCart,
        toast: toast,
        trackMeta: trackMeta,
        trackGA4Event: trackGA4Event,
        ga4Item: ga4Item
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})(window, document);
