/* ============================================================
   FLOW ASISTENTE — guía de compras con IA
   - Botón flotante "F" + panel (ventana en escritorio, drawer en móvil)
   - Llama a /api/asistente; la clave de IA vive solo en el servidor
   - Recomendaciones siempre del catálogo real devuelto por el backend
   ============================================================ */
(function (window, document) {
    'use strict';

    var API_URL = '/api/asistente';
    var GREETING = '¡Hola! Soy tu asistente de Flow Colombia. ¿Qué estás buscando hoy? ' +
        'Algo para ti, para tu casa, o para regalar.';
    var QUICK_OPTIONS = [
        { label: 'Algo para mí', text: 'Busco algo para mí.' },
        { label: 'Para mi casa', text: 'Busco algo para mi casa.' },
        { label: 'Para regalar', text: 'Busco un regalo.' }
    ];
    var MAX_HISTORIAL = 6;

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
        return !!(window.matchMedia && window.matchMedia('(max-width: 640px)').matches);
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
        panel.setAttribute('aria-label', 'Asistente de compras de Flow Colombia');
        panel.hidden = true;
        panel.innerHTML =
            '<header class="flow-ai-head">' +
            '  <span class="flow-ai-head__mark" aria-hidden="true">F</span>' +
            '  <div class="flow-ai-head__text">' +
            '    <strong>Asistente Flow</strong>' +
            '    <span class="flow-ai-head__status"><i aria-hidden="true"></i> Guía de compras</span>' +
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
            '<p class="flow-ai-foot">Recomendaciones basadas en el catálogo real de la tienda.</p>';

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

        setTimeout(function () { if (input) input.focus(); }, 60);
    }

    function close() {
        if (!panel) return;
        panel.hidden = true;
        fab.setAttribute('aria-expanded', 'false');
        document.body.classList.remove('flow-lock');
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

        productos.slice(0, 4).forEach(function (p) {
            if (!p || p.id == null) return;

            var link = document.createElement('a');
            link.className = 'flow-ai-product';
            link.href = '/producto/' + encodeURIComponent(p.id);

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

            var body = document.createElement('div');
            body.className = 'flow-ai-product__body';

            var name = document.createElement('span');
            name.className = 'flow-ai-product__name';
            name.textContent = p.nombre || 'Producto';

            var price = document.createElement('span');
            price.className = 'flow-ai-product__price';
            price.textContent = formatPrice(p.precio);

            var cta = document.createElement('span');
            cta.className = 'flow-ai-product__cta';
            cta.textContent = 'Ver producto →';

            body.appendChild(name);
            body.appendChild(price);
            body.appendChild(cta);
            link.appendChild(body);
            box.appendChild(link);
        });

        feed.appendChild(box);
        scrollToEnd();
    }

    function scrollToEnd() {
        if (!feed) return;
        window.requestAnimationFrame(function () {
            feed.scrollTop = feed.scrollHeight;
        });
    }

    function setBusy(value) {
        busy = value;
        if (input) input.disabled = value;
        if (sendBtn) sendBtn.disabled = value;
        if (input) input.placeholder = value ? 'Pensando…' : 'Ej: busco algo para mi casa';
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
            body: JSON.stringify({ mensaje: message, historial: previous })
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

                if (data && Array.isArray(data.productos) && data.productos.length) {
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
