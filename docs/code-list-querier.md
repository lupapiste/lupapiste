# Code List Querier

Fetches external code list data from koodistot.suomi.fi, stores it as EDN, and generates i18n translation files.

## File Structure

### Core Files
- **`dev-src/code_list_querier.clj`** - Main logic for fetching, transforming, and storing code lists
- **`src/lupapalvelu/ryhti/api/mappings.clj`** - URI→keyword mappings and custom reader functions. These mappings need to contain all the possible uri values from the code list.
- **`stest/lupapalvelu/codes_modified_stest.clj`** - Tests that verify code lists are up-to-date

### Generated and Override Files
- **`resources/codes/*.edn`** - Stored code list data (e.g., `languages.edn`, `suhdepintaan.edn`)
- **`resources/i18n/generated/*.txt`** - Translation files (e.g., `languages.txt`, `suhde-pintaan.txt`)
- **`resources/i18n/overrides/*.txt`** - Manual override files for missing or incorrect translations. The override files can share the same name as the generated file.

## Usage

Update all code lists:
```clojure
(update-code-lists!)
```

Update specific code list:
```clojure
(update-code-lists! :language-data)
```

## Adding New Code Lists

1. Add a new configuration to `code-list-configs` in `code_list_querier.clj`. See the `CodeListConfigs` schema in [`dev-src/code_list_querier.clj`](../dev-src/code_list_querier.clj) for details on the configuration keys.

2. For custom URI mappings, add mapping to `mappings.clj` and reference via `:code-value-fn`

3. Run `(update-code-lists! :your-key)` and commit generated files
