(ns lupapalvelu.cookie
  (:require [clj-http.cookies :as cookies]
            [lupapalvelu.mongo :as mongo]
            [sade.env :as env])
  (:import org.apache.http.cookie.Cookie))

(env/in-dev
  (defn ->lupa-cookie ^Cookie [name value]
    (proxy [Cookie] []
      (getName [] name)
      (getValue [] value)
      (getDomain [] (get (re-matches #"(http(s)?://)([a-z0-9-\.]+)(:\d+)?" (env/server-address)) 3))
      (getPath [] "/")
      (isSecure [] false)
      (getVersion [] 2)
      (isExpired [_] false))))

(defn add-mongo-cookie
  "Adds test database cookie to `options` if in development environment. The cookie is used
  to allow different threads to share the same test database."
  [options]
  (merge options
         (env/in-dev
           {:cookie-store
            (doto (cookies/cookie-store)
              (cookies/add-cookie (->lupa-cookie "test_db_name" (mongo/current-db-name))))})))
