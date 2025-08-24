INSERT INTO h_murex_trade_leg_component (
    trade_leg_id,
    currency_pair,
    market_spot_rate,
    market_forward_rate,
    spot_value_date
) VALUES (
    :tradeLegId,
    :currencyPair,
    :marketSpotRate,
    :marketForwardRate,
    :spotValueDate
 )