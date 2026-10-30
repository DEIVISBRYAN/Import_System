package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "cajas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Caja {
    @Id private Integer numero;
    private String nombre;
}
