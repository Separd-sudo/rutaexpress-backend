package com.rutaexpress.shipments;

import com.rutaexpress.shipments.entity.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentStatusTest {

    @Test
    @DisplayName("Regla Clave: No se puede pasar a EN_RUTA sin haber sido ACEPTADO previamente")
    void shouldRejectEnRutaIfNotAccepted() {
        // Desde EN_BODEGA con wasAccepted = false
        boolean canGoToEnRuta = ShipmentStatus.EN_BODEGA.canTransitionTo(ShipmentStatus.EN_RUTA, false);
        assertFalse(canGoToEnRuta, "No debe permitir transicionar a EN_RUTA si wasAccepted es falso");

        // Desde EN_BODEGA con wasAccepted = true
        boolean canGoToEnRutaAccepted = ShipmentStatus.EN_BODEGA.canTransitionTo(ShipmentStatus.EN_RUTA, true);
        assertTrue(canGoToEnRutaAccepted, "Debe permitir transicionar a EN_RUTA si fue aceptado");
    }

    @Test
    @DisplayName("Debe permitir flujo secuencial regular CREADO -> ACEPTADO -> EN_BODEGA -> EN_RUTA -> ENTREGADO")
    void shouldAllowHappyPath() {
        assertTrue(ShipmentStatus.CREADO.canTransitionTo(ShipmentStatus.ACEPTADO, false));
        assertTrue(ShipmentStatus.ACEPTADO.canTransitionTo(ShipmentStatus.EN_BODEGA, true));
        assertTrue(ShipmentStatus.EN_BODEGA.canTransitionTo(ShipmentStatus.EN_RUTA, true));
        assertTrue(ShipmentStatus.EN_RUTA.canTransitionTo(ShipmentStatus.ENTREGADO, true));
    }

    @Test
    @DisplayName("No debe permitir saltos no autorizados directos de CREADO a EN_RUTA")
    void shouldRejectDirectJumpFromCreadoToEnRuta() {
        assertFalse(ShipmentStatus.CREADO.canTransitionTo(ShipmentStatus.EN_RUTA, false));
    }

    @Test
    @DisplayName("Debe permitir cancelar antes de estar ENTREGADO")
    void shouldAllowCancelBeforeDelivered() {
        assertTrue(ShipmentStatus.CREADO.canTransitionTo(ShipmentStatus.CANCELADO, false));
        assertTrue(ShipmentStatus.ACEPTADO.canTransitionTo(ShipmentStatus.CANCELADO, true));
        assertTrue(ShipmentStatus.EN_BODEGA.canTransitionTo(ShipmentStatus.CANCELADO, true));
        assertTrue(ShipmentStatus.EN_RUTA.canTransitionTo(ShipmentStatus.CANCELADO, true));
        assertFalse(ShipmentStatus.ENTREGADO.canTransitionTo(ShipmentStatus.CANCELADO, true));
    }
}