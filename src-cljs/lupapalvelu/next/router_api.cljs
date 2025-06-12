(ns lupapalvelu.next.router-api
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub :route/match
  (fn [db]
    (get db :route/match)))

;; https://cljdoc.org/d/metosin/reitit/0.7.1/doc/coercion/plumatic-schema#plumatic-schema-coercion
(re-frame/reg-sub :route/parameters
  :<- [:route/match]
  :-> :parameters)

(re-frame/reg-sub :route/query-parameters
  :<- [:route/parameters]
  :-> :query)

(re-frame/reg-sub :route/path-parameters
  :<- [:route/parameters]
  :-> :path)

(defn db->query-parameters
  [db]
  (-> db :route/match :parameters :query))

(defn db->path-parameters
  [db]
  (-> db :route/match :parameters :path))

(defn path-parameter [db key]
  (get (db->path-parameters db) key))

(defmulti route-init-fn
  "Return the route-specific init-fn"
  {:arglists '([route-name])}
  identity)

(defmethod route-init-fn :default
  [route-name]
  (throw (ex-info "no route-init-fn specified" {:route route-name})))
