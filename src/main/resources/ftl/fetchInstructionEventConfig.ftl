<#--
Input parameters expected in the model:
    businessEvent       : string (e.g. 'Inception')
    hedgingInstrument   : string e.g.  'FX Spot')
    hedgeMethod          : string (e.g. 'COH')
    currencyType        : string (e.g. 'Restricted' / 'Non Restricted')
    status               : int    (e.g. 1)
-->

SELECT
    rule_id AS ruleId,
    description AS description,
    business_event AS businessEvent,
    hedge_method AS hedgeMethod,
    currency_type AS currencyType,
    nav_type AS navType,
    hedging_instrument AS hedgingInstrument,
    status AS status
FROM h_business_event_config
WHERE 1=1
<#if businessEvent??>
  AND business_event = '${businessEvent}'
</#if>
<#if hedgeMethod??>
  AND hedge_method = '${hedgeMethod}'
</#if>
<#if hedgingInstrument??>
  AND hedging_instrument = '${hedgingInstrument}'
</#if>
<#if currencyType??>
  AND currency_type = '${currencyType}'
</#if>
<#if status??>
  AND status = ${status}
</#if>
ORDER BY nav_type