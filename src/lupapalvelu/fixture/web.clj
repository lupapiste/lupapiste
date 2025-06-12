(ns lupapalvelu.fixture.web
  "Tokens and organizations for web itest."
  (:require [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.fixture.minimal :as minimal]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.system.util-components :as components]
            [monger.operators :refer :all]))

(def valid-map-token "PnuzWCOzNWKmhZUX4Z7cmV602Ispd9xaWWqarko2wBXJcVjQ")
(def valid-other-token "PnuzWCOzNWKmhZUX4Z7cmV602Ispd9xaWWqarko2wBXJcVjG")
(def expired-map-token "PnuzWCOzNWKmhZUX4Z7cmV602Ispd9xaWWqarko2wBXJcVjF")
(def valid-pena-token "eXuj2hIN9aUb0zyXXIrn25DFNmNiLmWsbMQ6IuEZsTEYSPG4")

(def tokens
  [{:id           valid-map-token
    :ip           "[0:0:0:0:0:0:0:1]"
    :created      1722884582980
    :user-id      "777777777777777777000017" ; "jussi.viranomainen@tampere.fi"
    :expires      9722895382980
    :token-type   "map-3d-token"
    :auto-consume false}
   {:id           valid-pena-token
    :ip           "[0:0:0:0:0:0:0:1]"
    :created      1722884582980
    :user-id      "777777777777777777000020" ; Pena
    :expires      9722895382980
    :token-type   "map-3d-token"
    :auto-consume false}
   {:id           valid-other-token
    :ip           "[0:0:0:0:0:0:0:1]"
    :created      1722884582980
    :user-id      "777777777777777777000017" ; "jussi.viranomainen@tampere.fi"
    :expires      9722895382980
    :auto-consume false}
   {:id           expired-map-token
    :ip           "[0:0:0:0:0:0:0:1]"
    :created      1722884582980
    :user-id      "777777777777777777000017" ; "jussi.viranomainen@tampere.fi"
    :expires      1722884582981
    :token-type   "map-3d-token"
    :auto-consume false}])

(defmethod fixture/fixture :web
  [_ system]
  (mongo/clear!)
  (mongo/insert-batch :users minimal/users)
  (mongo/insert-batch :companies minimal/companies)
  (mongo/insert-batch :organizations (minimal/organizations {:http-port (components/system->http-port system)}))
  (mongo/insert-batch :token tokens))
