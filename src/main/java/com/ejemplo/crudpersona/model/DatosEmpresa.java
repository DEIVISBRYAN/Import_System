package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "datos_empresa")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DatosEmpresa {
    @Id private Long id;
    @Column(name = "razon_social") private String razonSocial;
    private String ruc;
    private String direccion;
}
