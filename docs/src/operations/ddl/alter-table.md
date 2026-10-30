# ALTER TABLE

Changes the schema or properties of a table.

## SET TBLPROPERTIES

Add or update key-value properties on a table:

```sql
ALTER TABLE users SET TBLPROPERTIES ('team' = 'data-eng', 'env' = 'production');
```

If a property already exists, its value is overwritten:

```sql
ALTER TABLE users SET TBLPROPERTIES ('env' = 'staging');
```

## UNSET TBLPROPERTIES

Remove properties from a table:

```sql
ALTER TABLE users UNSET TBLPROPERTIES ('env');
```

Remove multiple properties at once:

```sql
ALTER TABLE users UNSET TBLPROPERTIES ('team', 'env');
```

Unsetting a property that does not exist is a no-op (no error is raised).

## Limitations

Schema and encoding properties for a column already in the table can only be set through
`CREATE TABLE`. `ALTER TABLE ... SET TBLPROPERTIES` rejects those keys, including attempts
to set the current value:

- `<column>.arrow.fixed-size-list.size`, `<column>.arrow.float16`,
  `<column>.arrow.fixed-size-binary.byte-width`, and `<column>.arrow.large_var_char`.
- `<column>.lance.encoding` and the supported `<column>.lance.*` encoding keys:
  `compression`, `compression-level`, `structural-encoding`, `rle-threshold`, and `bss`.

The same keys are accepted when the named column is not in the schema yet. Directory-backed
tables do not persist `CREATE TABLE` user properties, so set the future-column property with
`ALTER TABLE` before `ADD COLUMNS`. For example, set `content.lance.encoding` to `blob` before
adding a blob column. See [ADD COLUMNS](../dml/add-columns.md).

`UNSET TBLPROPERTIES` remains available to clean up legacy config for these keys.
It does not change the stored schema or encoding. Vector dimensions continue to appear
in `DESCRIBE EXTENDED` and `SHOW TBLPROPERTIES`, derived from the schema.
If a SET names any protected existing column, the entire operation is rejected and no
property from that statement is written.

The `enable_stable_row_ids` property also can only be set at table creation;
both SET and UNSET through `ALTER TABLE` are rejected.

```sql
-- Correct: set at creation time
CREATE TABLE users (id BIGINT, name STRING)
    TBLPROPERTIES ('enable_stable_row_ids' = 'true');

-- Rejected: this property is creation-only
ALTER TABLE users SET TBLPROPERTIES ('enable_stable_row_ids' = 'true');
```

## Rename Table

Rename a table within the same namespace:

```sql
ALTER TABLE users RENAME TO new_users;
```

Rename a table to a different namespace:

```sql
ALTER TABLE ns1.users RENAME TO ns2.new_users;
```

!!! note
Rename is only supported when using a namespace-based catalog (`impl=rest`).
Directory-based catalogs do not support table renames.

## Error Behavior

| Scenario | Error |
|----------|-------|
| Source table does not exist | `TABLE_OR_VIEW_NOT_FOUND` |
| Target table name already exists | `TABLE_ALREADY_EXISTS` |
| Directory-based catalog | `UnsupportedOperationException` |
