(ns lupapalvelu.cookie
  (:require [sade.env :as env])
  (:import org.apache.http.cookie.Cookie))

(env/in-dev
  (defn ->lupa-cookie [name value]
    (proxy [Cookie] []
      (getName [] name)
      (getValue [] value)
      (getDomain [] (get (re-matches #"(http(s)?://)([a-z0-9-\.]+)(:\d+)?" (env/server-address)) 3))
      (getPath [] "/")
      (isSecure [] false)
      (getVersion [] 2)
      (isExpired [_] false))))

