# Overview

This API permits the *dmCity* system to submit application state updates into applications in *Lupapiste*. The state updates represent various dates and times assigned to the application in dmCity.

Note that the state updates must contain the whole state as the submission will overwrite any existing states in Lupapiste.


# Specification


## Authentication

The API authentication is based on *basic authentication*.


## Endpoint

The endpoint is `/rest/application/$applicationid/states`, where `$applicationid` is the Lupapiste identifier for the application. The endpoint accepts only `PUT` requests.


## Payload

The request payload must have a content type of `application/json` and contains a single field.

| Field    | Type  | Required? | Contents                       |
|----------|-------|-----------|--------------------------------|
| `states` | array | yes       | An array of *state structures* |

Each *state structure* has the following fields

| Field      | Type    | Required? | Contents                  | Format                    |
|------------|---------|-----------|---------------------------|---------------------------|
| `state`    | string  | yes       | Name of the state         |                           |
| `enabled`  | boolean | no        | A generic true/false flag |                           |
| `date`     | string  | no        | A generic date field      | `"YYYY-MM-DD"`            |
| `datetime` | string  | no        | A generic date-time field | `"YYYY-MM-DD'T'HH:mm:ss"` |

The `date` and `datetime` fields are interpreted to be in the `Europe/Helsinki` timezone. The `datetime` field also accepts the `"YYYY-MM-DD'T'HH:mm:ssZ"` format, which is interpreted to mean UTC.

The optionality of the three data fields (`enabled`, `date`, and `datetime`) depends on the value of the `state` field.

| State                                 | Data       | Finnish description                                            |
|---------------------------------------|------------|----------------------------------------------------------------|
| `submitted-date`                      | `date`     | "Hakemus kirjattu"                                             |
| `order-date`                          | `date`     | "Toimitusmääräys annettu"                                      |
| `decision-date`                       | `date`     | "Päätöksen päivämäärä"                                         |
| `pending-date`                        | `date`     | "Vireilletulopäivä"                                            |
| `display-decision-date`               | `date`     | "Nähtäville asettamisen päiväys"                               |
| `display-start-date`                  | `date`     | "Nähtävilläolon alkaminen"                                     |
| `display-end-date`                    | `date`     | "Nähtävilläolon päättyminen"                                   |
| `acceptance-date`                     | `date`     | "Hyväksymispäivä"                                              |
| `apply-date`                          | `date`     | "Voimaantulopäivä"                                             |
| `survey-finished`                     | `date`     | "Maastotyöt tehty"                                             |
| `initial-meeting`                     | `datetime` | "Alkukokous"                                                   |
| `final-meeting`                       | `date`     | "Loppukokous"                                                  |
| `end-date`                            | `date`     | "Päättymispäivä"                                               |
| `appeal-period`                       | `enabled`  | "Valitusaika"                                                  |
| `validation-required`                 | `enabled`  | "Tarkastettavaksi"                                             |
| `registration-required`               | `enabled`  | "Rekisteröitäväksi"                                            |
| `registered`                          | `date`     | "Rekisteröity"                                                 |
| `plan-pending-date`                   | `date`     | "Asemakaavan vireilletulo"                                     |
| `plan-draft-comments-end-date`        | `date`     | "Mielipiteiden esittämisaika päättyy"                          |
| `plan-draft-display-decision-date`    | `date`     | "Päätös kaavaluonnoksen nähtäville asettamisesta"              |
| `plan-draft-display-start-date`       | `date`     | "Kaavaluonnoksen nähtävillä olo alkaa"                         |
| `plan-draft-display-end-date`         | `date`     | "Kaavaluonnoksen nähtävillä olo päättyy"                       |
| `plan-proposal-display-decision-date` | `date`     | "Päätös kaavaehdotuksen nähtäville asettamisesta"              |
| `plan-proposal-display-start-date`    | `date`     | "Kaavaehdotuksen nähtävillä olo alkaa"                         |
| `plan-proposal-display-end-date`      | `date`     | "Kaavaehdotuksen nähtävillä olo päättyy"                       |
| `statements-board-date`               | `date`     | "Lausuntojen ja muistutusten käsittely lautakunnassa"          |
| `statements-city-date`                | `date`     | "Lausuntojen ja muistutusten käsittely kaupunginhallituksessa" |
| `plan-accepted-date`                  | `date`     | "Asemakaavan hyväksyminen"                                     |
| `plan-enforced-date`                  | `date`     | "Asemakaava on kokonaan tai osittain voimassa"                 |

An example payload with three states (`sample_states.json`):

```json
{
  "states": [
    {
      "state": "submitted-date",
      "date": "2023-01-20"
    },
    {
      "state": "initial-meeting",
      "datetime": "2023-01-20T12:34:56"
    },
    {
      "state": "appeal-period",
      "enabled": false
    }
  ]
}
```


## Requirements and Restrictions

-   The user account:
    -   Must configured as a "REST API user" in Lupapiste
    -   Must have permissions to access the target application and its organization
-   The organization:
    -   Must have the *dmCity* (or *matti*) designated as the integration partner


# Sample Requests

We use the following value as the Lupapiste application id in the examples:

```bash
LP-092-2023-90005
```

We also assume that the authentication credentials are stored in the `$username` and `$password` variables.


## Submitting Without Proper Authorization

Here we omit the username and password:

```bash
curl -s -X PUT "http://localhost:8000/rest/application/LP-092-2023-90005/states" \
     -H 'accept: application/json' \
     -H 'Content-Type: application/json' \
     -d @'/home/lassemaatta/Asiakirjat/sample_states.json' | jq .
```

The response indicates the appropriate error:

```json
{
  "errors": [],
  "status": 403,
  "ok": false,
  "text": "Unauthenticated"
}
```


## Submitting Invalid Parameters

The first level of error checking verifies the HTTP parameter names and enumerations;

Here we submit an invalid payload with

-   The first state has a `dates` field instead of `date`
-   The second state has `datetimes` instead of `datetimes`
-   The third states references an unknown `state`

```json
{
  "states": [
    {
      "state": "submitted-date",
      "dates": "2023-01-20"
    },
    {
      "state": "initial-meeting",
      "datetimes": "2023-01-20T12:34:56"
    },
    {
      "state": "appeal-period---",
      "enabled": false
    }
  ]
}
```

The request:

```bash
curl -s -X PUT "http://localhost:8000/rest/application/LP-092-2023-90005/states" \
     -u $username:$password \
     -H 'accept: application/json' \
     -H 'Content-Type: application/json' \
     -d @'/home/lassemaatta/Asiakirjat/invalid_param_states.json' | jq .
```

The response reports the unexpected fields and the unknown enumeration.

```json
{
  "errors": [
    {
      "field": [
        "states",
        0,
        "dates"
      ],
      "error": "disallowed-key"
    },
    {
      "field": [
        "states",
        1,
        "datetimes"
      ],
      "error": "disallowed-key"
    },
    {
      "field": [
        "states",
        2,
        "state"
      ],
      "error": "not-valid",
      "value": "appeal-period---",
      "expected": [
        "acceptance-date",
        "appeal-period",
        "apply-date",
        "decision-date",
        "display-decision-date",
        "display-end-date",
        "display-start-date",
        "end-date",
        "final-meeting",
        "initial-meeting",
        "order-date",
        "pending-date",
        "registered",
        "registration-required",
        "submitted-date",
        "survey-finished",
        "validation-required"
      ]
    }
  ],
  "status": 400,
  "ok": false,
  "text": "Invalid HTTP parameters"
}
```


## Submitting Invalid Dates

The second level of error checking verifies the date formats.

Here we submit an invalid payload with

-   The first state has `date` in an invalid format
-   The second state has `datetime` in an invalid format

```json
{
  "states": [
    {
      "state": "submitted-date",
      "date": "20.1.2023"
    },
    {
      "state": "initial-meeting",
      "datetime": "2023-01-20 12:34"
    },
    {
      "state": "appeal-period",
      "enabled": false
    }
  ]
}
```

The request:

```bash
curl -s -X PUT "http://localhost:8000/rest/application/LP-092-2023-90005/states" \
     -u $username:$password \
     -H 'accept: application/json' \
     -H 'Content-Type: application/json' \
     -d @'/home/lassemaatta/Asiakirjat/invalid_dates.json' | jq .
```

The response reports the invalid date values.

```json
{
  "errors": [
    {
      "field": [
        "states",
        0,
        "date"
      ],
      "error": "not-valid",
      "value": "20.1.2023"
    },
    {
      "field": [
        "states",
        1,
        "datetime"
      ],
      "error": "not-valid",
      "value": "2023-01-20 12:34"
    }
  ],
  "status": 400,
  "ok": false,
  "text": "Failed to validate states data"
}
```


## Submitting Incorrect State Data

The third level of error checking verifies that the `state` and the attached data match.

Here we submit an invalid payload with

-   The first state has `datetime` instead of `date`
-   The second state has `enabled` instead of `datetime`
-   The third state has `date` instead of `enabled`

```json
  {
  "states": [
    {
      "state": "submitted-date",
      "datetime": "2023-01-20T12:34:56"
    },
    {
      "state": "initial-meeting",
      "enabled": false
    },
    {
      "state": "appeal-period",
      "date": "2023-01-20"
    }
  ]
}
```

The request:

```bash
curl -s -X PUT "http://localhost:8000/rest/application/LP-092-2023-90005/states" \
     -u $username:$password \
     -H 'accept: application/json' \
     -H 'Content-Type: application/json' \
     -d @'/home/lassemaatta/Asiakirjat/states_mismatch.json' | jq .
```

The response reports the mismatched fields.

```json
{
  "errors": [
    {
      "error": "missing-required-key",
      "field": [
        "states",
        0,
        "date"
      ]
    },
    {
      "error": "disallowed-key",
      "field": [
        "states",
        0,
        "datetime"
      ]
    },
    {
      "error": "missing-required-key",
      "field": [
        "states",
        1,
        "datetime"
      ]
    },
    {
      "error": "disallowed-key",
      "field": [
        "states",
        1,
        "enabled"
      ]
    },
    {
      "error": "missing-required-key",
      "field": [
        "states",
        2,
        "enabled"
      ]
    },
    {
      "error": "disallowed-key",
      "field": [
        "states",
        2,
        "date"
      ]
    }
  ],
  "status": 400,
  "ok": false,
  "text": "Status type and fields do not match"
}
```


## Submitting Valid States

Here we submit the valid example given in an earlier section (`sample_states.json`):

```bash
curl -s -X PUT "http://localhost:8000/rest/application/LP-092-2023-90005/states" \
     -u $username:$password \
     -H 'accept: application/json' \
     -H 'Content-Type: application/json' \
     -d @'/home/lassemaatta/Asiakirjat/sample_states.json' | jq .
```

```json
{
  "status": 201,
  "ok": true
}
```
