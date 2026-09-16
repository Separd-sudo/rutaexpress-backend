package com.rutaexpress.shipments.entity;

public enum ShipmentStatus {
    CREADO,
    ACEPTADO,
    EN_BODEGA,
    EN_RUTA,
    ENTREGADO,
    CANCELADO;

    public boolean canTransitionTo(ShipmentStatus target, boolean wasAccepted) {
        if (this == CANCELADO || this == ENTREGADO) {
            return false; // Estados terminales
        }
        if (target == CANCELADO) {
            return true; // Se puede cancelar en cualquier momento previo a ENTREGADO
        }

        switch (this) {
            case CREADO:
                return target == ACEPTADO;
            case ACEPTADO:
                return target == EN_BODEGA;
            case EN_BODEGA:
                // Regla clave: No se puede pasar a EN_RUTA sin antes haber sido ACEPTADO
                return target == EN_RUTA && wasAccepted;
            case EN_RUTA:
                return target == ENTREGADO;
            default:
                return false;
        }
    }
}