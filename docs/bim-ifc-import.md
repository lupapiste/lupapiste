# BIM / IFC Data Import

This document describes the end-to-end flow for reading IFC model files, extracting
building data from them, and applying that data to Lupapiste application documents.

---

## Overview

When an architect or designer uploads an IFC file and links it to a building permit
application, Lupapiste automatically sends the file to an external **Bimmer** microservice.
The service parses the IFC property sets and returns the structured data. Lupapiste stores
the extracted values and makes them available for the applicant to review and apply to the
application's document fields in one click.

The pipeline also triggers model validation checks, code-compliance checks, and XKT
conversion for the 3D viewer — all as parallel subtasks of the same job.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  Lupapiste                                                                       │
│                                                                                  │
│  File upload ──► linked_files/ensure-file-version                                │
│                         │                                                        │
│                         ▼                                                        │
│         post-process-linked-file :bim-model-file  (bim/core.clj)                │
│                         │                                                        │
│            ┌────────────┼──────────────┬──────────────────────┐                 │
│            ▼            ▼              ▼                       ▼                 │
│    ifc-data-snapshot  xkt-generation  validation-check  compliance-check         │
│            │            │              │                       │                 │
│            └────────────┴──────────────┴───────────────────────┘                │
│                         │  one combined job, one pubsub message per subtask      │
│                         ▼                                                        │
│                  PubSub outbound ──────────────────────────────────────────────► │
│                                                                                  │
│ ◄──────────────────────────────────── PubSub inbound (from-bim-service)         │
│                         │                                                        │
│         store-ifc-data-snapshot!  (bim/data_update.clj)                         │
│                         │                                                        │
│                         ▼                                                        │
│           save-ifc-data-snapshot!  (bim/bim_extracted_data.clj)                 │
│                         │  ifc-field-mappings applied, IfcDoc stored in MongoDB  │
│                         ▼                                                        │
│              :ifc-data collection  (MongoDB)                                     │
│                         │                                                        │
│                         ▼  (user action)                                         │
│                apply-ifc-model-data  (bim/api.clj)                               │
│                         │                                                        │
│                         ▼                                                        │
│               Application document fields updated                                │
└──────────────────────────────────────────────────────────────────────────────────┘
                          │                  ▲
                          ▼                  │
                   Bimmer microservice ───────┘
                   (external, via PubSub)
```

---

## Step 1 — File Upload Trigger

**Entry point:** `lupapalvelu.linked-files/post-process-linked-file`

When a BIM IFC file is uploaded and linked to an application via
`linked-files/ensure-file-version`, the file metadata document is upserted in the
`:linked-file-metadata` collection and then passed directly to the `post-process-linked-file`
multimethod. The dispatch is based on the `:target-entity :target-type` field of the linked
file document, keywordized to `:bim-model-file`.

**Implementation:** `defmethod linked-files/post-process-linked-file :bim-model-file` in
`lupapalvelu.bim.core`.

### What it does

1. Resolves the **operation name** from the operation-id stored in the linked file's
   target-entity (e.g. `"kerrostalo-rivitalo"`, `"laajentaminen"`).
2. Derives the **schema name** for that operation using `op/get-operation-schema`
   (e.g. `"uusiRakennuskohde"`, `"rakennuksen-laajentaminen"`). This is needed later so
   the extraction result is stored against the correct document schema.
3. Builds a **`base-context`** map containing:
   - `application-id`, `operation-id`, `operation-name`, `schema-name`
   - `file-id`, `bucket`, `object-key` (GCS path to the IFC file)
4. Determines which **subtasks** to include in the job:
   - **`ifc-data-snapshot`** — always included. Sends the IFC file to Bimmer for property
     extraction. The context includes the psets query (see §3) and the
     `linked-metadata-file-version-id` to tie the snapshot to the exact file version.
   - **`validation-check`** — included if validation checking plans exist for the operation.
   - **`compliance-check`** — included if compliance plans exist, the model is not
     completion-time, and the organisation has the `bim-compliance-check-enabled` flag set.
   - **`xkt-generation`** — included if the file size is within the viewer limit
     (`bim.viewer-max-filesize` environment value).
5. Creates **one combined job** via `job/start` with all active subtasks. Each subtask
   is a key in the job's `:value` map, tracking its own `:status`.
6. Iterates the subtasks and calls `send-bim-job-pubsub` for each — publishing one message
   per subtask with the relevant handler keyword and response topic.

---

## Step 2 — PubSub Messaging

**Namespace:** `lupapalvelu.bim.core`, `lupapalvelu.bim.protocol`

### Outbound (Lupapiste → Bimmer)

`send-bim-job-pubsub` builds the outbound pubsub message:

```clojure
{:handler            :get-model-properties   ;; bimmer handler key
 :response-expected? true
 :response-topic     "from-bim-service"       ;; where bimmer sends the response
 :message-id         "<job-id>"
 :data               { ... extraction request payload ... }}
```

The extraction request payload is constructed by
`bim-proto/-extraction-request` (implemented by `bimmer-core/->Extraction`), which
builds the **psets query** describing which IFC property sets and properties to extract
(see §3).

### Inbound (Bimmer → Lupapiste)

Lupapiste subscribes to three inbound topics at startup via Mount `defstate`:

| Topic | Subscriber state | Handler |
|---|---|---|
| `"from-bim-service"` | `bim-extraction-response-subscriber` | `store-ifc-data-snapshot!` |
| `"from-bim-check-service"` | `bim-checking-response-subscriber` | `update-checking-data` |
| `"from-bim-xkt-service"` | `bim-ifc-to-xkt-response-subscriber` | `ifc-to-xkt/store-xkt-response` |

The `BIMExtraction` protocol (`lupapalvelu.bim.protocol`) defines three methods:

| Method | Purpose |
|---|---|
| `-extraction-request [self context]` | Build the pubsub payload and topic to send to Bimmer |
| `-extraction-handler [self message]` | Handle an internal extraction request (no-op for Bimmer; Bimmer handles externally) |
| `-extraction-response [self message]` | Parse the raw inbound pubsub message and return the IFC properties map |

The active implementation is `bimmer-core/->Extraction`, bound to `*extraction-instance*`
(dynamically rebindable in tests).

---

## Step 3 — Property Set Query (psets query)

**Namespace:** `lupapalvelu.bim.bimmer.bimmer-to-lp`, `lupapalvelu.bim.bimmer.ifc-field-mappings`

The **psets query** tells Bimmer which IFC entities and property sets to read from the file.
It is a map from IFC entity class keyword to a list of property set descriptors:

```clojure
{:IfcBuilding    [{:name "FI_Kohde"} {:name "FI_Talotekniikka"}]
 :IfcSite        [{:name "FI_Kiinteistö"}]
 :IfcSpatialZone [{:name "FI_Huoneisto"}
                  {:name "FI_Laajuustieto"
                   :replace {"Tyyppi" {"Huoneistoala" :area
                                       "Kokonaisala"  :area}}}]
 :IfcSpace       [{:name "FI_Laajuustieto"}]}
```

The query is **derived automatically** from the three mapping tables in
`ifc-field-mappings.clj` by `bimmer-to-lp/psets-query` — no manual maintenance needed.

The `:replace` option on `IfcSpatialZone FI_Laajuustieto` instructs Bimmer to filter
instances by a `Tyyppi` property value and return the numeric `area` measurement instead
of the raw property bag.

---

## Step 4 — Receiving the Extraction Response

**Namespace:** `lupapalvelu.bim.data-update`

When Bimmer finishes processing and publishes its response to `"from-bim-service"`,
`store-ifc-data-snapshot!` is called:

1. Looks up the running **job** using the `response-to` message field (= the job id).
2. Reads the `ifc-data-snapshot` sub-task from the job's `:value` to retrieve the stored
   context (application-id, schema-name, operation-name, file-id, query, …).
3. Calls `bim-proto/-extraction-response` to parse the raw pubsub message into an IFC
   **properties map** — a nested structure keyed by entity class → instance list → pset
   name → property name → value.
4. If properties are present: calls `save-ifc-data-snapshot!` and marks the sub-task
   `:done`.
5. If properties are absent or an exception is thrown: logs a warning/error and marks the
   sub-task `:error`.

---

## Step 5 — Field Mapping and Storage

**Namespaces:** `lupapalvelu.bim.bimmer.ifc-field-mappings`,
`lupapalvelu.bim.bimmer.bimmer-to-lp`,
`lupapalvelu.bim.bim-extracted-data`

### Mapping tables

All IFC → LP mappings live in `ifc-field-mappings.clj`. There are four tables:

#### `field-mappings` — scalar fields
Each entry maps a single IFC property to a single LP document field. The target document
path is derived at runtime by walking the schema for the `:ifc-mapping` annotation:

```clojure
{:ifc-mapping :ifc-ulkokuori-kerrosluku   ;; matched against schema annotation
 :entity      :IfcBuilding
 :pset        :FI_Kohde
 :property    :RakennuksenKerrosluku
 :transform   str}
```

For fields inside **repeating tables** (e.g. `huoneistot`), the derived path prefix
matches the table path and the value is indexed by the IFC instance position.

#### `row-mappings` — boolean-driven repeating table rows
IFC exposes multi-value selections as individual boolean properties (one per option).
Each truthy boolean produces one row in the LP repeating table:

```clojure
{:table-path [:rakennuskohteen-osa :energiatiedot :lammitystapa]
 :entity     :IfcBuilding
 :pset       :FI_Talotekniikka
 :property   :LämmitystapaVesikeskuslämmitys
 :row        {:lammitystapatyyppi "vesikeskus"}}
```

Groups covered: `verkostoliittymat`, `lammonlahde`, `lammitystapa`, `julkisivu`,
`kantavaRakennusaine`, `varuste`.

#### `aggregate-mappings` — summed area fields
Some IFC area measurements are spread across multiple `IfcSpace` or `IfcSpatialZone`
instances and must be summed. Each entry uses a raw string `:property-key` (the IFC
data dictionary key, which may contain spaces):

```clojure
{:ifc-mapping  :ifc-agg-kerrosala   ;; matched against schema annotation
 :entity       :IfcSpace
 :pset         :FI_Laajuustieto
 :property-key "Kerrosala"}
```

`build-aggregate-data` sums all numeric values for the key across every entity instance
and writes the result as a rounded integer string.

Fields mapped via aggregation:

| IFC property key | Entity | LP path |
|---|---|---|
| `"Kokonaisala"` | `IfcSpace` | `ulkokuorentiedot.kokonaisala` |
| `"Kerrosala"` | `IfcSpace` | `ulkokuorentiedot.kerrosala` |
| `"Rakennusoikeudellinen kerrosala"` | `IfcSpace` | `ulkokuorentiedot.rakennusoikeudellinenKerrosala` |
| `"Kellariala"` | `IfcSpace` | `sisatilat.kellariala` |
| `"Ullakkoala"` | `IfcSpace` | `sisatilat.ullakkoala` |
| `"Huoneistoala"` | `IfcSpatialZone` | `sisatilat.huoneistoala` |

#### `rakennuspaikka` fields
Fields that target the separate `rakennuspaikka` document (Kaavatilanne and
Hallintaperuste from `IfcSite / FI_Kiinteistö`) are part of `field-mappings` and are
selected automatically when the schema walk is run against the `rakennuspaikka` schema.

### Building the LP data map

`create-data-map` deep-merges the outputs of three build functions — one for scalar/repeating
fields, one for boolean-flag rows, and one for aggregated area values. All three take `schema`
as their first argument; it is walked to resolve `:ifc-mapping` annotations to LP paths and to
detect which paths sit inside repeating tables.

`create-data-map` also derives and sets `kaytto.kohteen-laji` (`"rakennus"` or `"rakennelma"`)
from the operation type when an `operation-name` is provided.

### The IfcDoc document

`save-ifc-data-snapshot!` stores the result in the `:ifc-data` MongoDB collection as
an `IfcDataDocument`:

```clojure
{:linked-metadata-file-version-id "<version-id>"   ;; FK to the specific file version
 :modified                        <timestamp>
 :ifc-query                       { ... psets query ... }
 :ifc-properties                  { ... raw IFC response ... }
 :ifc-doc {:id              "<object-id>"
           :created         <timestamp>
           :schema-info     {:name "uusiRakennuskohde" :version 1}
           :data            { ... LP document data map ... }
           :rakennuspaikka-data { ... }}}
```

The `:data` map uses the same key structure as Lupapiste document data: top-level group
names as keywords (`:kaytto`, `:ulkokuorentiedot`, `:huoneistot`, …).

---

## Step 6 — User Review and Apply

**Namespace:** `lupapalvelu.bim.api`

### Checking which files have data ready

`ifc-data-file-version-ids` (defquery) returns the subset of the application's linked-file
version IDs that have an `:ifc-data` snapshot stored. The frontend polls this to show a
"Apply BIM data" button next to the relevant file version.

### Previewing extracted data

`fetch-ifc-doc` (defquery, parameters: `id`, `file-version-id`) returns the stored
`IfcDoc` together with the matching application document for the linked operation. The
frontend renders this as a preview of what will be written into the application.

### Applying data to the application

`apply-ifc-model-data` (defcommand, parameters: `id`, `file-version-id`, `docId`):

1. Validates that the application is editable and the `docId` matches the document
   associated with the IFC file's linked operation.
2. Fetches the stored `:ifc-doc` for the given `file-version-id`.
3. Converts `(:data ifc-doc)` to model-updates via `doc-model/map2updates`, strips
   read-only fields, computes remove-updates for top-level group paths that are present
   in the data map, and persists via `doc-persistence/persist-model-updates`.
4. If `(:rakennuspaikka-data ifc-doc)` is non-empty, finds the application's
   `rakennuspaikka` document and applies that data in the same way.

The apply step is **idempotent**: running it multiple times overwrites the same fields
with the same values.

---

## Fields Not Mapped

Some IFC fields cannot be mapped automatically and are intentionally excluded.
The full tracking table is in `RAVA.org` at the project root. Summary of exclusions:

| Field | Reason |
|---|---|
| `Perusparannus` | IFC is a boolean; LP only has `perusparannusPaiva` (a date field) |
| `Muutosala` | Not present in the `uusiRakennuskohde` schema |
| `LiiketaloudellinenRakentaminen` | IFC boolean → LP enum; requires additional business logic |
| `Omistajalaji` | Lives inside the repeating `rakennuksenOmistajat` table; IFC and LP use different value formats |
| `Lämmitystapa` (Aurinkolämmitys, Ilmalämpöpumppu, Muu) | No matching option in LP lämmitystapa schema |
| `Pysyvä rakennustunnus` | Only relevant in amendment/extension permits (different schema) |
| Muutos-area fields (6) | Only relevant in extension permits (different schema) |
| Coordinates (Eastings/Northings) | Set via the map UI; coordinate system transform required |
| `Huoneistotunnus` | Only relevant in amendment/extension permits |

---

## Key Files

| File | Role |
|---|---|
| `src/lupapalvelu/bim/core.clj` | Pipeline entry point: `post-process-linked-file`, job creation, pubsub subscribers |
| `src/lupapalvelu/bim/protocol.clj` | `BIMExtraction` and `BIMChecking` protocols |
| `src/lupapalvelu/bim/data_update.clj` | Inbound response handler: `store-ifc-data-snapshot!` |
| `src/lupapalvelu/bim/bim_extracted_data.clj` | `IfcDoc` schema, `save-ifc-data-snapshot!`, `fetch-ifc-doc` |
| `src/lupapalvelu/bim/api.clj` | `apply-ifc-model-data`, `fetch-ifc-doc`, `ifc-data-file-version-ids` commands/queries |
| `src/lupapalvelu/bim/bimmer/ifc_field_mappings.clj` | All IFC→LP mappings: `field-mappings`, `row-mappings`, `aggregate-mappings` |
| `src/lupapalvelu/bim/bimmer/bimmer_to_lp.clj` | Mapping execution: `build-fields-data`, `build-aggregate-data`, `create-ifc-rows-data`, `psets-query` |
| `src/lupapalvelu/bim/bimmer/core.clj` | `BIMExtraction` / `BIMChecking` implementations for the Bimmer service |
| `src/lupapalvelu/linked_files.clj` | `post-process-linked-file` multimethod definition |
| `RAVA.org` | Full field-by-field mapping tracking table |
| `docs/bim-pubsub-process.puml` | PlantUML diagram of the pubsub flow |
