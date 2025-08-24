<#--
Input parameters expected in the model:
    ruleId : string (e.g. 'RULE_1')
-->

SELECT
    mcfg.murex_book_code AS murexBookCode,
    mcfg.description AS description,
    mcfg.tps_outbound AS tpsOutbound,
    mcfg.transformations AS transformations
FROM murex_book_config mcfg
WHERE mcfg.murex_book_code IN (
    SELECT rbm.mx_booking_code
    FROM rule_mx_book_map rbm
    WHERE rbm.rule_id = '${ruleId}'
)