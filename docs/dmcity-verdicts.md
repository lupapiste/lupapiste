# Overview

This API permits the *dmCity* system to submit verdicts into applications in *Lupapiste*.


# Specification


## Authentication

The API authentication is based on *basic authentication*.


## Endpoint

The verdict endpoint is `/rest/application/$applicationid/verdict`, where `$applicationid` is the Lupapiste identifier for the application. The endpoint accepts only `POST` requests.


## Payload

The request payload must have a content type of `multipart/form-data` and contains one or more parts.

| Part name    | Type               | Count | Contents                        |
|--------------|--------------------|-------|---------------------------------|
| "verdict"    | `application/json` | 1     | The verdict data as JSON        |
| any filename | any                | 0-N   | The binary contents of the file |


### The Verdict JSON

| Field           | Type    | Description                               | Format         |
|-----------------|---------|-------------------------------------------|----------------|
| `verdict-code`  | string  |                                           | enumeration    |
| `decided-by`    | string  | First name and last name                  |                |
| `section`       | integer |                                           |                |
| `decision-date` | string  | Date of decision, "päätöspvm"             | `"yyyy-mm-dd"` |
| `apply-date`    | string  | Date when applicable, "lainvoimaisuuspvm" | `"yyyy-mm-dd"` |
| `attachments`   | map     | Describes each attachment (see below)     |                |

The date fields are interpreted to be in the `Europe/Helsinki` timezone.

The `verdict-code` enumeration is as follows

| Value             | Finnish label               |
|-------------------|-----------------------------|
| `"accepted"`      | Annettu lausunto            |
| `"conditional"`   | Ehdollinen                  |
| `"denied"`        | Evätty                      |
| `"granted"`       | Myönnetty                   |
| `"granted-start"` | Myönnetty aloitusoikeudella |
| `"statement"`     | Annettu lausunto            |
| `"tabled"`        | Pantu pöydälle kokouksessa  |
| `"verdict-given"` | Päätös annettu              |


The `attachments` field maps each attachment filename to the corresponding definition as follows

| Field         | Type   | Description                             | Format         |
|---------------|--------|-----------------------------------------|----------------|
| `created-at`  | string | Date when attachment was created        | `"yyyy-mm-dd"` |
| `description` | string | Free-form description of the attachment |                |
| `type`        | string | Type of the attachment                  | enumeration    |

The `type` enumeration is as follows

| Value               | Finnish label |
|---------------------|---------------|
| `"verdict"`         | Päätös        |
| `"verdict-extract"` | Päätösote     |

An example `"verdict"` payload, which describes the verdict and an attachment named `"liite.pdf"`.

```json
{
  "verdict-code":  "granted",
  "decided-by":    "Ahto Simakuutio",
  "decision-date": "2022-03-08",
  "apply-date":    "2022-04-01",
  "section":       7,
  "attachments": {
    "liite.pdf": {
      "created-at":  "2022-03-08",
      "description": "Liitteen kuvaus",
      "type":        "verdict"
    }
  }
}
```


## Requirements and Restrictions

-   The user account:
    -   Must configured as a "REST API user" in Lupapiste
    -   Must have permissions to access the target application and its organization
-   The target application:
    -   Must be in a suitable state to accept verdicts. For example, submitting a verdict to a *draft* application is an error.
    -   Must not have any existing verdicts. To update an existing verdict, the old verdict must be explicitly removed before submitting the new verdict.
-   The organization:
    -   Must have the *dmCity* (or *matti*) designated as the integration partner
-   The payload:
    -   Each file referenced in the `attachments` field must have a corresponding file entry in the request payload


# Sample Requests

We use the following value as the Lupapiste application id in the examples:

```bash
LP-092-2023-90001
```

We also assume that the authentication credentials are stored in the `$username` and `$password` variables.


# Submitting Invalid Verdicts


## Submitting Without Proper Authorization

Here we omit the username and password:

```bash
curl -s "http://localhost:8000/rest/application/LP-092-2023-90001/verdict" \
     -H 'accept: application/json' \
     -H 'Content-Type: multipart/form-data' \
     -F 'verdict=</home/lassemaatta/Asiakirjat/verdict.json;type=application/json' \
     -F 'liite.pdf=@/home/lassemaatta/Asiakirjat/liite.pdf' | jq .
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


## Submitting Without a Mandatory Property

Here we omit the `apply-date` property.

```json
{
  "verdict-code":  "granted",
  "decided-by":    "Ahto Simakuutio",
  "decision-date": "2022-03-08",
  "section":       7,
  "attachments": {
    "liite.pdf": {
      "created-at":  "2022-03-08",
      "description": "Liitteen kuvaus",
      "type":        "verdict"
    }
  }
}
```

The request:

```bash
curl -s "http://localhost:8000/rest/application/LP-092-2023-90001/verdict" \
     -u $username:$password \
     -H 'accept: application/json' \
     -H 'Content-Type: multipart/form-data' \
     -F 'verdict=</home/lassemaatta/Asiakirjat/invalid_verdict.json;type=application/json' \
     -F 'liite.pdf=@/home/lassemaatta/Asiakirjat/liite.pdf' | jq .
```

The response indicates that there was a single error: the `"verdict"` field was missing the `"apply-date"` field.

```json
{
  "errors": [
    {
      "field": [
        "verdict",
        "apply-date"
      ],
      "error": "missing-required-key"
    }
  ],
  "status": 400,
  "ok": false,
  "text": "Failed to validate verdict data"
}
```


# Submitting a Valid Verdict

Here we submit the previously presented valid JSON payload.

The request:

```bash
curl -s "http://localhost:8000/rest/application/LP-092-2023-90001/verdict" \
     -u $username:$password \
     -H 'accept: application/json' \
     -H 'Content-Type: multipart/form-data' \
     -F 'verdict=</home/lassemaatta/Asiakirjat/verdict.json;type=application/json' \
     -F 'liite.pdf=@/home/lassemaatta/Asiakirjat/liite.pdf' | jq .
```

The response:

```json
{
  "status": 201,
  "ok": true,
  "verdict-id": "641bff4c374a8303cfcb8d7b"
}
```


# Submitting the Same Verdict Again

The request:

```bash
curl -s "http://localhost:8000/rest/application/LP-092-2023-90001/verdict" \
     -u $username:$password \
     -H 'accept: application/json' \
     -H 'Content-Type: multipart/form-data' \
     -F 'verdict=</home/lassemaatta/Asiakirjat/verdict.json;type=application/json' \
     -F 'liite.pdf=@/home/lassemaatta/Asiakirjat/liite.pdf' | jq .
```

The response:

```json
{
  "errors": [
    {
      "field": [
        "application-id"
      ],
      "error": "application-has-verdict"
    }
  ],
  "status": 400,
  "ok": false,
  "text": "Application error"
}
```
