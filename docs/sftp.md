# SFTP

Lupapiste provides an SFTP access for outside stakeholders. The access
is mostly (solely?) used as an integration mechanism between Lupapiste
and other systems. Since the server implementatations and use cases
vary, this document summarizes the current status.

Lupapiste has also some (invoicing) functionality that accesses SFTP
servers on other systems, but that is out of the scope for this
document.

## Use Cases, Users and Folders

Different SFTP use cases have different mechanism on resolving the
correct SFTP user and the target folder.

### Backing system

Lupapiste writes KuntaGML messages (and attachments) that the backing
systems then fetches. Note that the integration is purely one-way:
Lupapiste in this case never reads anything from the server. Verdicts,
reviews, etc. are queried from the backing system via http.

Backing system integration is configured by the `krysp` field in the
organization document/schema. The field is an permit type -
configuration map. See `organization.clj` for details.

The application can be “sent” to the backing system (written to the SFTP
server) if its permit type matches a key in the organization `krysp`
field. The SFTP username is in the configuration (e.g.,
`krysp.R.ftpUser`) and the target (leaf) folder is determined by the
application permit type (e.g., R → rakennus).

### Case management

Case management (asianhallinta) systems differ from (other) backing
systems in so many ways that they can be considered as a separate use
case. In case management integration Lupapiste both writes to and reads
from its SFTP server.

Case management integration is configured by `caseManagement` field for
an organization `scope`. Thus, an application uses case management
integration if the corresponding scope (determined by the application
municipality and permit type) has case management configured. The SFTP
username is part of the configuration and the folder structure is fixed.

ELY integration (statement requests and statements) uses case management
integration mechanism. However, the SFTP username is determined by the
global `ely.sftp-user` environment property.

### Invoicing

The invoicing authority (biller role) can write transfer batches to the
local SFTP server. Since this is only one of the integration
alternatives, it must be selected for organization by setting
`invoicing-config.local-sftp?` to `true`. The SFTP username is either
explicitly set via `invoicing-config.local-sftp-user` or (as a fallback)
the first `ftpUser` for any invoicing-enabled scope is used.

## Servers

There are two different SFTP server implementations that are both used
in Lupapiste. For each integration, the server is selected according to
the organization `sftpType` value. The value is either `legacy`
(default) or `gcs` (Google Cloud Storage).

The differences between the two are purely implementation details. For
authorities and SFTP users (integration counterparts) both systems work
exactly the same. Only the server names differ.

### Legacy

As the name implies, the legacy is the old-school implementation. SFTP
accounts and folders are creted by
[lupapiste-chef](https://github.com/cloudpermit/lupapiste-chef) and the
server is accessible to the Lupapiste code directly via file system. The
objective is to migrate the legacy accounts to GCS in the future.

### Google Cloud Storage

SFTP on top of Google Cloud Storage (GCS-SFTP), where instead of file
system a GCS bucket is used. The actual SFTP server functionality is
provided by [FileMage](https://www.filemage.io) and the accounts are
created by [gcp-infra](https://github.com/cloudpermit/gcp-infra).

## UI

The organization SFTP can be configured by Admin admin in the
organization view. In the editor, `sftpType` can be selected and the
SFTP accounts bound to the correct use cases and permit types.

**Note:** The SFTP details should not be manipulated directly in mongo,
since one aspect of the configuration is to make sure that the correct
SFTP folders exist. For legacy SFTP, Chef creates correct folders with
correct permissions and the configuration UI simply checks that the
folders exist. For GCS-SFTP the configuration UI creates the correct
folders.

## Environment

SFTP functionality related properties. See various environment-specific
properties file for details (e.g., `dev.properties`).

| Property                 | Type   | Comment                                                                  |
|--------------------------|--------|--------------------------------------------------------------------------|
| `ely.gcs-sftp?`          | Bool   | If `true`, ELY integration uses GCS-SFTP (default `false`)               |
| `ely.sftp-user`          | String | SFTP username for ELY integration.                                       |
| `feature.gcs`            | Bool   | **MUST** be `true`, for GCS-SFTP to work.                                |
| `fileserver-address`     | URL    | Prefix (`sftp://sfp-server`) for links in legacy SFTP KuntaGML messages. |
| `gcs.fileserver-address` | URL    | Prefix (`sftp://sfp-server`) for links in GCS-SFTP KuntaGML messages.    |
| `gcs.sftp-bucket`        | String | GCS bucket name for SFTP data.                                           |
