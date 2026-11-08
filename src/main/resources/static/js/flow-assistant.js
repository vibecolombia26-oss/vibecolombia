/* ============================================================
   FLOW ASISTENTE — guía de compras con IA
   - Botón flotante "F" + panel (ventana en escritorio, drawer en móvil)
   - Llama a /api/asistente; la clave de IA vive solo en el servidor
   - Recomendaciones siempre del catálogo real devuelto por el backend
   ============================================================ */
(function (window, document) {
    'use strict';

    var API_URL = '/api/asistente';
    var GREETING = '¡Hola! Soy Tina de Flow Colombia 🌸. ¿Buscas algo para ti, para tu casa o un regalo especial?';
    var QUICK_OPTIONS = [
        { label: 'Para mí', text: 'Busco algo para mí.' },
        { label: 'Para mi casa', text: 'Busco algo para mi casa.' },
        { label: 'Un regalo', text: 'Busco un regalo especial.' }
    ];
    var MAX_HISTORIAL = 6;
    /** El prompt pide máximo 2 productos: el chat muestra esos 2. */
    var MAX_TARJETAS = 2;

    var fab = null;
    var panel = null;
    var feed = null;
    var form = null;
    var input = null;
    var sendBtn = null;

    var history = [];
    var started = false;
    var busy = false;
    var lastFocus = null;

    function isMobile() {
        // Mismo corte que el CSS: por debajo de 768px el chat es pantalla completa.
        return !!(window.matchMedia && window.matchMedia('(max-width: 767px)').matches);
    }

    /* ------------------------------------------------------------
       TECLADO VIRTUAL (móvil)
       ------------------------------------------------------------
       En iOS y Android, al abrir el teclado, window.innerHeight no
       cambia pero window.visualViewport sí: se encoge la parte
       realmente visible. Sin esto, la barra de escritura queda detrás
       del teclado y la conversación no se puede leer.
       ------------------------------------------------------------ */
    function ajustarAlturaTeclado() {
        if (!panel) return;

        // Escritorio o chat cerrado: se limpian las medidas en línea y
        // manda el CSS. Nunca se toca nada si no es móvil.
        if (!isMobile() || panel.hidden) {
            panel.style.height = '';
            panel.style.maxHeight = '';
            panel.style.top = '';
            panel.style.bottom = '';
            return;
        }

        if (window.visualViewport) {
            var alto = Math.round(window.visualViewport.height);
            var desplazamiento = Math.round(window.visualViewport.offsetTop || 0);

            panel.style.height = alto + 'px';
            panel.style.maxHeight = alto + 'px';
            panel.style.top = desplazamiento + 'px';
            panel.style.bottom = 'auto';
        } else {
            // Navegador antiguo sin visualViewport: queda el CSS con 100dvh.
            panel.style.height = '';
            panel.style.maxHeight = '';
            panel.style.top = '';
            panel.style.bottom = '';
        }

        scrollToEnd();
    }

    /** Escucha los cambios del teclado. En escritorio no se engancha nada. */
    function vigilarTeclado() {
        if (!window.visualViewport) return;

        window.visualViewport.addEventListener('resize', ajustarAlturaTeclado);
        window.visualViewport.addEventListener('scroll', ajustarAlturaTeclado);
        window.addEventListener('orientationchange', ajustarAlturaTeclado);
    }

    function formatPrice(value) {
        return '$' + (Number(value) || 0).toLocaleString('es-CO');
    }

    /* ------------------------------------------------------------
       Construcción del widget (una sola vez)
       ------------------------------------------------------------ */
    function build() {
        if (fab) return;

        fab = document.createElement('button');
        fab.type = 'button';
        fab.className = 'flow-ai-fab';
        fab.setAttribute('aria-label', 'Abrir asistente de compras');
        fab.setAttribute('aria-expanded', 'false');
        fab.innerHTML = '<span aria-hidden="true">F</span>' +
            '<span class="flow-ai-fab__dot" aria-hidden="true"></span>';

        panel = document.createElement('section');
        panel.className = 'flow-ai-panel';
        panel.setAttribute('role', 'dialog');
        panel.setAttribute('aria-label', 'Tina, asesora de Flow Colombia');
        panel.hidden = true;
        panel.innerHTML =
            '<header class="flow-ai-head">' +
            '  <span class="flow-ai-head__mark" aria-hidden="true">S</span>' +
            '  <div class="flow-ai-head__text">' +
            '    <strong>Tina · Flow Colombia</strong>' +
            '    <span class="flow-ai-head__status"><i aria-hidden="true"></i> Asesora de estilo</span>' +
            '  </div>' +
            '  <button type="button" class="flow-ai-close" aria-label="Cerrar asistente">✕</button>' +
            '</header>' +
            '<div class="flow-ai-body" role="log" aria-live="polite"></div>' +
            '<form class="flow-ai-form">' +
            '  <label class="flow-sr-only" for="flowAiInput">Escribe tu mensaje</label>' +
            '  <input class="flow-ai-input" id="flowAiInput" type="text" maxlength="500" ' +
            '         placeholder="Ej: busco algo para mi casa" autocomplete="off">' +
            '  <button type="submit" class="flow-ai-send" aria-label="Enviar mensaje">→</button>' +
            '</form>' +
            '<p class="flow-ai-foot">Tina responde con el catálogo real · envío gratis y pago contra entrega.</p>';

        document.body.appendChild(fab);
        document.body.appendChild(panel);

        feed = panel.querySelector('.flow-ai-body');
        form = panel.querySelector('.flow-ai-form');
        input = panel.querySelector('.flow-ai-input');
        sendBtn = panel.querySelector('.flow-ai-send');

        fab.addEventListener('click', function () {
            if (panel.hidden) open(); else close();
        });

        panel.querySelector('.flow-ai-close').addEventListener('click', close);

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            send(input.value);
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && !panel.hidden) close();
        });

        // Altura real del viewport (teclado virtual en móvil).
        vigilarTeclado();
    }

    /* ------------------------------------------------------------
       Abrir / cerrar
       ------------------------------------------------------------ */
    function open() {
        build();
        lastFocus = document.activeElement;
        panel.hidden = false;
        fab.setAttribute('aria-expanded', 'true');

        if (isMobile()) document.body.classList.add('flow-lock');

        if (!started) {
            greet();
            started = true;
        }

        // Se aplica antes de enfocar: el teclado puede abrirse de inmediato.
        ajustarAlturaTeclado();

        setTimeout(function () { if (input) input.focus(); }, 60);
    }

    function close() {
        if (!panel) return;
        panel.hidden = true;
        fab.setAttribute('aria-expanded', 'false');
        document.body.classList.remove('flow-lock');
        // Se limpian las medidas en línea para no arrastrarlas a la próxima apertura.
        ajustarAlturaTeclado();
        if (lastFocus && typeof lastFocus.focus === 'function') lastFocus.focus();
        else fab.focus();
    }

    /* ------------------------------------------------------------
       Conversación
       ------------------------------------------------------------ */
    function greet() {
        addBubble('bot', GREETING);

        var chips = document.createElement('div');
        chips.className = 'flow-ai-chips';

        QUICK_OPTIONS.forEach(function (option) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'flow-ai-chip';
            button.textContent = option.label;
            button.addEventListener('click', function () { send(option.text); });
            chips.appendChild(button);
        });

        feed.appendChild(chips);
        scrollToEnd();
    }

    function addBubble(role, text) {
        var wrap = document.createElement('div');
        wrap.className = 'flow-ai-msg flow-ai-msg--' + role;

        var bubble = document.createElement('div');
        bubble.className = 'flow-ai-bubble';
        bubble.textContent = text;

        wrap.appendChild(bubble);
        feed.appendChild(wrap);
        scrollToEnd();
        return wrap;
    }

    function addTyping() {
        var wrap = document.createElement('div');
        wrap.className = 'flow-ai-msg flow-ai-msg--bot';

        var bubble = document.createElement('div');
        bubble.className = 'flow-ai-bubble flow-ai-typing';
        bubble.innerHTML = '<i></i><i></i><i></i>';

        wrap.appendChild(bubble);
        feed.appendChild(wrap);
        scrollToEnd();
        return wrap;
    }

    function addProducts(productos) {
        if (!productos || !productos.length) return;

        var box = document.createElement('div');
        box.className = 'flow-ai-products';

        productos.slice(0, MAX_TARJETAS).forEach(function (p) {
            if (!p || (!p.slug && p.id == null)) return;

            // URL canónica con slug: el usuario entra directo al producto, sin
            // pasar por la redirección 301 de la ruta antigua por id.
            // Si un producto todavía no tuviera slug, se usa el id como respaldo.
            var destino = (p.slug && String(p.slug).trim() !== '')
                ? '/producto/' + encodeURIComponent(p.slug)
                : '/producto/' + encodeURIComponent(p.id);

            var card = document.createElement('div');
            card.className = 'flow-ai-product';

            // ---- zona clicable: imagen + nombre + precio ----
            var link = document.createElement('a');
            link.className = 'flow-ai-product__main';
            link.href = destino;

            if (p.imagen) {
                var img = document.createElement('img');
                img.className = 'flow-ai-product__img';
                img.src = p.imagen;
                img.alt = '';
                img.loading = 'lazy';
                img.onerror = function () { this.style.visibility = 'hidden'; };
                link.appendChild(img);
            } else {
                var ph = document.createElement('span');
                ph.className = 'flow-ai-product__ph';
                ph.textContent = 'F';
                ph.setAttribute('aria-hidden', 'true');
                link.appendChild(ph);
            }

            var body = document.createElement('span');
            body.className = 'flow-ai-product__body';

            var name = document.createElement('span');
            name.className = 'flow-ai-product__name';
            name.textContent = p.nombre || 'Producto';

            var price = document.createElement('span');
            price.className = 'flow-ai-product__price';
            price.textContent = formatPrice(p.precio);

            body.appendChild(name);
            body.appendChild(price);
            link.appendChild(body);
            card.appendChild(link);

            // ---- acciones: comprar desde el chat + ver ficha ----
            var acciones = document.createElement('div');
            acciones.className = 'flow-ai-product__actions';

            var agregar = document.createElement('button');
            agregar.type = 'button';
            agregar.className = 'flow-ai-add';
            agregar.textContent = 'Agregar al carrito';
            agregar.addEventListener('click', function (evento) {
                evento.preventDefault();
                agregarDesdeChat(p, agregar);
            });

            var ver = document.createElement('a');
            ver.className = 'flow-ai-product__cta';
            ver.href = destino;
            ver.textContent = 'Ver →';

            acciones.appendChild(agregar);
            acciones.appendChild(ver);
            card.appendChild(acciones);
            box.appendChild(card);
        });

        feed.appendChild(box);
        scrollToEnd();
    }

    /**
     * Compra rápida desde el chat.
     *
     * Reutiliza el carrito compartido (flow-cart.js): misma clave de
     * localStorage, mismo drawer, mismo toast y mismo píxel de Meta. No hay
     * lógica de carrito duplicada.
     */
    function agregarDesdeChat(producto, boton) {
        if (!window.FlowCart || typeof window.FlowCart.add !== 'function') {
            boton.textContent = 'Abre la tienda';
            return;
        }

        try {
            window.FlowCart.add({
                sku: '',
                nombre: producto.nombre || 'Producto',
                precio: Number(producto.precio) || 0,
                imagen: producto.imagen || '',
                cantidad: 1,
                color: '',
                talla: '',
                productoId: producto.id != null ? producto.id : null,
                url: producto.slug ? ('/producto/' + producto.slug) : ''
            }, { title: 'Añadido desde el chat' });
        } catch (e) {
            console.error('Asistente: no se pudo agregar al carrito', e);
            boton.textContent = 'Intenta de nuevo';
            return;
        }

        // Confirmación visual en el propio botón.
        boton.textContent = '✓ Agregado';
        boton.classList.add('is-added');
        setTimeout(function () {
            boton.textContent = 'Agregar al carrito';
            boton.classList.remove('is-added');
        }, 2200);
    }

    /** Lo que el cliente ya tiene en el carrito (solo nombres). */
    function carritoActual() {
        try {
            if (window.FlowCart && typeof window.FlowCart.read === 'function') {
                return window.FlowCart.read()
                    .map(function (item) { return String(item.nombre || ''); })
                    .filter(function (nombre) { return nombre; })
                    .slice(0, 6);
            }
        } catch (e) {
            /* sin carrito disponible */
        }
        return [];
    }

    function scrollToEnd() {
        if (!feed) return;
        window.requestAnimationFrame(function () {
            var ultimo = feed.lastElementChild;
            if (ultimo && typeof ultimo.scrollIntoView === 'function') {
                // Desplazamiento suave hasta el último mensaje, dentro del chat.
                ultimo.scrollIntoView({ behavior: 'smooth', block: 'end' });
            } else {
                feed.scrollTop = feed.scrollHeight;
            }
        });
    }

    /**
     * Botón grande de acción: puente del asistente con Flow Track.
     * El backend lo pide con `accion: "ir_a_seguimiento"` cuando el usuario
     * pregunta por el estado de su pedido.
     */
    function addTrackButton() {
        var wrap = document.createElement('div');
        wrap.className = 'flow-ai-msg flow-ai-msg--bot';

        var link = document.createElement('a');
        link.className = 'flow-ai-track';
        link.href = '/seguimiento';
        link.setAttribute('aria-label', 'Ir a la página de seguimiento de pedidos');
        link.innerHTML = '<span aria-hidden="true">◎</span> Ir a Flow Track';

        wrap.appendChild(link);
        feed.appendChild(wrap);
        scrollToEnd();
    }

    function setBusy(value) {
        busy = value;
        if (input) input.disabled = value;
        if (sendBtn) sendBtn.disabled = value;
        if (input) input.placeholder = value ? 'Tina está escribiendo…' : 'Ej: busco algo para mi casa';
    }

    function send(text) {
        if (busy) return;

        var message = (text || '').trim();
        if (!message) return;

        if (panel.hidden) open();

        var previous = history.slice(-MAX_HISTORIAL);
        addBubble('user', message);
        history.push({ role: 'user', content: message });

        if (input) input.value = '';
        setBusy(true);
        var typing = addTyping();

        fetch(API_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
            // El carrito viaja para que Tina pueda ofrecer un complemento lógico.
            body: JSON.stringify({
                mensaje: message,
                historial: previous,
                carrito: carritoActual()
            })
        })
            .then(function (response) {
                return response.json()
                    .catch(function () { return {}; })
                    .then(function (data) {
                        if (!response.ok) {
                            throw new Error((data && data.error)
                                ? String(data.error)
                                : 'HTTP ' + response.status);
                        }
                        return data;
                    });
            })
            .then(function (data) {
                if (typing.parentNode) typing.remove();

                var respuesta = (data && data.respuesta)
                    ? String(data.respuesta)
                    : 'No pude generar una respuesta en este momento.';

                addBubble('bot', respuesta);
                history.push({ role: 'assistant', content: respuesta });

                if (data && data.accion === 'ir_a_seguimiento') {
                    // El pedido se consulta SOLO en Flow Track (código +
                    // teléfono). Aquí no se muestra ningún dato del pedido.
                    addTrackButton();
                } else if (data && Array.isArray(data.productos) && data.productos.length) {
                    addProducts(data.productos);
                }

                // El historial se mantiene corto: solo se reenvía lo reciente.
                if (history.length > 12) {
                    history = history.slice(-12);
                }
            })
            .catch(function (error) {
                console.error('Asistente: error al consultar', error);
                if (typing.parentNode) typing.remove();

                var mensaje = (error && error.message && error.message.indexOf('HTTP ') !== 0)
                    ? String(error.message)
                    : 'No pude conectar con el asistente. Revisa tu conexión e inténtalo de nuevo.';

                addBubble('bot', mensaje);
            })
            .finally(function () {
                setBusy(false);
                if (input) input.focus();
            });
    }

    /* ------------------------------------------------------------
       Inicio
       ------------------------------------------------------------ */
    function init() {
        build();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})(window, document);
