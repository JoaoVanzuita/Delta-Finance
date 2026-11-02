package com.deltafinance.privacy.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Um conjunto de dados exportável: colunas estáveis, SQL escopado e mapeamento da linha. */
record ExportDataset(String name, List<String> columns, String sql, Mapper mapper) {

  @FunctionalInterface
  interface Mapper {
    Map<String, Object> map(ResultSet rs) throws SQLException;
  }
}
