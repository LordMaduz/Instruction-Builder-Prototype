package com.ruchira.murex.util;

import java.util.Map;

public interface FtlQueryBuilder {

     void init();

     String buildQuery(Map<String, Object> params, String ftlFileName);
}
