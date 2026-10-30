package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RPLCComprobantesService implements RPLCComprobantesServiceI {
    private final ComprobanteSunatRepository comprobantes;
    private final VentaRepository ventas;
    private final DetalleVentaRepository detalles;
    private final DatosEmpresaRepository empresa;
    public RPLCComprobantesService(ComprobanteSunatRepository comprobantes, VentaRepository ventas,
                               DetalleVentaRepository detalles, DatosEmpresaRepository empresa) {
        this.comprobantes = comprobantes; this.ventas = ventas; this.detalles = detalles; this.empresa = empresa;
    }
    @Transactional
    public Long registrarVenta(Long ventaId) {
        Venta venta = ventas.bloquear(ventaId).orElseThrow();
        ComprobanteSunat c = comprobantes.findByVentaId(ventaId)
                .or(() -> comprobantes.findBySerieAndCorrelativo(venta.getSerie(), venta.getCorrelativo())).orElse(null);
        if (c != null && c.getVentaId() != null) {
            if ("ANULADA".equals(venta.getEstado())) { c.setEstadoSunat("ANULADA"); c.setPdfDocumento(null); }
            return c.getId();
        }
        boolean aceptado = c != null && "ACEPTADO".equals(c.getEstadoSunat());
        DatosEmpresa emisor = datosEmpresa();
        if (c == null) c = new ComprobanteSunat();
        c.setVentaId(ventaId); c.setTipoComprobante(venta.getTipoComprobante()); c.setSerie(venta.getSerie());
        c.setCorrelativo(venta.getCorrelativo()); c.setClienteDocumento(venta.getClienteDocumento());
        c.setClienteNombre(venta.getClienteNombre()); c.setFechaEmision(venta.getFechaEmision());
        c.setTotal(venta.getTotal()); c.setSubtotal(venta.getSubtotal()); c.setIgv(venta.getIgv());
        c.setMetodoPago(venta.getMetodoPago());
        if (!aceptado) {
            c.setEmisorNombre(emisor.getRazonSocial()); c.setEmisorRuc(emisor.getRuc()); c.setEmisorDireccion(emisor.getDireccion());
            c.setEstadoSunat("ANULADA".equals(venta.getEstado()) ? "ANULADA" : "PENDIENTE"); c.setHashCpe(null);
        }
        for (DetalleVenta detalle : detalles.findByVentaId(ventaId)) {
            c.getDetalles().add(ComprobanteDetalle.builder().comprobante(c)
                    .descripcion(detalle.getProducto().getDescripcion()).cantidad(detalle.getCantidad())
                    .precioUnitario(detalle.getPrecioUnitario()).importe(detalle.getSubtotal()).build());
        }
        return comprobantes.saveAndFlush(c).getId();
    }
    @Transactional
    public void sincronizarVentas() {
        for (Venta venta : ventas.findAllByOrderByFechaEmisionDesc()) registrarVenta(venta.getId());
    }
    @Transactional(readOnly = true)
    public ComprobanteDocumento documento(Long id) { return convertir(comprobantes.findById(id).orElseThrow()); }

    @Transactional
    public byte[] generarPdf(Long id) {
        ComprobanteSunat c = comprobantes.findById(id).orElseThrow();
        if (c.getDetalles().isEmpty()) throw new IllegalStateException("Este registro no tiene detalle de venta. No se generara un PDF incompleto.");
        if (c.getPdfDocumento() == null) c.setPdfDocumento(DBQPComprobantePdf.generar(convertir(c)));
        return c.getPdfDocumento().clone();
    }
    @Transactional(readOnly = true)
    public DatosEmpresa datosEmpresa() { return empresa.findById(1L).orElseThrow(); }

    @Transactional
    public void guardarEmpresa(String nombre, String ruc, String direccion) {
        if (nombre == null || nombre.isBlank() || ruc == null || !ruc.matches("\\d{11}"))
            throw new IllegalArgumentException("Ingresa la razon social y un RUC de 11 digitos.");
        DatosEmpresa datos = datosEmpresa(); datos.setRazonSocial(nombre.trim()); datos.setRuc(ruc); datos.setDireccion(direccion.trim());
        empresa.save(datos);
        for (ComprobanteSunat c : comprobantes.findAll()) {
            if (!"ACEPTADO".equals(c.getEstadoSunat())) {
                c.setEmisorNombre(datos.getRazonSocial()); c.setEmisorRuc(ruc); c.setEmisorDireccion(datos.getDireccion());
                c.setPdfDocumento(null);
            }
        }
    }
    private ComprobanteDocumento convertir(ComprobanteSunat c) {
        BigDecimal base = c.getSubtotal() == null ? c.getTotal().divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP) : c.getSubtotal();
        return new ComprobanteDocumento(c.getSerie() + "-" + String.format("%06d", c.getCorrelativo()),
                c.getTipoComprobante(), c.getFechaEmision(), texto(c.getEmisorNombre()), texto(c.getEmisorRuc()),
                texto(c.getEmisorDireccion()), texto(c.getClienteNombre()), texto(c.getClienteDocumento()), texto(c.getMetodoPago()),
                base, c.getIgv() == null ? c.getTotal().subtract(base) : c.getIgv(), c.getTotal(), c.getEstadoSunat(),
                c.getDetalles().stream().map(d -> new ComprobanteDocumento.Linea(d.getCantidad(), d.getDescripcion(), d.getPrecioUnitario(), d.getImporte())).toList());
    }
    private String texto(String text) { return text == null ? "" : text; }
}
