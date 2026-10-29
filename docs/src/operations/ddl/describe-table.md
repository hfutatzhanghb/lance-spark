# DESCRIBE TABLE

Inspect table structure and metadata.

Describe table structure:

```sql
DESCRIBE TABLE users;
```

Show detailed table information:

```sql
DESCRIBE EXTENDED users;
```

Fixed-size vector columns appear as `array<float>` or `array<double>` in the
column list. `DESCRIBE EXTENDED` includes their dimensions in the `Table Properties`
row, including when the dimensions are stored only in the Lance schema:

```sql
CREATE TABLE vectors (id BIGINT, embedding ARRAY<FLOAT>)
USING lance
TBLPROPERTIES ('embedding.arrow.fixed-size-list.size' = '756');

-- Reload the table so dimensions are read from the stored Lance schema.
REFRESH TABLE vectors;

DESCRIBE EXTENDED vectors;
-- Table Properties includes: embedding.arrow.fixed-size-list.size=756

SHOW TBLPROPERTIES vectors ('embedding.arrow.fixed-size-list.size');
-- value: 756
```

Dimension properties are derived from the actual Lance schema when table properties
are read. If a stored config value disagrees with the schema, the displayed dimension
comes from the schema. `ALTER TABLE ... SET TBLPROPERTIES` does not resize an existing
vector column. `ALTER TABLE ... UNSET TBLPROPERTIES` removes the stored config entry,
but the dimension still appears in `DESCRIBE EXTENDED` and `SHOW TBLPROPERTIES` because
the column remains a fixed-size vector.
