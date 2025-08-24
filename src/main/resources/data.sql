
-- Insert test data for h_stg_mrx_ext
INSERT INTO stg_mrx_ext (
    txn_id, mx_prod_cd, deal_udf_pc_code, ctpy_relnship, ctpy, next_rollover_date,
    value_dte, maturity_dte, trans_dte, pymt_dte, actual_int_spread, buy_trans_amt,
    sell_trans_amt, mtm_trans_amt, curr_1, curr_2, cpty_loc_ctry, instrument_code,
    mrx_entity_id, curr_biz_unit, next_reprice_dte, deal_status, mkt_op_last_dte,
    issuer_code, exch_rate, exch_traded_ind, swap_leg_ind, spot_forward, deliverable,
    issuer, sales_person_id, trading_portf, mtm_curr, orig_curr, live_amt,
    cancel_reissue_ind, delta, market_value, bs_indicator, dirty_price,
    discountd_mkt_val, discountd_mkt_val_ccy, pv_on_cg, ext_ref, nondisc_mv, pv_effect,
    last_calc_dte, nondisc_mv_d, live_qty, past_cash_cap, upl, realize_pl_fut, fwsw_points,
    nom_l1_orig, nom_l2_orig, disc_npv_l1, disc_npv_l2, unreal_pl_l1, unreal_pl_l2,
    init_price, pl, mkt_price, mv_curr, fix_date, ntdsg_portf, comment0, spot_rate,
    comment1, comment2, unre_cap_gain, live_amt2, initial_qty, deal_time, market_spot_rate1,
    market_spot_rate2, contract, typology_mx3, deal_no, orig_contract_ref, legal_bu,
    sales_margin_usd, sales_margin_curr_usd, source_data_loc_cd, product_code, dl_businessdate
) VALUES (
    'TXN001', 'MX001', 'PC001', 'REL001', 'Counterparty A', '2025-12-31',
    '2025-08-18', '2026-12-01', '2025-12-01', '2025-12-05', 0.05, 100000.00,
    95000.00, 5000.00, 'USD', 'HKD', 'CN', 'INST001',
    'ENT001', 'BU001', '2025-12-15', 'ACTIVE', '2025-12-01',
    'ISS001', 7.85, 'Y', 'N', 'F', 'Y',
    'Issuer A', 'SP001', 'SG BANK SFX', 'USD', 'USD', 100000.00,
    'N', 0.01, 1000.00, 'B', 99.95,
    1000.00, 'USD', 100.00, 'EXT001', 5000.00, 50.00,
    '2025-12-01', 4500.00, 1000.00, 5000.00, 200.00, 100.00, 0.05,
    1000.00, 2000.00, 500.00, 400.00, 50.00, 60.00,
    99.95, 1500.00, 100.50, 'USD', '2025-12-01', 'NTPF001', 'DBS India Ltd', 7.85,
    NULL, NULL, 500.00, 100000.00, 1000.00, '2025-12-01 12:00:00', 7.80,
    7.90, '123456', 'FX NDF', 'DEAL001', 'OCR001', 'LEGALBU001',
    500.00, 500.00, 'LOC001', 'PROD001', '2025-08-18'
);

-- Insert test data for h_entity
INSERT INTO h_entity
(entity_type, entity_name, entity_id, isActive, murex_comment)
VALUES
('Assoc', 'DBS CHINA Ltd', '1', 1, 'DBS CHINA Pvt Ltd, DBS China Ltd'),
('Subsidiary', 'DBS INDIA Ltd', '2', 1, 'DBS IND Pvt Ltd, DBS India Ltd'),
('Branch', 'DBS USA Ltd', '3', 1, 'DBS USA Pvt Ltd, DBS Usa Ltd');

-- Insert test data for h_net_asset_value
INSERT INTO h_net_asset_value (business_date, entity_id, nav_type, historical_exchange_rate) VALUES
('2025-08-18', 1, 'COI', 0.15),
('2025-08-18', 1, 'RE and Reserves', 0.15),
('2025-08-18', 2, 'COI', 0.15),
('2025-08-18', 2, 'RE and Reserves', 0.15),
('2025-08-18', 3, 'COI', 0.15),
('2025-08-18', 3, 'RE and Reserves', 0.15);
