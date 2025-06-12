(ns lupapalvelu.backing-system.dynasty.api.client-proto
  "A simple wrapper for the HTTP communication with Dynasty"
  (:require [sade.schemas :as ssc]
            [schema-tools.core :as st]
            [schema.core :as sc]))

(defprotocol DynastyClient
  (-get [this opts])
  (-post [this opts]))

(sc/defschema Client (sc/protocol DynastyClient))

(sc/defschema TokenOptions
  {:url           ssc/NonBlankStr
   :client-id     ssc/NonBlankStr
   :client-secret ssc/NonBlankStr})

(sc/defschema GetOptions
  (st/merge
    {:url ssc/NonBlankStr}
    (st/optional-keys
      {:auth    TokenOptions
       :headers {sc/Keyword ssc/NonBlankStr}})))

(sc/defn get!
  {:always-validate true}
  [client :- Client
   opts :- GetOptions]
  (-get client opts))

(sc/defschema PostOptions
  (st/merge
    GetOptions
    (st/optional-keys
      {:form-params {sc/Keyword sc/Any}
       :multipart   [(st/merge
                       {:name    ssc/NonBlankStr
                        :content sc/Any}
                       (st/optional-keys
                         {:part-name ssc/NonBlankStr
                          :mime-type ssc/NonBlankStr
                          :encoding  ssc/NonBlankStr}))]})))

(sc/defn post!
  {:always-validate true}
  [client :- Client
   opts :- PostOptions]
  (-post client  opts))
