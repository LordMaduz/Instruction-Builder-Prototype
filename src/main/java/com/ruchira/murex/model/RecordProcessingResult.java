package com.ruchira.murex.model;

import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.model.trade.MurexTrade;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RecordProcessingResult {
    private List<StgMrxExtDmcDto> allStgMrxExtDmcs;
    private List<MurexTrade> allMurexTrades;
}
