# Dynasty 10 integration

## Overview

Dynasty 10 (D10) is a case management system. Lupapiste integrates to D10 using a HTTP REST API. The API consists of an authentication server, which provides tokens, and the actual service API, which handles cases and attachments etc.

## Local testing

First, you might want to enable schema checking (`(sc/set-fn-validation! true)`).

Create a configuration which points to the service API (`:host`) and the authentication API (`:auth`). The API URIs, the `organization`, the `client-id`, and `client-secret` values should be provided by the Dynasty admins.

Note that you most likely need to use the real server names and not e.g. IP addresses. The token created by the authentication server includes the `:host` key sent by us as the issuer. The service API will only accept tokens issued by the correct server. If you access the servers through a VPN and/or port-forwarding, you may need to work around this by hacking `/etc/hosts`.

```clojure
(def server-config {:host         "https://some.dynasty.test.server.fi"
                    :organization "0"
                    :auth         {:host          "https://auth.some.dynasty.test.server.fi"
                                   :client-id     "IntegrationApiTest"
                                   :client-secret "some secret key"}})
```

Create a client.

```clojure
(require '[lupapalvelu.backing-system.dynasty.api.client-impl :as client])

(def client (client/make-client))
```

First query endpoints, which do not require a token. If something goes wrong, check your connections (e.g. are you using HTTP or HTTPS?).

```clojure
(require '[lupapalvelu.backing-system.dynasty.api.info :as info])

(info/version client server-config) ;; => Returns data about the API version

(info/dynasty-info client server-config) ;; => Returns data about the Dynasty version
```

Once the communication works, query endpoints which require the token. If something goes wrong, try to figure out if a) did you get a token from the authentication API and b) did the service API accept the token. The token seems to be a JWT so you can try do decode it.

```clojure
(require '[lupapalvelu.backing-system.dynasty.api.document :as document])

(document/workflows client server-config) ;; => Returns the document workflows
```

Afterwards you may use the other APIs to create cases, add contacts to cases etc.
