package com.ruchira.murex.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a group of records sharing the same external_deal_id, comment_0, and nav_type
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupedRecord {
    
    private String contract;
    private String comment0;
    private String navType;
    private String typology;
    private List<AggregatedDataResponse> records;
}