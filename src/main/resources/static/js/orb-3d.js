/* ============================================================
   FLOW ORB 3D · burbuja de cristal con cinta de luz (WebGL puro)
   ------------------------------------------------------------
   Render 3D real del botón del asistente, SIN librerías: un solo
   draw call de un shader que raytraza dentro del canvas.

   Por qué no Three.js: la CSP de producción solo permite scripts
   de 'self' (no se puede tocar SecurityConfig) y este entorno no
   tiene salida TLS para descargar el bundle. Un shader propio da el
   mismo look con ~4 KB, sin CDN, sin bundler y sin cargar 600 KB en
   móviles.

   Qué dibuja:
     - Esfera de vidrio ahumado (base oscura + Fresnel en el filo).
     - Cinta ondulada en el ecuador: naranja/rojo arriba, blanco al
       centro, cian/azul abajo, con onda animada (dos senos).
     - Bloom: halo aditivo fuera del orbe, cálido arriba y frío abajo.
     - Respiración suave del radio (como el CSS anterior).
     - Brillo especular y sombra interior para dar volumen.

   Reglas respetadas:
     - El <button> sigue siendo el botón: el canvas es decorativo,
       `pointer-events: none` y sin capturar clics (touch incluido).
     - Coste bajo: canvas de ~58 px (DPR limitado a 2), sin sombras,
       sin texturas externas, un triángulo a pantalla completa.
     - Se pausa con la pestaña oculta y respeta prefers-reduced-motion.
     - Si no hay WebGL, el CSS del orbe sigue funcionando igual.
   ============================================================ */
(function (window, document) {
    'use strict';

    var VERTEX = [
        'attribute vec2 a_pos;',
        'varying vec2 v_uv;',
        'void main() {',
        '    v_uv = a_pos * 0.5 + 0.5;',
        '    gl_Position = vec4(a_pos, 0.0, 1.0);',
        '}'
    ].join('\n');

    var FRAGMENT = [
        'precision mediump float;',
        'varying vec2 v_uv;',
        'uniform float u_tiempo;',
        'uniform vec3 u_calido;',
        'uniform vec3 u_frio;',
        '',
        'void main() {',
        '    vec2 p = v_uv * 2.0 - 1.0;',
        '    float d = length(p);',
        '',
        '    /* Respiración: el radio del orbe late muy poco */',
        '    float radio = 0.86 + 0.015 * sin(u_tiempo * 1.1);',
        '',
        '    /* Bloom: resplandor fuera del orbe */',
        '    float fuera = max(d - radio, 0.0);',
        '    float halo = exp(-fuera * 10.0) * 0.6;',
        '    vec3 tinte = mix(u_frio, u_calido, clamp(p.y * 0.5 + 0.5, 0.0, 1.0));',
        '',
        '    if (d > radio) {',
        '        gl_FragColor = vec4(tinte * halo, halo * 0.85);',
        '        return;',
        '    }',
        '',
        '    /* Esfera en espacio de vista */',
        '    float z = sqrt(max(radio * radio - d * d, 0.0));',
        '    vec3 n = normalize(vec3(p, z));',
        '    vec3 v = vec3(0.0, 0.0, 1.0);',
        '',
        '    /* Vidrio ahumado: base oscura + Fresnel en el filo */',
        '    float fresnel = pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 3.0);',
        '    vec3 color = vec3(0.02, 0.03, 0.075) + vec3(0.32, 0.55, 0.95) * fresnel * 0.55;',
        '',
        '    /* Coordenadas esféricas para la cinta del ecuador */',
        '    float theta = atan(n.x, n.z);',
        '    float phi = asin(clamp(n.y, -1.0, 1.0));',
        '',
        '    /* Ondulación: dos senos desfasados para que no parezca mecánica */',
        '    float onda = 0.30 * sin(2.2 * theta + u_tiempo * 0.9)',
        '               + 0.07 * sin(5.0 * theta - u_tiempo * 0.6);',
        '',
        '    float desplazamiento = (phi - onda) / 0.30;',
        '    float banda = exp(-desplazamiento * desplazamiento);',
        '',
        '    /* Cálido arriba, blanco al centro, frío abajo */',
        '    vec3 cinta = mix(u_frio, u_calido, smoothstep(-0.5, 0.5, desplazamiento * 0.9));',
        '    cinta = mix(cinta, vec3(1.0), exp(-pow(desplazamiento / 0.55, 2.0)));',
        '',
        '    color += cinta * banda * (0.85 + 0.9 * fresnel);',
        '',
        '    /* Brillo especular del vidrio */',
        '    vec3 luz = normalize(vec3(-0.45, 0.55, 0.8));',
        '    float especular = pow(max(dot(reflect(-luz, n), v), 0.0), 24.0);',
        '    color += vec3(1.0) * especular * 0.32;',
        '',
        '    /* Sombra interior abajo: da volumen al cristal */',
        '    color *= 1.0 - 0.35 * smoothstep(0.0, 1.0, -n.y * 0.8 + 0.2);',
        '',
        '    gl_FragColor = vec4(color, 1.0);',
        '}'
    ].join('\n');

    var ESTILOS = '.flow-ai-fab__canvas{position:absolute;inset:0;width:100%;height:100%;' +
        'display:block;border-radius:50%;pointer-events:none;z-index:1;}' +
        /* Con el 3D activo, los pseudos del orbe CSS se apagan para no duplicar */
        '.flow-ai-fab.is-3d::before,.flow-ai-fab.is-3d::after{display:none;}' +
        '.flow-ai-fab.is-3d{background:#050914;border-color:rgba(255,255,255,.16);' +
        'box-shadow:0 10px 24px -12px rgba(0,0,0,.9),' +
        '0 0 22px -6px rgba(120,195,255,.35),0 0 34px -12px rgba(255,160,90,.3);}';

    var boton = null;
    var canvas = null;
    var gl = null;
    var programa = null;
    var uTiempo = null;
    var rafId = null;
    var inicio = 0;
    var reducido = false;

    function inyectarEstilos() {
        if (document.getElementById('flowOrb3dStyle')) return;
        var estilo = document.createElement('style');
        estilo.id = 'flowOrb3dStyle';
        estilo.textContent = ESTILOS;
        document.head.appendChild(estilo);
    }

    function compilar(tipo, fuente) {
        var shader = gl.createShader(tipo);
        gl.shaderSource(shader, fuente);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
            var error = gl.getShaderInfoLog(shader);
            gl.deleteShader(shader);
            throw new Error('Orb3D: shader inválido: ' + error);
        }
        return shader;
    }

    function crearPrograma() {
        var vs = compilar(gl.VERTEX_SHADER, VERTEX);
        var fs = compilar(gl.FRAGMENT_SHADER, FRAGMENT);
        var prog = gl.createProgram();
        gl.attachShader(prog, vs);
        gl.attachShader(prog, fs);
        gl.linkProgram(prog);
        if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
            throw new Error('Orb3D: no se pudo enlazar: ' + gl.getProgramInfoLog(prog));
        }
        gl.detachShader(prog, vs);
        gl.detachShader(prog, fs);
        gl.deleteShader(vs);
        gl.deleteShader(fs);

        gl.useProgram(prog);

        // Un solo triángulo que cubre la pantalla (sin buffers de geometría)
        var buffer = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
        gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
        var posicion = gl.getAttribLocation(prog, 'a_pos');
        gl.enableVertexAttribArray(posicion);
        gl.vertexAttribPointer(posicion, 2, gl.FLOAT, false, 0, 0);

        return prog;
    }

    /** Ajusta el lienzo al tamaño real del botón (58 px, 52 px en móvil). */
    function dimensionar() {
        var caja = boton.getBoundingClientRect();
        var lado = Math.max(24, Math.round(caja.width || 58));
        var dpr = Math.min(window.devicePixelRatio || 1, 2);
        canvas.width = Math.round(lado * dpr);
        canvas.height = Math.round(lado * dpr);
        gl.viewport(0, 0, canvas.width, canvas.height);
    }

    function pintar(ahora) {
        rafId = window.requestAnimationFrame(pintar);
        if (document.hidden) return;   // pestaña oculta: no se gasta GPU

        gl.uniform1f(uTiempo, (ahora - inicio) / 1000);
        gl.drawArrays(gl.TRIANGLES, 0, 3);
    }

    function desmontar() {
        if (rafId) {
            window.cancelAnimationFrame(rafId);
            rafId = null;
        }
        if (canvas && canvas.parentNode) canvas.parentNode.removeChild(canvas);
        if (boton) boton.classList.remove('is-3d');
        canvas = null;
        gl = null;
    }

    function iniciar() {
        boton = document.querySelector('.flow-ai-fab');
        if (!boton || boton.querySelector('.flow-ai-fab__canvas')) return;

        canvas = document.createElement('canvas');
        canvas.className = 'flow-ai-fab__canvas';
        canvas.setAttribute('aria-hidden', 'true');
        boton.appendChild(canvas);

        var opciones = {
            alpha: true,
            antialias: false,
            depth: false,
            stencil: false,
            premultipliedAlpha: true,
            powerPreference: 'low-power'
        };

        gl = canvas.getContext('webgl', opciones) || canvas.getContext('experimental-webgl', opciones);
        if (!gl) {
            // Sin WebGL: el orbe CSS sigue siendo el protagonista.
            desmontar();
            return;
        }

        try {
            inyectarEstilos();
            programa = crearPrograma();
            uTiempo = gl.getUniformLocation(programa, 'u_tiempo');

            gl.uniform3f(gl.getUniformLocation(programa, 'u_calido'), 1.0, 0.42, 0.12);
            gl.uniform3f(gl.getUniformLocation(programa, 'u_frio'), 0.25, 0.65, 1.0);

            gl.disable(gl.DEPTH_TEST);
            gl.enable(gl.BLEND);
            gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
            gl.clearColor(0, 0, 0, 0);

            dimensionar();
            boton.classList.add('is-3d');

            canvas.addEventListener('webglcontextlost', function (evento) {
                evento.preventDefault();
                desmontar();   // vuelve el orbe CSS, sin errores en consola
            });

            window.addEventListener('resize', dimensionar, { passive: true });

            reducido = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
            inicio = performance.now();

            if (reducido) {
                // Un solo fotograma quieto: se conserva la cinta y el bloom.
                gl.uniform1f(uTiempo, 1.4);
                gl.drawArrays(gl.TRIANGLES, 0, 3);
            } else {
                rafId = window.requestAnimationFrame(pintar);
            }
        } catch (e) {
            // Un fallo del 3D nunca debe romper la tienda ni el botón.
            console.warn('Orb3D:', e && e.message ? e.message : e);
            desmontar();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})(window, document);
