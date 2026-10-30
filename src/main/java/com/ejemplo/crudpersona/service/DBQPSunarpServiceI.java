package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import java.util.*;

public interface DBQPSunarpServiceI {
    boolean puedeEditar();
    record Moto(Long detalleCompra,String vin,String motor,String marca,String modelo,int anio,String color){}
    record Tramite(String tipo,String documento,String nombre){}
    record Seguimiento(int version,String estado,String titulo,Integer anioTitulo,String oficina,String responsable,String observaciones,boolean revisado,String placa,String partida,String documento,String nombre){}
    List<Map<String,Object>> motos(String buscar);
    List<Map<String,Object>> compras();
    long registrar(Moto moto);
    default long registrarConArchivo(Moto moto, String numeroDuaManual, ERQZArchivoDua archivoDua) {
        return registrar(moto);
    }
    void editar(long id,int version,Moto moto);
    long crearExpediente(long moto,Tramite tramite);
    List<Map<String,Object>> expedientes(long moto);
    Map<String,Object> expediente(long id);
    void actualizar(long id,Seguimiento cambio);
    void adjuntar(long expediente,int version,String categoria,ERQZArchivoDua archivo);
    List<Map<String,Object>> archivos(long expediente);
    ERQZArchivoDua descargar(long archivo);
    List<Map<String,Object>> historial(long moto);
}
