/* ============================================================
   FLOW UI · capa de experiencia compartida
   ------------------------------------------------------------
   Se carga en TODAS las páginas públicas: la home, la ficha de
   producto, el carrito, el seguimiento y también las legales
   (envíos, contacto, términos, privacidad y error), que no usan
   flow-ui.css.

   Hace dos cosas y no toca ni la lógica del carrito ni la del chat:

     1. Barra de progreso superior de 3 px al navegar.
     2. Puente con el API de View Transitions, con respaldo.

   Sin dependencias, sin frameworks y sin una sola imagen.
   ============================================================ */
(function (window, document) {
    'use strict';

    /* ============================================================
       1. BARRA DE PROGRESO
       ------------------------------------------------------------
       El CSS viaja aquí dentro, y no solo en flow-ui.css, porque las
       páginas legales no cargan esa hoja de estilos y también deben
       ver la barra. Va con id propio para que nunca se duplique y no
       dependa de ninguna variable --flow-* (esas páginas tampoco las
       tienen): los colores de marca van literales.
       ============================================================ */
    var BARRA_ID = 'flowProgress';
    var ESTILO_ID = 'flowProgressStyle';
    var CLAVE_NAVEGANDO = 'flow-navegando';

    var CSS_CAPA = [
        '#' + BARRA_ID + '{position:fixed;top:0;left:0;width:100%;height:3px;',
        'z-index:2147483000;pointer-events:none;opacity:0;contain:layout style;',
        'transition:opacity .24s ease;}',
        '#' + BARRA_ID + '.is-on{opacity:1;}',
        '#' + BARRA_ID + ' .flow-progress__bar{position:relative;display:block;',
        'height:100%;width:0;border-radius:0 3px 3px 0;',
        'background:linear-gradient(90deg,#1d4ed8 0%,#2563eb 45%,#60a5fa 100%);',
        'box-shadow:0 0 10px rgba(37,99,235,.95),0 0 18px rgba(252,209,22,.4),',
        '0 0 26px rgba(206,17,38,.3);transition:width .22s ease-out;}',
        /* Punta tricolor: el filo de bandera que deja la marca en el detalle */
        '#' + BARRA_ID + ' .flow-progress__bar::after{content:"";position:absolute;',
        'top:0;right:0;width:26px;height:100%;filter:blur(1px);opacity:.95;',
        'background:linear-gradient(90deg,rgba(252,209,22,0) 0%,#fcd116 35%,',
        '#ce1126 100%);}',
        '@media (prefers-reduced-motion:reduce){#' + BARRA_ID + ',',
        '#' + BARRA_ID + ' .flow-progress__bar{transition:none;}}',
        /* Fundido de respaldo: en las páginas que sí cargan flow-ui.css esta
           misma regla ya existe; aquí va para que las legales se comporten
           igual, porque no cargan esa hoja. */
        'html.flow-leaving body{opacity:.35;transition:opacity 180ms ease-out;}'
    ].join('');

    var barra = null;       // { contenedor, pista }
    var valor = 0;          // porcentaje actual
    var avance = null;      // interval que hace crecer la barra

    function inyectarEstilos() {
        if (document.getElementById(ESTILO_ID)) return;
        var estilo = document.createElement('style');
        estilo.id = ESTILO_ID;
        estilo.appendChild(document.createTextNode(CSS_CAPA));
        (document.head || document.documentElement).appendChild(estilo);
    }

    function crear() {
        if (barra) return barra;

        inyectarEstilos();

        var contenedor = document.createElement('div');
        contenedor.id = BARRA_ID;
        contenedor.setAttribute('aria-hidden', 'true');
        contenedor.innerHTML = '<span class="flow-progress__bar"></span>';

        var destino = document.body || document.documentElement;
        destino.appendChild(contenedor);

        barra = { contenedor: contenedor, pista: contenedor.firstChild };
        return barra;
    }

    function pintar() {
        if (barra) barra.pista.style.width = valor + '%';
    }

    function empezar() {
        crear();
        window.clearInterval(avance);
        window.clearTimeout(barra.espera);

        // Se vuelve a cero en seco y, solo después, se deja la transición:
        // así el crecimiento se anima siempre desde el principio y nunca
        // arrastra el 100% de una navegación anterior.
        barra.contenedor.style.transition = 'opacity .24s ease';
        barra.pista.style.transition = 'none';
        barra.pista.style.width = '0%';
        void barra.pista.offsetWidth;   // fuerza el reflow: fija el 0%
        barra.pista.style.transition = 'width .22s ease-out';

        valor = 9;
        barra.contenedor.classList.add('is-on');
        pintar();

        // Avance asintótico hacia el 92%: se acerca cada vez más despacio,
        // así la barra siempre parece que va a terminar sin llegar a mentir.
        avance = window.setInterval(function () {
            if (valor >= 92) return;
            valor += Math.max(0.7, (92 - valor) * 0.11);
            pintar();
        }, 180);
    }

    /**
     * Cierra la barra al 100% en la página NUEVA (la barra de la página
     * anterior desaparece con la navegación, así que el "terminado" se
     * pinta aquí: es lo que hace que el recorrido se sienta continuo).
     */
    function completar() {
        crear();
        window.clearInterval(avance);

        // Sin transición: el salto a 100% ocurre en el mismo frame en que
        // aparece la página nueva.
        barra.contenedor.style.transition = 'none';
        barra.pista.style.transition = 'none';
        valor = 100;
        barra.contenedor.classList.add('is-on');
        pintar();

        window.requestAnimationFrame(function () {
            window.requestAnimationFrame(function () {
                if (!barra) return;
                barra.contenedor.style.transition = 'opacity .26s ease';
                barra.contenedor.classList.remove('is-on');
            });
        });

        // Se deja en cero (invisible) para la siguiente navegación.
        barra.espera = window.setTimeout(function () {
            if (barra) barra.pista.style.width = '0%';
        }, 340);
    }

    /* ---- Memoria entre páginas ----------------------------------
       sessionStorage recuerda que la navegación salió de la propia
       tienda; así la barra solo "cierra" cuando de verdad venimos de
       un enlace interno y no en cada visita directa.
       (En modo privado estricto puede fallar: se ignora y listo.) */
    function recordarSalida() {
        try {
            window.sessionStorage.setItem(CLAVE_NAVEGANDO, '1');
        } catch (e) {
            /* sin almacenamiento: la barra igual funciona al salir */
        }
    }

    function consumirMarca() {
        try {
            if (window.sessionStorage.getItem(CLAVE_NAVEGANDO)) {
                window.sessionStorage.removeItem(CLAVE_NAVEGANDO);
                return true;
            }
        } catch (e) {
            /* nada */
        }
        return false;
    }

    /* ---- Qué clics cuentan como navegación ---------------------- */
    var NO_NAVEGABLE = /\.(pdf|zip|rar|7z|xlsx?|docx?|pptx?|csv|txt|jpg|jpeg|png|gif|webp|avif|svg|mp4|mov|mp3)$/i;

    function esEnlaceInterno(enlace, evento) {
        if (!enlace || !enlace.getAttribute) return false;
        if (enlace.hasAttribute('download')) return false;
        if (enlace.hasAttribute('data-no-progress')) return false;

        var destino = enlace.getAttribute('target');
        if (destino && destino !== '_self') return false;

        var rel = enlace.getAttribute('rel');
        if (rel && rel.indexOf('external') !== -1) return false;

        if (evento) {
            if (evento.defaultPrevented) return false;
            if (evento.button !== 0 || evento.metaKey || evento.ctrlKey
                || evento.shiftKey || evento.altKey) return false;
        }

        var href = enlace.getAttribute('href');
        if (!href || href.charAt(0) === '#') return false;
        if (/^(mailto|tel|javascript|data|blob):/i.test(href)) return false;

        var url;
        try {
            url = new URL(enlace.href, window.location.href);
        } catch (e) {
            return false;
        }

        if (url.origin !== window.location.origin) return false;
        if (NO_NAVEGABLE.test(url.pathname)) return false;

        // Mismo documento (ancla o recarga de la misma URL): no hay página
        // nueva que celebrar.
        if (url.pathname === window.location.pathname
            && url.search === window.location.search) {
            return false;
        }
        return true;
    }

    document.addEventListener('click', function (evento) {
        var enlace = evento.target && evento.target.closest
            ? evento.target.closest('a[href]')
            : null;

        if (!esEnlaceInterno(enlace, evento)) return;

        empezar();
        recordarSalida();
        marcarRespaldo();
    }, false);

    // Búsquedas y formularios que navegan (p. ej. el seguimiento de pedidos)
    document.addEventListener('submit', function (evento) {
        if (evento.defaultPrevented) return;
        var formulario = evento.target;
        if (!formulario || !formulario.getAttribute) return;
        var destino = formulario.getAttribute('target');
        if (destino && destino !== '_self') return;

        empezar();
        recordarSalida();
        marcarRespaldo();
    }, false);

    /* ============================================================
       2. VIEW TRANSITIONS
       ------------------------------------------------------------
       El sitio es multipágina, así que la transición correcta es la
       "cross-document": la activa la regla @view-transition de
       flow-ui.css y el navegador funde la foto de la página vieja con
       la nueva. No hay nada que interceptar: si el navegador no la
       conoce, la regla se ignora y la navegación ocurre igual.

       El respaldo es solo un fundido de salida de 180 ms. Se aplica
       EXCLUSIVAMENTE cuando document.startViewTransition no existe,
       para no sumar dos animaciones donde ya hay una buena.
       ============================================================ */
    function soportaViewTransitions() {
        return typeof document.startViewTransition === 'function';
    }

    function marcarRespaldo() {
        if (soportaViewTransitions()) return;
        document.documentElement.classList.add('flow-leaving');
    }

    function quitarRespaldo() {
        document.documentElement.classList.remove('flow-leaving');
    }

    if (soportaViewTransitions()) {
        // Permite afinar estilos solo en navegadores con el API.
        document.documentElement.classList.add('flow-vt');
    }

    /* ============================================================
       3. ARRANQUE
       ============================================================ */
    var pendienteDeCierre = false;

    function alDomListo() {
        crear();
        pendienteDeCierre = consumirMarca();
        quitarRespaldo();
    }

    function alCargarTodo() {
        if (!pendienteDeCierre) return;
        pendienteDeCierre = false;
        completar();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', alDomListo);
    } else {
        alDomListo();
    }

    if (document.readyState === 'complete') {
        alCargarTodo();
    } else {
        window.addEventListener('load', alCargarTodo);
    }

    // Volver con el botón "atrás" restaura la página desde la caché:
    // hay que limpiar el fundido y dejar la barra lista otra vez.
    window.addEventListener('pageshow', function (evento) {
        if (!evento.persisted) return;
        quitarRespaldo();
        pendienteDeCierre = false;
        try {
            window.sessionStorage.removeItem(CLAVE_NAVEGANDO);
        } catch (e) {
            /* nada */
        }
        if (barra) {
            window.clearInterval(avance);
            valor = 0;
            pintar();
            barra.contenedor.classList.remove('is-on');
        }
    });

    // Para depurar desde la consola o engancharlo a un flujo propio.
    window.FlowUI = {
        progreso: { empezar: empezar, completar: completar },
        soportaViewTransitions: soportaViewTransitions
    };
})(window, document);
