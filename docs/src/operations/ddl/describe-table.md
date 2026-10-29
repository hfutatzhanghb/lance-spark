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

DESCRIBE EXTENDED vectors;
-- Table Properties includes: embedding.arrow.fixed-size-list.size=756

SHOW TBLPROPERTIES vectors ('embedding.arrow.fixed-size-list.size');
-- value: 756
```
