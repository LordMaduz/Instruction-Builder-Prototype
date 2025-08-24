package com.ruchira.murex.model;

import lombok.Data;

@Data
public class MurexBookingConfig {

    private Long id;
    private String murexBookCode;
    private String description;
    private String tpsOutbound;
    private String transformations;
}