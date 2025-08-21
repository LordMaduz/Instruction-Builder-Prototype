-- Insert test data for h_stg_mrx_ext
INSERT INTO h_stg_mrx_ext
(portfolio, counterparty, typology, external_deal_id, value_date, maturity_date, stg_exchange_rate, trans_date, comment_0, currency_1, currency_2, buy_trans_amt, sell_trans_amount)
VALUES
('1', 'D', 'FX Spot', '123456', '2025-08-18', '2025-08-18', 0.1535, '2025-08-18', 'DBS China Ltd', 'USD', 'HKD', 100, 180),
('1', 'D', 'FX Spot', '123456', '2025-08-18', '2025-08-18', 0.1535, '2025-08-18', 'DBS India Ltd', 'USD', 'HKD', 100, 180),
('SG BANK SFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1531, '2025-08-18', 'DBS China Ltd', 'USD', 'HKD', 100, 180),
('SG BANK SFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1532, '2025-08-18', 'DBS China Ltd', 'USD', 'HKD', 100, 180),
('SG BANK SFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1533, '2025-08-18', 'DBS China Ltd', 'USD', 'HKD', 100, 180),
('SG BANK SFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1534, '2025-08-18', 'DBS China Ltd', 'USD', 'HKD', 100, 180),
('SG BANK SFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1531, '2025-08-18', 'DBS India Ltd', 'USD', 'HKD', 100, 180),
('SG BANK SFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1532, '2025-08-18', 'DBS India Ltd', 'USD', 'HKD', 100, 180),
('SG BANK DFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1533, '2025-08-18', 'DBS India Ltd', 'USD', 'HKD', 100, 180),
('SG BANK DFX', 'D', 'FX Swap', '123457', '2025-08-18', '2025-08-18', 0.1534, '2025-08-18', 'DBS India Ltd', 'USD', 'HKD', 100, 180);

-- Insert test data for h_entity
INSERT INTO h_entity
(entity_type, entity_name, entity_id, isActive, murex_comment)
VALUES
('Assoc', 'DBS CHINA Ltd', '1', 1, 'DBS CHINA Pvt Ltd, DBS China Ltd'),
('Subsidiary', 'DBS INDIA Ltd', '2', 1, 'DBS IND Pvt Ltd, DBS India Ltd'),
('Branch', 'DBS USA Ltd', '3', 1, 'DBS USA Pvt Ltd, DBS Usa Ltd');

-- Insert test data for h_apportionment
INSERT INTO h_apportionment (
    business_date, currency, hedge_amount_allocation, entity_name, nav_type
) VALUES
('2025-08-18', 'HKD', 100, 'DBS CHINA Ltd', 'COI'),
('2025-08-18', 'HKD', 100, 'DBS INDIA Ltd', 'COI'),
('2025-08-18', 'HKD', 200, 'DBS USA Ltd', 'RE and Reserves');

-- Insert test data for h_net_asset_value
INSERT INTO h_net_asset_value (business_date, entity_id, nav_type, historical_exchange_rate) VALUES
('2025-08-18', 1, 'COI', 0.15),
('2025-08-18', 1, 'RE and Reserves', 0.15),
('2025-08-18', 2, 'COI', 0.15),
('2025-08-18', 2, 'RE and Reserves', 0.15),
('2025-08-18', 3, 'COI', 0.15),
('2025-08-18', 3, 'RE and Reserves', 0.15);

-- Insert test data for h_business_event_config
INSERT INTO h_business_event_config (
    rule_id, description, business_event, sap_pc_acc_mtd,
    currency_type, nav_type, hedging_instrument, mrx_book_code_ids, status
) VALUES
('R1', 'INC_COH_R_C_SWAP', 'Inception', 'COH', 'Restricted', 'COI', 'FX Swap', '1,2,3', 1),
('R2', 'INC_COH_R_R_SWAP', 'Inception', 'COH', 'Restricted', 'RE', 'FX Swap', '4,2,5', 1),
('R3', 'INC_COH_NR_C_SWAP', 'Inception', 'COH', 'Non Restricted', 'COI', 'FX Swap', '1,2,6', 1),
('R4', 'INC_COH_NR_R_SWAP', 'Inception', 'COH', 'Non Restricted', 'RE', 'FX Swap', '4,2,7', 1);

-- Insert test data for murex_book_config
INSERT INTO murex_book_config (murex_book_code, description, tps_outbound, transformations) VALUES
 ('SPOT_MIRR_INTSC_COIRE', 'SPOT_MIRR_INTSC_COIRE',
 '{
    "outboundProduct": "SPOT",
    "familyGroupType": "CURR_FXF_FXD",
    "portfolio": "SG_POR INTSC",
    "counterParty": "SG BANK LTD",
    "traderId": "SG TRD",
    "sourceSystem": "RUC",
    "comment0": [
      { "table": "entityTable", "fieldName": "murexComment"},
      { "table": "navTable", "fieldName": "navType" }
    ],
    "comment1": [
      { "table": "murexBookCode", "fieldName": "murexBookCode"}
    ]
  }',
 '[{
    "referenceTrade": "FX Spot",
    "referencePortfolio": "SG BNK SFX",
    "referenceTradeBuySell": {"buy": true, "sell": false},
    "flipCurrency": false,
    "exchangeRates": "REF_TRADE_SWAP_PTS",
    "tpsFields": ["valueDate", "maturityDate"],
    "outboundCurrChange": false
  }]'),

  ('SPOT_MIRR_INTSC_COI', 'SPOT_MIRR_INTSC_COI',
 '{
    "outboundProduct": "SPOT",
    "familyGroupType": "CURR_FXF_FXD",
    "portfolio": "SG_POR INTSC",
    "counterParty": "SG BANK LTD",
    "traderId": "SG TRD",
    "sourceSystem": "RUC",
    "comment0": [
      { "table": "entityTable", "fieldName": "murexComment"},
      { "table": "navTable", "fieldName": "navType" }
    ],
    "comment1": [
      { "table": "murexBookCode", "fieldName": "murexBookCode"}
    ]
  }',
 '[{
    "referenceTrade": "FX Spot",
    "referencePortfolio": "SG BNK SFX",
   "referenceTradeBuySell": {"buy": true, "sell": false},
    "flipCurrency": false,
    "exchangeRates": "REF_TRADE_SWAP_PTS",
    "tpsFields": ["valueDate", "maturityDate"],
    "outboundCurrChange": false
  }]'),

 ('SWAP_MIRR_INTSC_COIRE', 'SWAP_MIRR_INTSC_COIRE',
 '{
    "outboundProduct": "SWAP",
    "familyGroupType": "CURR_FXF_FXD",
    "portfolio": "SG_POR INTSC",
    "counterParty": "SG BANK LTD",
    "traderId": "SG TRD",
    "sourceSystem": "RUC",
     { "table": "entityTable", "fieldName": "murexComment"},
     { "table": "navTable", "fieldName": "navType" }
    "comment1": [
      { "table": "murexBookCode", "fieldName": "murexBookCode"}
    ]
  }',
 '[{
    "referenceTrade": "FX Swap",
    "referencePortfolio": "SG BNK SFX",
    "referenceSubTradeBuySell": [{"nearLeg": true ,"buy": true, "sell": true},{"farLegLeg": true ,"buy": true, "sell": true}],
    referenceSubTrade
    "flipCurrency": false,
    "exchangeRates": "REF_TRADE_SWAP_PTS",
    "tpsFields": ["valueDate", "maturityDate"],
    "outboundCurrChange": false
  }]'),
('SWAP_MIRR_INTSC_COI', 'SWAP_MIRR_INTSC_COI',
 '{
    "outboundProduct": "SWAP",
    "familyGroupType": "CURR_FXF_FXD",
    "portfolio": "SG_POR INTSC",
    "counterParty": "SG BANK LTD",
    "traderId": "SG TRD",
    "sourceSystem": "RUC",
     { "table": "entityTable", "fieldName": "murexComment"},
     { "table": "navTable", "fieldName": "navType" }
    "comment1": [
      { "table": "murexBookCode", "fieldName": "murexBookCode"}
    ]
  }',
 '[{
    "referenceTrade": "FX Swap",
    "referencePortfolio": "SG BNK SFX",
    "referenceSubTradeBuySell": [{"nearLeg": true ,"buy": true, "sell": true}],
    "flipCurrency": false,
    "exchangeRates": "REF_TRADE_SWAP_PTS",
    "tpsFields": ["valueDate", "maturityDate"],
    "outboundCurrChange": false
  }]');

CREATE TABLE currency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_currency VARCHAR(10) NOT NULL,
    functional_currency VARCHAR(10) NOT NULL,
    currency_category VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_original_currency (original_currency)
);


INSERT INTO currency (original_currency, functional_currency, currency_category)
VALUES
('HK1', 'HKD', 'Restricted'),
('HKT', 'HKD', 'Restricted'),
('HKD', 'HKD', 'Restricted'),
('CN1', 'CNY', 'Restricted'),
('CNH', 'CNY', 'Restricted'),
('CNY', 'CNY', 'Restricted');