package com.deltafinance.privacy.application;

import com.deltafinance.shared.audit.AuditEventType;
import com.deltafinance.shared.audit.AuditService;
import com.deltafinance.shared.time.Clock;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exportação portátil dos dados ativos do titular.
 *
 * <p>Gera um ZIP em streaming com JSON Lines e CSV por conjunto de dados. Valores financeiros são
 * decifrados para o titular; segredos, credenciais, hashes de senha, tokens, chaves, HMACs e
 * identificadores externos não são exportados. Nenhum dado de outro usuário entra na consulta.
 *
 * <p>O catálogo de datasets fica em {@link ExportDatasetCatalog}; este serviço cuida do streaming,
 * do manifesto, do CSV e da auditoria.
 */
@Service
public class ExportService {

  private static final int FORMAT_VERSION = 1;

  private final JdbcTemplate jdbcTemplate;
  private final ExportDatasetCatalog datasetCatalog;
  private final AuditService auditService;
  private final Clock clock;
  private final JsonMapper jsonMapper = JsonMapper.shared();

  public ExportService(
      JdbcTemplate jdbcTemplate,
      ExportDatasetCatalog datasetCatalog,
      AuditService auditService,
      Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.datasetCatalog = datasetCatalog;
    this.auditService = auditService;
    this.clock = clock;
  }

  public ExportResult export(UUID userId, OutputStream output) throws IOException {
    auditService.record(
        userId,
        AuditEventType.EXPORT_REQUESTED,
        "app_user",
        userId,
        Map.of("format", "zip", "content", "jsonl+csv", "version", FORMAT_VERSION));

    ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
    Map<String, Integer> files = new LinkedHashMap<>();
    int totalRecords = 0;
    for (ExportDataset dataset : datasetCatalog.datasets()) {
      int count = writeJsonLines(zip, dataset, userId);
      writeCsv(zip, dataset, userId);
      files.put(dataset.name() + ".jsonl", count);
      files.put(dataset.name() + ".csv", count);
      totalRecords += count;
    }

    Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("format", "delta-finance-export");
    manifest.put("version", FORMAT_VERSION);
    manifest.put("exportedAt", clock.now().toString());
    manifest.put("timezone", "America/Sao_Paulo");
    manifest.put("currency", "BRL");
    manifest.put("recordCount", totalRecords);
    manifest.put("files", files);
    manifest.put(
        "notes",
        List.of(
            "Dados ativos e históricos do titular da conta.",
            "Segredos, credenciais, API Keys, hashes de senha, tokens, chaves e HMACs não são exportados.",
            "Documentação do formato e política de retenção: docs/security.md."));
    zip.putNextEntry(new ZipEntry("manifest.json"));
    zip.write(jsonMapper.writeValueAsBytes(manifest));
    zip.closeEntry();
    zip.finish();

    return new ExportResult(totalRecords, files.size() + 1);
  }

  private int writeJsonLines(ZipOutputStream zip, ExportDataset dataset, UUID userId)
      throws IOException {
    zip.putNextEntry(new ZipEntry(dataset.name() + ".jsonl"));
    int[] count = {0};
    try {
      jdbcTemplate.query(
          dataset.sql(),
          (RowCallbackHandler)
              rs -> {
                Map<String, Object> row = dataset.mapper().map(rs);
                try {
                  zip.write(jsonMapper.writeValueAsBytes(row));
                  zip.write('\n');
                } catch (IOException exception) {
                  throw new UncheckedIOException(exception);
                }
                count[0]++;
              },
          userId);
    } catch (UncheckedIOException exception) {
      throw exception.getCause();
    }
    zip.closeEntry();
    return count[0];
  }

  private void writeCsv(ZipOutputStream zip, ExportDataset dataset, UUID userId)
      throws IOException {
    zip.putNextEntry(new ZipEntry(dataset.name() + ".csv"));
    writeCsvLine(zip, dataset.columns());
    try {
      jdbcTemplate.query(
          dataset.sql(),
          (RowCallbackHandler)
              rs -> {
                Map<String, Object> row = dataset.mapper().map(rs);
                List<String> values = new ArrayList<>(dataset.columns().size());
                for (String column : dataset.columns()) {
                  values.add(csvValue(row.get(column)));
                }
                try {
                  writeCsvLine(zip, values);
                } catch (IOException exception) {
                  throw new UncheckedIOException(exception);
                }
              },
          userId);
    } catch (UncheckedIOException exception) {
      throw exception.getCause();
    }
    zip.closeEntry();
  }

  private static void writeCsvLine(ZipOutputStream zip, List<String> values) throws IOException {
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        zip.write(',');
      }
      String value = values.get(index) == null ? "" : values.get(index);
      boolean quote =
          value.contains(",")
              || value.contains("\"")
              || value.contains("\n")
              || value.contains("\r");
      if (quote) {
        zip.write('"');
        zip.write(value.replace("\"", "\"\"").getBytes(StandardCharsets.UTF_8));
        zip.write('"');
      } else {
        zip.write(value.getBytes(StandardCharsets.UTF_8));
      }
    }
    zip.write('\n');
  }

  private String csvValue(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof java.time.temporal.TemporalAccessor
        || value instanceof String
        || value instanceof Number
        || value instanceof Boolean) {
      return String.valueOf(value);
    }
    try {
      return jsonMapper.writeValueAsString(value);
    } catch (RuntimeException exception) {
      return "";
    }
  }

  /** Contagens sanitizadas para log e resposta; não contém dados financeiros. */
  public record ExportResult(int records, int files) {}
}
