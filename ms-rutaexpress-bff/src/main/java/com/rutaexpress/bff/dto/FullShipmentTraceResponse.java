package com.rutaexpress.bff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FullShipmentTraceResponse {
    private Map<String, Object> shipment;
    private Map<String, Object> catalogService;
    private List<Map<String, Object>> timeline;
}