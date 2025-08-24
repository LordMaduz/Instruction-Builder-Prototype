<#-- Input parameters expected in the model:
    businessDate        : string (e.g. '2025-08-20')
    contractList        : list of strings (e.g. ['123456','123457'])
    typologyMx3         : string (e.g. 'FX Swap')
    inputCurrency       : string (e.g. 'HKD')
    USDCurrency         : string (e.g. 'USD')
    tradingPortf        : string (e.g. 'SG BANK SFX')
-->

SELECT
    hstg.txn_id AS txnId,
    hstg.mx_prod_cd AS mxProdCd,
    hstg.deal_udf_pc_code AS dealUdfPcCode,
    hstg.ctpy_relnship AS ctpyRelnship,
    hstg.ctpy AS ctpy,
    hstg.next_rollover_date AS nextRolloverDate,
    hstg.value_dte AS valueDte,
    hstg.maturity_dte AS maturityDte,
    hstg.trans_dte AS transDte,
    hstg.pymt_dte AS pymtDte,
    hstg.actual_int_spread AS actualIntSpread,
    hstg.buy_trans_amt AS buyTransAmt,
    hstg.sell_trans_amt AS sellTransAmt,
    hstg.mtm_trans_amt AS mtmTransAmt,
    hstg.curr_1 AS curr1,
    hstg.curr_2 AS curr2,
    hstg.cpty_loc_ctry AS cptyLocCtry,
    hstg.instrument_code AS instrumentCode,
    hstg.mrx_entity_id AS mrxEntityId,
    hstg.curr_biz_unit AS currBizUnit,
    hstg.next_reprice_dte AS nextRepriceDte,
    hstg.deal_status AS dealStatus,
    hstg.mkt_op_last_dte AS mktOpLastDte,
    hstg.issuer_code AS issuerCode,
    hstg.exch_rate AS exchRate,
    hstg.exch_traded_ind AS exchTradedInd,
    hstg.swap_leg_ind AS swapLegInd,
    hstg.spot_forward AS spotForward,
    hstg.deliverable AS deliverable,
    hstg.issuer AS issuer,
    hstg.sales_person_id AS salesPersonId,
    hstg.trading_portf AS tradingPortf,
    hstg.mtm_curr AS mtmCurr,
    hstg.orig_curr AS origCurr,
    hstg.live_amt AS liveAmt,
    hstg.cancel_reissue_ind AS cancelReissueInd,
    hstg.delta AS delta,
    hstg.market_value AS marketValue,
    hstg.bs_indicator AS bsIndicator,
    hstg.dirty_price AS dirtyPrice,
    hstg.discountd_mkt_val AS discountdMktVal,
    hstg.discountd_mkt_val_ccy AS discountdMktValCcy,
    hstg.pv_on_cg AS pvOnCg,
    hstg.ext_ref AS extRef,
    hstg.nondisc_mv AS nondiscMv,
    hstg.pv_effect AS pvEffect,
    hstg.last_calc_dte AS lastCalcDte,
    hstg.nondisc_mv_d AS nondiscMvD,
    hstg.live_qty AS liveQty,
    hstg.past_cash_cap AS pastCashCap,
    hstg.upl AS upl,
    hstg.realize_pl_fut AS realizePlFut,
    hstg.fwsw_points AS fwswPoints,
    hstg.nom_l1_orig AS nomL1Orig,
    hstg.nom_l2_orig AS nomL2Orig,
    hstg.disc_npv_l1 AS discNpvL1,
    hstg.disc_npv_l2 AS discNpvL2,
    hstg.unreal_pl_l1 AS unrealPlL1,
    hstg.unreal_pl_l2 AS unrealPlL2,
    hstg.init_price AS initPrice,
    hstg.pl AS pl,
    hstg.mkt_price AS mktPrice,
    hstg.mv_curr AS mvCurr,
    hstg.fix_date AS fixDate,
    hstg.ntdsg_portf AS ntdsgPortf,
    hstg.comment0 AS comment0,
    hstg.spot_rate AS spotRate,
    hstg.comment1 AS comment1,
    hstg.comment2 AS comment2,
    hstg.unre_cap_gain AS unreCapGain,
    hstg.live_amt2 AS liveAmt2,
    hstg.initial_qty AS initialQty,
    hstg.deal_time AS dealTime,
    hstg.market_spot_rate1 AS marketSpotRate1,
    hstg.market_spot_rate2 AS marketSpotRate2,
    hstg.contract AS contract,
    hstg.typology_mx3 AS typologyMx3,
    hstg.deal_no AS dealNo,
    hstg.orig_contract_ref AS origContractRef,
    hstg.legal_bu AS legalBu,
    hstg.sales_margin_usd AS salesMarginUsd,
    hstg.sales_margin_curr_usd AS salesMarginCurrUsd,
    hstg.source_data_loc_cd AS sourceDataLocCd,
    hstg.product_code AS productCode,
    hstg.dl_businessdate AS dlBusinessdate,

    hn.historical_exchange_rate as historicalExchangeRate,

    ha.nav_type as navType,
    ha.exposure_currency as exposureCurrency,
    ha.hedging_state as apportionmentCurrency,
    ha.hedge_amt_allocation as hedgeAmtAllocation,
    ha.entity_name as entityName,

    he.entity_type as entityType,
    he.entity_id as entityId,
    he.murex_comment as murexComment
FROM h_apportionment ha
JOIN h_entity he
    ON LOWER(ha.entity_id) = LOWER(he.entity_id)
JOIN h_net_asset_value hn
    ON hn.entity_id = he.entity_id
   AND hn.nav_type  = ha.nav_type
   AND hn.business_date = '${businessDate}'
JOIN stg_mrx_ext hstg
    ON hstg.contract IN (
        <#list contractList as c>
            '${c}'<#if c_has_next>,</#if>
        </#list>
    )
   AND hstg.dl_businessdate = '${businessDate}'
   AND FIND_IN_SET(
        LOWER(REPLACE(hstg.comment0, ' ', '')) COLLATE utf8mb4_unicode_ci,
        LOWER(REPLACE(he.murex_comment, ' ', '')) COLLATE utf8mb4_unicode_ci
    ) > 0
WHERE ha.exposure_currency = '${inputCurrency}'
  AND ha.instruction_date = '${businessDate}'
  AND ha.trace_id = (
                SELECT MAX(hap.trace_id)
                FROM h_apportionment hap
                WHERE hap.exposure_currency = ha.exposure_currency
                    AND hap.instruction_date = ha.instruction_date
       )
  AND (
        hstg.typology_mx3 <> '${typologyMx3}'
        OR (
            hstg.typology_mx3 = '${typologyMx3}'
            AND hstg.trading_portf = '${tradingPortf}'
            AND (
                   (hstg.curr_2 = '${inputCurrency}' AND hstg.curr_1 = '${USDCurrency}')
                OR (hstg.curr_1 = '${inputCurrency}' AND hstg.curr_2 = '${USDCurrency}')
            )
        )
  );