<#-- Input parameters expected in the model:
    isActive           : int    (e.g. 1)
    currency      : string (e.g. HKD)
-->

SELECT
    original_currency as originalCurrency,
    functional_currency as functionalCurrency,
    currency_category as currencyCategory
FROM currency
WHERE functional_currency = '${currency}'
AND is_active = '${isActive}'