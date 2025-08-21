package com.ruchira.murex.kafka.dto;

import com.ruchira.murex.model.MurexTrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MurexTradeOutBound {
    private MurexTrade murexTrade;
}
