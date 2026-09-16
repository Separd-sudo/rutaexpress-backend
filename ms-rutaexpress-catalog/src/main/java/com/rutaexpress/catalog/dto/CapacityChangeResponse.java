package com.rutaexpress.catalog.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapacityChangeResponse {
    private Long serviceId;
    private String serviceCode;
    private Integer previousAvailableCapacity;
    private Integer newAvailableCapacity;
    private Integer maxDailyCapacity;
    private String message;
}
