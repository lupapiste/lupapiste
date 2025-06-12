# Lupapiste KuntaGML Schema Extensions

This folder contains Lupapiste-specific extensions to the official KuntaGML kiinteistotoimitus (cadastral) schema.

## Background

The official KuntaGML kiinteistotoimitus schema (versions 1.0.2 through 1.0.8) does not include all operation types that Lupapiste supports. Specifically, the following operation types are defined in Lupapiste's Clojure mapping code but are missing from the official XSD schemas. These have been added after customers requests:

- **TilanLohkominen** (Plot subdivision)
- **TontinosanLunastus** (Redemption of part of a plot)

This causes XML validation errors when generating KuntaGML for these operation types.

## Files

### kiinteistotoimitus-1.0.8.xsd

Extended version of the official `kiinteistotoimitus-1.0.8.xsd` with:

1. **New operation type elements** (substitutionGroup of `_Toimitus`):
   - `TilanLohkominen` → `TilanLohkominenType`
   - `TontinosanLunastus` → `TontinosanLunastusType`

2. **New complex types** extending `AbstractToimitusFeatureType`:
   - `TilanLohkominenType` — base fields + optional `kuvaus`
   - `TontinosanLunastusType` — base fields + optional `kuvaus`

3. **Lupapiste extensions in ToimitushakemusType** (same as dmcity integration):
   - `lp:viitelupatieto` — reference permit data
   - `lp:toimenpiteet` — operation descriptions
   - `lp:kohteet` — target objects
   - `lp:lausuntotieto` — statements/opinions
   - `lp:liitetieto` — attachments with extended metadata

### lupapiste-1.0.1.xsd

Defines the `lp:` namespace elements used in the extended kiinteistotoimitus schema. This is based on `dmcity/lupapiste-1.0.0.xsd` but updated for yhteiset 2.1.11 (to match kiinteistotoimitus 1.0.8 dependencies).

**Elements defined**:
- `liitetieto` — attachment with `YmparistoLiiteType`
- `viitelupatieto` — permit reference using `LuvanTunnisteTiedotType`
- `lausuntotieto` — statement using `LausuntoRvPType`
- `toimenpiteet` — list of operations (tunnus + text)
- `kohteet` — list of target identifiers

## Relationship to dmcity

The `dmcity/` folder contains extensions specifically for the dmCity (Tiera City & Matti) integration. This `lp/` folder follows the same pattern but:

1. Is based on kiinteistotoimitus **1.0.8** (dmcity uses 1.0.6)
2. Adds the missing `TilanLohkominen` and `TontinosanLunastus` operation types
3. Uses version suffix `-lp` instead of `-dmcity-lp`

The `lp:` namespace (`http://www.lupapiste.fi/gml/dmcity`) is intentionally reused to minimize code changes — the `add-dmcity-ns` function and element mappings from `dmcity.clj` work unchanged.
