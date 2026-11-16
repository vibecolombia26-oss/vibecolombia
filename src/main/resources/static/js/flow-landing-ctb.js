/* ============================================================
   FLOW LANDING CTB-234 · medición del smoke test
   ------------------------------------------------------------
   Script EXCLUSIVO de /landing/ctb-234. No se ejecuta en ninguna
   otra ruta: la primera comprobación es el pathname.

   Qué hace:
     1. ViewContent + view_item al cargar la landing.
     2. Scroll50 y Scroll90, una vez por sesión (sessionStorage).
     3. InitiateCheckout exacto en el clic del CTA del hero.
     4. Contact al usar el WhatsApp (flotante o del cierre).
     5. Mete el producto en el carrito antes de ir a /carrito?checkout=1;
        sin esto el checkout llegaba vacío.

   Reglas de la casa:
     - Vanilla, IIFE y 'use strict', sin frameworks ni bundler.
     - TODO va en try/catch: un fallo de medición NUNCA rompe la
       landing (mismo contrato que flow-cart.js).
     - La línea del carrito lleva bloquearPurchaseMeta:true para que
       el checkout no dispare el Purchase del píxel (regla S1).
   ============================================================ */
(function (window, document) {
    'use strict';

    /* ------------------------------------------------------------
       Datos del producto (hardcoded: es una landing de UN producto)
       ------------------------------------------------------------ */
    var RUTA = '/landing/ctb-234';
    var SKU = 'CTB-234';
    var NOMBRE = 'Destornillador de Impacto Manual x 13 Piezas';
    var PRECIO = 89900;
    var MONEDA = 'COP';

    /* Claves de sessionStorage: un evento de scroll por sesión y pestaña */
    var CLAVE_SCROLL_50 = 'ctb234_scroll50';
    var CLAVE_SCROLL_90 = 'ctb234_scroll90';

    /* ------------------------------------------------------------
       Puentes de medición (si el píxel o GA4 no cargaron, no pasa nada)
       ------------------------------------------------------------ */
    function trackMeta(evento, datos) {
        try {
            if (!window.FlowCart || typeof window.FlowCart.trackMeta !== 'function') return;
            window.FlowCart.trackMeta(evento, datos);
        } catch (e) {
            /* La medición nunca rompe la landing. */
        }
    }

    function trackGA4(evento, params) {
        try {
            if (!window.FlowCart || typeof window.FlowCart.trackGA4Event !== 'function') return;
            window.FlowCart.trackGA4Event(evento, params);
        } catch (e) {
            /* La medición nunca rompe la landing. */
        }
    }

    /** ¿Ya se disparó este evento en esta sesión? */
    function yaDisparado(clave) {
        try {
            return window.sessionStorage.getItem(clave) === 'true';
        } catch (e) {
            // Modo privado sin storage: se mide igual (una vez por carga).
            return false;
        }
    }

    /** Marca el evento como disparado. Si no hay storage, no se guarda. */
    function marcarDisparado(clave) {
        try {
            window.sessionStorage.setItem(clave, 'true');
        } catch (e) {
            /* Sin storage no se guarda: como mucho, un evento de más. */
        }
    }

    /* ------------------------------------------------------------
       Carrito: el CTA existe para comprar, no solo para medir
       ------------------------------------------------------------ */

    /**
     * ID real del producto en BD (lo inyecta la plantilla por Thymeleaf).
     *
     * <p>Es la pieza que cierra el riesgo del respaldo por nombre: con ID, el
     * pedido viaja con {@code productosIds} y el backend decide por fila, no
     * por el texto del producto.</p>
     *
     * @returns {number|null} ID válido o null si el producto aún no existe
     */
    function idProducto() {
        try {
            var id = window.CTB234_PRODUCTO_ID;
            var numero = Number(id);
            return (isFinite(numero) && numero > 0) ? numero : null;
        } catch (e) {
            return null;
        }
    }

    /**
     * Mete CTB-234 en el carrito con su productoId real.
     *
     * @returns {boolean} true si el carrito quedó con el producto
     */
    function agregarProductoAlCarrito() {
        try {
            if (!window.FlowCart || typeof window.FlowCart.add !== 'function') return false;

            var productoId = idProducto();
            // Sin ID no se agrega nada: un pedido con productosIds vacío
            // obligaría al backend a adivinar por nombre.
            if (!productoId) return false;

            var item = {
                sku: SKU,
                nombre: NOMBRE,
                precio: PRECIO,
                imagen: '',
                cantidad: 1,
                color: '',
                talla: '',
                productoId: productoId,
                url: RUTA,
                excluirMeta: false,
                // Regla S1: este producto es COD y su Purchase está
                // bloqueado; la marca viaja para que el checkout respete
                // la regla también en el navegador.
                bloquearPurchaseMeta: true
            };
            // Si ya está en el carrito no se duplica: el CTA puede pulsarse
            // varias veces (hero, mitad y cierre de la página).
            if (typeof window.FlowCart.has === 'function' && window.FlowCart.has(item)) return true;
            window.FlowCart.add(item, { toast: false });
            return true;
        } catch (e) {
            /* Si el carrito falla, el enlace sigue llevando al checkout. */
            return false;
        }
    }

    /** Aviso cuando el producto todavía no está publicado en la base de datos. */
    function avisarProximamente() {
        try {
            if (window.FlowCart && typeof window.FlowCart.toast === 'function') {
                window.FlowCart.toast({
                    title: 'Próximamente',
                    message: 'Producto próximamente disponible. Escríbenos por WhatsApp.'
                });
            }
        } catch (e) {
            /* Sin toast tampoco pasa nada: el CTA simplemente no navega. */
        }
    }

    /* ------------------------------------------------------------
       Eventos de una vez (ViewContent)
       ------------------------------------------------------------ */
    function dispararViewContent() {
        trackMeta('ViewContent', {
            content_ids: [SKU],
            content_type: 'product',
            value: PRECIO,
            currency: MONEDA
        });

        trackGA4('view_item', {
            currency: MONEDA,
            value: PRECIO,
            items: [{
                item_id: SKU,
                item_name: NOMBRE,
                price: PRECIO,
                quantity: 1
            }]
        });
    }

    /* ------------------------------------------------------------
       Scroll profundo (50 % y 90 % de la página)
       ------------------------------------------------------------ */
    function dispararScroll50() {
        if (yaDisparado(CLAVE_SCROLL_50)) return;
        marcarDisparado(CLAVE_SCROLL_50);

        trackMeta('Scroll50', { value: 50, currency: MONEDA });
        trackGA4('scroll_50', { percent: 50 });
    }

    function dispararScroll90() {
        if (yaDisparado(CLAVE_SCROLL_90)) return;
        marcarDisparado(CLAVE_SCROLL_90);

        trackMeta('Scroll90', { value: 90, currency: MONEDA });
        trackGA4('scroll_90', { percent: 90 });
    }

    /**
     * Recalcula el avance de lectura.
     *
     * <p>Se usa alto recorrible (scrollHeight − viewport) y no
     * {@code scrollHeight * 0.5}: así el 50 % y el 90 % significan lo mismo
     * en un móvil de 640 px y en un monitor de 1440 px, y una página que cabe
     * entera en pantalla no dispara nada.</p>
     */
    function medirScroll() {
        try {
            var recorrible = document.documentElement.scrollHeight - window.innerHeight;
            if (!isFinite(recorrible) || recorrible <= 0) return;

            var avance = (window.pageYOffset || window.scrollY || 0) / recorrible;

            if (avance >= 0.5) dispararScroll50();
            if (avance >= 0.9) {
                dispararScroll90();
                // Al 90 % ya está todo medido: se deja de escuchar.
                window.removeEventListener('scroll', medirScroll);
            }
        } catch (e) {
            /* La medición nunca rompe la landing. */
        }
    }

    /* ------------------------------------------------------------
       Clic en los CTA
       ------------------------------------------------------------ */
    function prepararCtas() {
        var ctas = document.querySelectorAll('[data-ctb-cta]');

        Array.prototype.forEach.call(ctas, function (cta) {
            cta.addEventListener('click', function (event) {
                try {
                    // Sin producto en BD no hay compra posible: se corta la
                    // navegación a un checkout que llegaría vacío.
                    if (!idProducto()) {
                        if (event && typeof event.preventDefault === 'function') event.preventDefault();
                        avisarProximamente();
                        return;
                    }

                    agregarProductoAlCarrito();

                    // InitiateCheckout adrede SOLO en el CTA del hero: marca
                    // el instante exacto de intención. El resto de CTA son
                    // repeticiones del mismo paso y no deben inflar el embudo.
                    if (cta.id === 'ctaHero') {
                        trackMeta('InitiateCheckout', {
                            content_ids: [SKU],
                            content_type: 'product',
                            value: PRECIO,
                            currency: MONEDA,
                            num_items: 1
                        });

                        trackGA4('begin_checkout', {
                            currency: MONEDA,
                            value: PRECIO,
                            items: [{
                                item_id: SKU,
                                item_name: NOMBRE,
                                price: PRECIO,
                                quantity: 1
                            }]
                        });
                    }
                } catch (e) {
                    /* La navegación del enlace sigue intacta. */
                }
            });
        });
    }

    /* ------------------------------------------------------------
       Clic en WhatsApp (fallback flotante y enlace del cierre)
       ------------------------------------------------------------ */
    function prepararWhatsApp() {
        var enlaces = document.querySelectorAll('[data-ctb-wa]');

        Array.prototype.forEach.call(enlaces, function (enlace) {
            enlace.addEventListener('click', function () {
                try {
                    trackMeta('Contact', {
                        method: 'whatsapp',
                        content_ids: [SKU]
                    });

                    trackGA4('contact', {
                        method: 'whatsapp',
                        content_name: SKU
                    });
                } catch (e) {
                    /* El enlace de WhatsApp abre igual. */
                }
            });
        });
    }

    /* ------------------------------------------------------------
       Arranque
       ------------------------------------------------------------ */
    function iniciar() {
        try {
            dispararViewContent();

            prepararCtas();
            prepararWhatsApp();

            window.addEventListener('scroll', medirScroll, { passive: true });
            // Por si la landing ya carga con un scroll restaurado por el
            // navegador (volver atrás con el historial).
            medirScroll();
        } catch (e) {
            /* La landing funciona igual sin medición. */
        }
    }

    // Solo en la landing. Cualquier otra ruta sale sin tocar nada.
    if (window.location.pathname !== RUTA) {
        return;
    }

    // Debug: permite ver desde la consola que el script del lanzamiento cargó.
    window.FlowLandingCtb = { version: '1.0.0' };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})(window, document);
