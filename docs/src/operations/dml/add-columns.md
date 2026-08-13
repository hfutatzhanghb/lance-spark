# ADD COLUMNS FROM

Similar to most table formats, Lance supports traditional schema evolution: 
adding, removing, and altering columns in a dataset. 
Most of these operations can be performed without rewriting the data files in the dataset, 
making them very efficient operations. 

In addition, Lance supports data evolution, 
which allows you to also backfill existing rows with the new column data without rewriting the data files in the dataset, 
making it highly suitable for use cases like ML feature engineering.
This feature is implemented in Spark as `ALTER TABLE ADD COLUMNS FROM`

!!! warning "Spark Extension Required"
    This feature requires the Lance Spark SQL extension to be enabled. 
    See [Spark SQL Extensions](../../config.md#spark-sql-extensions) for configuration details.

Example:

```sql
CREATE TEMPORARY VIEW tmp_view
AS
SELECT _rowaddr, _fragid, hash(name) as name_hash
FROM users;

ALTER TABLE users ADD COLUMNS name_hash FROM tmp_view;
```

No table rewrite, no data movement—just a new column that is instantly queryable.

## Adding a Blob v2 Column

To add a blob v2 column, create the target table with Lance file format version `2.2` or higher,
then persist the future `BINARY` column's blob encoding with `ALTER TABLE SET TBLPROPERTIES` before
adding the column.

```sql
CREATE TABLE users (
    id INT,
    name STRING
) USING lance
TBLPROPERTIES (
    'file_format_version' = '2.2'
);

ALTER TABLE users
SET TBLPROPERTIES ('content.lance.encoding' = 'blob');

CREATE TEMPORARY VIEW content_backfill AS
SELECT _rowaddr, _fragid, CAST(name AS BINARY) AS content
FROM users;

ALTER TABLE users ADD COLUMNS content FROM content_backfill;
```

The source column must have Spark type `BINARY`. After the operation, reads expose `content` as a
blob v2 descriptor struct, so descriptor fields can be queried without loading the bytes:

```sql
SELECT id, content.size, content.kind FROM users;
```

!!! note
    Because we use `_rowaddr` and `_fragid` to address the target dataset's rows for the new column's data, 
    the temporary view should contain `_rowaddr` and `_fragid`.
