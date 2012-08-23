(ns lupapalvelu.security
  (:use monger.operators)
  (:require [lupapalvelu.mongo :as mongo]))

(defn- non-private [user]
  (dissoc user :private))

(defn login [username password]
  "returns non-private information of first user with the username and password"
  (non-private (first (mongo/select mongo/users {:username username 
                                                 "private.password" password}))))

(defn login-with-apikey [apikey]
  "returns non-private information of first user with the apikey"
  (and apikey (non-private (first (mongo/select mongo/users {"private.apikey" apikey})))))
