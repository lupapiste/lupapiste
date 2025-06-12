# AsiointiAPI

## API Implementation

The OpenAPI JSON spec is hosted at http://localhost:8000/asiointi/openapi.json

The Swagger UI for the API is hosted at http://localhost:8000/asiointi/docs/

### Notification API

The primary API does not describe notifications, as those are sent from Lupapiste to the registered backend. Instead, a separate API is provided to act as documentation.

The OpenAPI JSON spec is hosted at http://localhost:8000/asiointi-notifications/openapi.json

The Swagger UI for the API is hosted at http://localhost:8000/asiointi-notifications/docs/

## Local Development Settings

### Feature Flags

`feature.fetch-ryhti-plt`: Enables fetching of permanent building identifiers (PLT) from Ryhti when a new application is created. Also adds checks for enforcing that the PLT has been fetched, before certain actions are allowed (ie. submitting an application).

`feature.asiointi-api`: Enables the AsiointiAPI HTTP routes and notification mechanism.

`feature.generate-random-plt`: Generate a random (UUID) PLT locally, instead of fetching one from Ryhti. Applicable only when `feature.fetch-ryhti-plt` is also enabled. Effectively this will cause all organizations to produce PLTs.

### Organization Configuration

Organizations contain the `:ryhti-api-configuration` value, which specify which municipalities within the organization support fetching PLTs from Ryhti. This can be configured by the admin.

In development mode the configuration is ignored; we will always create a random UUID as the PLT.

If `feature.generate-random-plt` is enabled, we will first check the organization configuration. If the configuration exists, we will try to use it to fetch the PLT. If the configuration is missing, we will fallback to generating a random UUID as the PLT.

Also, naturally AsiointiAPI only serves applications, which follow the Ryhti data model. Therefore make sure `:ryhti-data-2026-start-ts` is set.

### Application

For an application to be applicable to AsiointiAPI, it must at minimum
- have a suitable permit type (see `:asiointi-api-supported?`)
- follow the Ryhti data model (see `:ryhti-data-2026`)
- have a suitable (Ryhti) control case (see `:control-case`)

### Credentials

The `minimal` fixture contains the user `sipoo_asiointiapi`, which has write permissions to `753-R`.

Alternatively, the admin may create new AsiointiAPI credentials in the "AsiointiAPI" -view.

### Receiving Notifications

The `web.clj` specifies a development time endpoint for receiving notifications (`/dev/asiointi/notifications`). This endpoint mimics a backend system, which receives notifications from Lupapiste and acknowledges them.  The endpoint has an optional `failure-rate` parameter if you want to simulate occasional failures.

### Registering for Notifications

Use the Swagger UI (link above) to manage registrations. Each registration targets a specific organization.

Example payload for a create request:

```json
{
  "registration": {
    "target": {
      "url": "http://localhost:8000/dev/asiointi/notifications?failure-rate=10"
    }
  }
}
```

## Receiving Notifications Locally

Suppose you wish to verify how notifications (and the AsiointiAPI) work using an application.

Pre-requisites (see above):
- The relevant feature flags are enabled
- When `feature.generate-random-plt` is not set: The target organization includes the necessary Ryhti credentials for the target municipality
- The target organization creates Ryhti compliant applications
- You have an AsiointiAPI user account with writer permissions
- You have registered a notification registration (e.g. for `http://localhost:8000/dev/asiointi/notifications`)

Create a new application. In the logs you should see something like

```
INFO lupapalvelu.ryhti.permanent-id - Generating random permanent permit identifier..
INFO lupapalvelu.ryhti.permanent-id - Received Ryhti Permanent Permit Identifier, 6a861464-10a2-4e98-8e03-11ef7c437975
```

This indicates that we have simulated the fetching of a PLT for the application.

You should be able to submit the application, because it has a valid PLT.

Now submit the application. The state change in the application should trigger a notification. In the logs you should see something like

```
INFO lupapalvelu.asiointi.notifications.notify - Sending notification to http://localhost:8000/dev/asiointi/notifications?failure-rate=50
INFO lupapalvelu.web - Received AsiointiAPI notification: {:application-id "LP-753-2026-90058", :registration-id "6995a15f8ecc2f56a21278b6", :organization-id "753-R", :permanent-permit-id "6a861464-10a2-4e98-8e03-11ef7c437975", :details {:old-state "draft", :new-state "submitted"}, :notification-type "application-state-change", :created-at "2026-03-11T09:07:31+02:00", :notification-id "69b114b33f727824ffa7f0a1"}
```

This indicates that the notification was generated and received by the mock endpoint. If you configured the `failure-rate` option, the notification is sometimes declined. In such a case Lupapiste should retry delivery until the delivery succeeds.

Also, you can fetch the permit information from AsiointiAPI with the PLT. Use either the Swagger UI or `curl`

```sh
curl -X 'GET'  'http://localhost:8000/asiointi/api/753-R/app/6a861464-10a2-4e98-8e03-11ef7c437975' -H 'accept: application/json'
```

should return something like

```json
{
  "locationPermitObject": [],
  "extensionDecision": [],
  "dateOfInitiation": "2026-03-11",
  "lifeCycleState": "http://uri.suomi.fi/codelist/rytj/rakvalv-asian-elinkaaren-tila/code/2",
  "permanentPermitIdentifier": "6a861464-10a2-4e98-8e03-11ef7c437975",
  "buildingPermitApplication": [
    {
      "permitApplicationKey": "dbb3e2f3-d113-4c3a-ba8d-8931ea491aa5",
      "lifeCycleState": "http://uri.suomi.fi/codelist/rytj/lupahak-elinkaaren-tila/code/2",
      "applicationContent": "asd",
      "dateOfReception": "2026-03-11",
      ...
```
