package com.saha.amit.orderServiceFunctions.model;


import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorInfo {
    private String code;
    private String message;
}