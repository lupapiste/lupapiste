# Pate Verdict System

Pate is a collection of various tools (functions, schemas, …) that
together offer a following functionality

 1. Schema-based layout and validation of form data
 2. Verdict templates and verdicts
 3. Schema-based publishing layout targeting html and pdf

Whereas there are lots of different use-cases and related
implementation details, in this document we concentrate the three
major parts listed above.

## Verdict Types

**Backing system verdicts.** Verdicts that have been fetched from a
backing system. These verdicts are never edited in Lupapiste.

**Legacy verdicts.** If Pate is not enabled in an organization, the
manually given verdicts do not utilize any verdict templates. The term
legacy refers to the verdict mechanism that preceded Pate. However,
under the hood, the editing and publishing legacy verdicts is still
done with Pate mechanisms.

**Pate verdicts.** "Full blown" Pate verdicts and verdict templates.

From the user's point of view, each of of the verdict types can
co-exist in the same application and cannot be easily
differentiated.

## Schemas, Schemas, Schemas

A term schema can refer to different things in Pate depending on the
context:

  - UI forms (settings, verdicts, verdict templates) are defined
    by data schemas (see `pate/verdict_schemas.cljc`,
    `pate/legacy_schemas.cljc`, …).
  - The semantics of the data schemas are defined by Plumatic schemas in
    `pate/shared_schemas.cljc`.
  - Organization schema (`organization.clj`) has an entry for verdict
    templates. In addition to template and settings data, the entry
    has other keys as well (`pate/schemas.clj`)
  - Similarly, the application `pate-verdicts` entries are validated
    against verdict schemas (`pate/schemas.clj`)
  - When a verdict is published, a verdict PDF is created. The
    information in the PDF is laid out differently than in the UI. In
    addition to the given verdict data, the PDF typically includes
    external information from the application (e.g., parties,
    buildings, …). The PDF is generated from HTML, which in turn is
    defined with layout schemas (`pate/pdf_layouts.cljc`).

### UI Forms: Data, Layout and Validation

> **Note:** Although schema-based forms are mainly used in Pate, the
    implementation is not in any way Pate-dependent. In other, words the
    same mechanism can be used in other domains as well.

UI data schema structure is fairly simple as it consists of two main
parts:

**Dictionary.** The definition of data. A map where keys are keywords
(called dicts in Pate parlance) and values are Pate component
definitions. The available components are defined in
`pate/shared_schemas.cljc`.

**Sections.** A list of sections. Each section defines a grid-based
layout and refers to dicts. Typically sections are differentiated
visually in the UI, but this is not necessary. For a complex form, the
whole schema can become quite unwieldy. Pate provides tools for
combining schema from multiple subschemas.

Below is real-world example of a subschema for a legacy YA verdict section.

```clojure
{:dictionary {:kuntalupatunnus {:text {:i18nkey :verdict.id},
                                :required? true},
              :handler {:text {:i18nkey :verdict.name},
                        :required? true},
              :verdict-code {:select {:loc-prefix :verdict.status,
                                      :items [:1 :2 :21 :37],
                                      :sort-by :text,
                                      :type :select},
                             :required? true},
              :verdict-section {:text {:i18nkey :verdict.section,
                                       :before :section}},
              :anto {:date {:i18nkey :verdict.anto}, :required? true},
              :lainvoimainen {:date {:i18nkey :verdict.lainvoimainen}},
              :verdict-text {:text {:i18nkey :verdict.text, :lines 20}}},
 :section {:id :verdict,
           :grid {:columns 12,
                  :rows [[{:col 2,
                           :align :full,
                           :dict :kuntalupatunnus}
                          {}
                          {:col 4, :align :full, :dict :handler}]
                         [{:dict :verdict-section}
                          {:col 2}
                          {:col 4, :align :full, :dict :verdict-code}]
                         [{:col 10, :align :full, :dict :verdict-text}]
                         [{:col 2, :dict :anto}
                          {:col 2, :dict :lainvoimainen}]]}}}

```

The dictionary has seven dicts:
  - kuntalupatunnus, handler, verdict-section and verdict-text are
    textual data. Verdict-text is represented as textarea others as
    text fields (inputs)
  - verdict-code can have one of four values and is represented as
    select.
  - anto and lainvoimainen are dates, represented as date fields.
  - verdict-section and verdict text are optional, other dicts are
    required. Required fields are highlighted in the UI and also taken
    into account in validation: a verdict cannot be published if any
    required dict is missing.

Section layout is based on grids. Here the grid has 12 columns and
four rows:
  1. Kuntalupatunnus and handler
  2. Verdict-section and verdict-code
  3. Verdict-text
  4. Anto and lainvoimainen

In this case, each layout cell consists of label and the
component. The label texts are resolved based on the current UI
language and the localization information (i18nkeys) in the schema.

Since schemas are in cljc-files they can be utilised both in the
backend (validation) and frontend (layout).

In principle the Pate validation mechanism (`pate/schemas`) is very
straightforward: given the dict and its proposed new value, the
validation checks whether the value adheres to the dict
schema. Sometimes the check is trivial (text fields) and sometimes
more convoluted (the range of acceptable values can be dynamic).

In Pate, the values are given in the frontend and
validated/processed in the backend. However, Pate does not provide a
generic mechanism for transporting data back and forth. Thus, each
form implementation (settings, verdicts, …) have their own APIs for
this purpose (e.g., `pate/verdict_template_api.clj`).
