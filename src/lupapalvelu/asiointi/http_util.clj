(ns lupapalvelu.asiointi.http-util
  "Common utilities for generating HTTP error responses.

  TODO: Generate RFC-9457 compatible error responses?"
  (:require [ring.util.response :as resp]))

(defn bad-request
  [& errors]
  (-> (resp/response (cond-> {:status 400
                              :title  "Bad request"}
                       (seq errors) (assoc :errors errors)))
      (resp/status 400)))

(def unauthorized (-> (resp/response "Unauthorized")
                      (resp/header "WWW-Authenticate" "Basic realm=\"Lupapiste\"")
                      (resp/status 401)))

(def forbidden (-> (resp/response "Forbidden")
                   (resp/status 403)))

(def not-found (-> (resp/response "Not found")
                   (resp/status 404)))

(def internal-error (-> (resp/response "Internal server error")
                        (resp/status 500)))

(def ErrorResponseSchema [:map
                          [:status :int]
                          [:title :string]
                          [:errors {:optional true}
                           [:sequential
                            [:map
                             [:detail :string]
                             [:pointer :string]]]]])

(def common-errors
  {400 {:description "Bad request"
        :content     {"application/json" {:schema ErrorResponseSchema}}}
   401 {:description "Authentication is missing or invalid"
        ;; This header spec don't seem to show up in the `openapi.json` output
        :headers     {"WWW-Authenticate" {:schema :string}}}
   403 {:description "Forbidden"}
   404 {:description "Not found"}
   500 {:description "Internal server error"}})
