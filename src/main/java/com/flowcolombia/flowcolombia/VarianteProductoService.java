package com.flowcolombia.flowcolombia.service;

import com.flowcolombia.flowcolombia.VarianteProducto;
import com.flowcolombia.flowcolombia.VarianteProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio para operaciones unitarias sobre variantes de producto.
 * <p>
 * NOTA: El guardado masivo de variantes (cuando se edita un producto completo)
 * se maneja en {@link ProductoService#guardarProductoConVariantes}.
 * Este servicio se enfoca en operaciones individuales: buscar, validar,
 * actualizar stock, etc.
 */
@Service
public class VarianteProductoService {

    private final VarianteProductoRepository varianteProductoRepository;

    public VarianteProductoService(VarianteProductoRepository varianteProductoRepository) {
        this.varianteProductoRepository = varianteProductoRepository;
    }

    // ============================================================
    // CONSULTAS
    // ============================================================

    /**
     * Obtiene todas las variantes de un producto.
     *
     * @param productoId ID del producto.
     * @return Lista de variantes del producto.
     */
    public List<VarianteProducto> listarPorProducto(Long productoId) {
        return varianteProductoRepository.findByProductoId(productoId);
    }

    /**
     * Obtiene todas las variantes activas de un producto.
     *
     * @param productoId ID del producto.
     * @return Lista de variantes activas.
     */
    public List<VarianteProducto> listarActivasPorProducto(Long productoId) {
        return varianteProductoRepository.findByProductoIdAndActivoTrue(productoId);
    }

    /**
     * Obtiene las variantes con stock disponible de un producto.
     *
     * @param productoId ID del producto.
     * @return Lista de variantes con stock > 0.
     */
    public List<VarianteProducto> listarConStock(Long productoId) {
        return varianteProductoRepository.findByProductoIdAndStockGreaterThan(productoId, 0);
    }

    /**
     * Busca una variante por su SKU (único en toda la tabla).
     *
     * @param sku SKU de la variante.
     * @return Optional con la variante encontrada.
     */
    public Optional<VarianteProducto> obtenerPorSku(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            return Optional.empty();
        }
        return varianteProductoRepository.findBySku(sku.trim());
    }

    /**
     * Busca una variante por su ID.
     *
     * @param id ID de la variante.
     * @return Optional con la variante encontrada.
     */
    public Optional<VarianteProducto> obtenerPorId(Long id) {
        return varianteProductoRepository.findById(id);
    }

    /**
     * Verifica si ya existe una variante con el SKU especificado.
     *
     * @param sku SKU a verificar.
     * @return true si existe, false en caso contrario.
     */
    public boolean existeSku(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            return false;
        }
        return varianteProductoRepository.existsBySku(sku.trim());
    }

    // ============================================================
    // OPERACIONES DE GUARDADO / ACTUALIZACIÓN (UNITARIAS)
    // ============================================================

    /**
     * Guarda una variante individual (crea o actualiza).
     * <p>
     * Realiza validaciones antes de guardar:
     * <ul>
     *   <li>SKU no vacío</li>
     *   <li>SKU único (si es nuevo o se cambia)</li>
     *   <li>Precio > 0</li>
     *   <li>Stock >= 0</li>
     *   <li>Costo >= 0 (si se proporciona)</li>
     *   <li>Peso >= 0 (si se proporciona)</li>
     * </ul>
     *
     * @param variante Variante a guardar.
     * @return Variante guardada.
     * @throws IllegalArgumentException si alguna validación falla.
     */
    @Transactional
    public VarianteProducto guardar(VarianteProducto variante) {
        validarVariante(variante);

        // Verificar unicidad de SKU (si es nueva o se cambió)
        String sku = variante.getSku().trim();
        if (variante.getId() == null) {
            // Es nueva: verificar que el SKU no exista
            if (existeSku(sku)) {
                throw new IllegalArgumentException("Ya existe una variante con el SKU: " + sku);
            }
        } else {
            // Es actualización: verificar que el SKU no esté en uso por otra variante
            Optional<VarianteProducto> existente = varianteProductoRepository.findBySku(sku);
            if (existente.isPresent() && !existente.get().getId().equals(variante.getId())) {
                throw new IllegalArgumentException("El SKU " + sku + " ya está en uso por otra variante.");
            }
        }

        // Si el precio es null, usar el precio del producto base
        if (variante.getPrecio() == null || variante.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio de la variante debe ser mayor a 0.");
        }

        // Asegurar valores por defecto para campos opcionales
        if (variante.getCosto() == null) {
            variante.setCosto(0.0);
        }
        if (variante.getStock() == null) {
            variante.setStock(0);
        }
        if (variante.getActivo() == null) {
            variante.setActivo(true);
        }

        return varianteProductoRepository.save(variante);
    }

    /**
     * Valida los campos de una variante.
     *
     * @param variante Variante a validar.
     * @throws IllegalArgumentException si algún campo no es válido.
     */
    private void validarVariante(VarianteProducto variante) {
        if (variante == null) {
            throw new IllegalArgumentException("La variante no puede ser nula.");
        }
        if (variante.getSku() == null || variante.getSku().trim().isEmpty()) {
            throw new IllegalArgumentException("El SKU de la variante es obligatorio.");
        }
        if (variante.getPrecio() == null || variante.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio de la variante debe ser mayor a 0.");
        }
        if (variante.getStock() == null || variante.getStock() < 0) {
            throw new IllegalArgumentException("El stock de la variante no puede ser negativo.");
        }
        if (variante.getCosto() != null && variante.getCosto() < 0) {
            throw new IllegalArgumentException("El costo de la variante no puede ser negativo.");
        }
        if (variante.getPeso() != null && variante.getPeso() < 0) {
            throw new IllegalArgumentException("El peso de la variante no puede ser negativo.");
        }
    }

    // ============================================================
    // OPERACIONES DE STOCK
    // ============================================================

    /**
     * Actualiza el stock de una variante específica.
     *
     * @param varianteId ID de la variante.
     * @param nuevoStock Nuevo valor de stock (debe ser >= 0).
     * @return Variante actualizada.
     * @throws IllegalArgumentException si el stock es negativo o la variante no existe.
     */
    @Transactional
    public VarianteProducto actualizarStock(Long varianteId, Integer nuevoStock) {
        if (nuevoStock == null || nuevoStock < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }

        VarianteProducto variante = varianteProductoRepository.findById(varianteId)
                .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada con ID: " + varianteId));

        variante.setStock(nuevoStock);
        // Si el stock llega a 0, se puede desactivar automáticamente (opcional)
        if (nuevoStock == 0) {
            // Puedes decidir si quieres desactivar la variante cuando se agote.
            // Por ahora solo actualizamos el stock.
        }
        return varianteProductoRepository.save(variante);
    }

    /**
     * Reduce el stock de una variante en la cantidad especificada.
     *
     * @param sku      SKU de la variante.
     * @param cantidad Cantidad a reducir (debe ser > 0).
     * @return Variante actualizada.
     * @throws IllegalArgumentException si la cantidad es inválida o el stock es insuficiente.
     */
    @Transactional
    public VarianteProducto reducirStock(String sku, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
        }

        VarianteProducto variante = varianteProductoRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada con SKU: " + sku));

        if (variante.getStock() < cantidad) {
            throw new IllegalArgumentException("Stock insuficiente. Disponible: " + variante.getStock());
        }

        variante.reducirStock(cantidad);
        return varianteProductoRepository.save(variante);
    }

    /**
     * Incrementa el stock de una variante en la cantidad especificada.
     *
     * @param sku      SKU de la variante.
     * @param cantidad Cantidad a incrementar (debe ser > 0).
     * @return Variante actualizada.
     */
    @Transactional
    public VarianteProducto incrementarStock(String sku, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
        }

        VarianteProducto variante = varianteProductoRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Variante no encontrada con SKU: " + sku));

        variante.incrementarStock(cantidad);
        return varianteProductoRepository.save(variante);
    }

    // ============================================================
    // OPERACIONES DE ELIMINACIÓN
    // ============================================================

    /**
     * Elimina una variante por su ID.
     *
     * @param id ID de la variante.
     * @throws IllegalArgumentException si la variante no existe.
     */
    @Transactional
    public void eliminar(Long id) {
        if (!varianteProductoRepository.existsById(id)) {
            throw new IllegalArgumentException("Variante no encontrada con ID: " + id);
        }
        varianteProductoRepository.deleteById(id);
    }

    /**
     * Elimina todas las variantes de un producto.
     *
     * @param productoId ID del producto.
     */
    @Transactional
    public void eliminarPorProducto(Long productoId) {
        varianteProductoRepository.deleteByProductoId(productoId);
    }

    // ============================================================
    // OPERACIONES DE VALIDACIÓN Y DISPONIBILIDAD
    // ============================================================

    /**
     * Verifica si una variante está disponible para la venta.
     *
     * @param sku SKU de la variante.
     * @return true si está activa y con stock, false en caso contrario.
     */
    public boolean isDisponible(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            return false;
        }
        return varianteProductoRepository.findBySku(sku)
                .map(VarianteProducto::isDisponible)
                .orElse(false);
    }

    /**
     * Obtiene el stock disponible de una variante.
     *
     * @param sku SKU de la variante.
     * @return Stock disponible o null si no existe la variante.
     */
    public Integer getStock(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            return null;
        }
        return varianteProductoRepository.findBySku(sku)
                .map(VarianteProducto::getStock)
                .orElse(null);
    }
}