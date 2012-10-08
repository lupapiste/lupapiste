(ns lupapalvelu.vetuma
  (:use [clojure.string :only [join split]]
        [clojure.set :only [rename-keys]]
        [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [clj-time.local :only [local-now]]
        [hiccup.form]
        [lupapalvelu.log])
  (:require [digest]
            [noir.request :as request]
            [noir.session :as session]
            [clj-time.core :as time]
            [clj-time.format :as format]))

;;
;; Configuration
;;

(def session-key "vetuma")
(def request-mac-keys  [:rcvid :appid :timestmp :so :solist :type :au :lg :returl :canurl :errurl :ap :extradata :appname :trid])
(def response-mac-keys [:rcvid :timestmp :so :userid :lg :returl :canurl :errurl :subjectdata :extradata :status :trid :vtjdata])

(def constants 
  {:url       "https://testitunnistus.suomi.fi/VETUMALogin/app"
   :rcvid     "***REMOVED***1"
   :appid     "VETUMA-APP2"
   :so        "6"
   :solist    "6,11"
   :type      "LOGIN"
   :au        "EXTAUTH"
   :lg        "fi"
   :returl    "https://localhost:8443/vetuma/return"
   :canurl    "https://localhost:8443/vetuma/cancel"
   :errurl    "https://localhost:8443/vetuma/error"
   :ap        "***REMOVED***"
   :appname   "Lupapiste"
   :extradata "VTJTT=T1" 
   :key       "***REMOVED***"})

;;
;; Helpers
;;

(defn generate-stamp [] (apply str (take 20 (repeatedly #(rand-int 10)))))

(def time-format (format/formatter-local "yyyyMMddHHmmssSSS"))

(defn timestamp [] (format/unparse time-format (local-now)))

(defn keys-as-strings [m] (into {} (for [[k v] m] [(.toUpperCase (name k)) v])))
(defn keys-as-keywords [m] (into {} (for [[k v] m] [(keyword (.toLowerCase k)) v])))

(defn logged [m] (info "%s" (str m)) m)

;;
;; Mac
;;

(defn secret [{rcvid :rcvid key :key}] (str rcvid "-" key))

(defn mac [data]  (-> data digest/sha-256 .toUpperCase))

(defn mac-of [m keys]
  (->
    (for [k keys] (k m))
    vec
    (conj (secret m))
    (conj "")
    (->> (join "&"))
    mac))

(defn with-mac [m]
  (merge m {:mac (mac-of m request-mac-keys)}))

(defn mac-verified [m]
  (if (= (:mac m) (mac-of m response-mac-keys)) m {}))

;;
;; response parsing
;;

(defn- extract-subject [m]
  (-> m
    :subjectdata
    (split #", ")
    (->> (map #(split % #"=")))
    (->> (into {}))
    keys-as-keywords
    (rename-keys {:etunimi :firstName})
    (rename-keys {:sukunimi :lastName})))

(defn- extract-person-id [m] (:userid m))

(defn- extract-response [m]
  (assoc (extract-subject m) :personId (extract-person-id m)))

;;
;; Request & Response mapping to clojure
;;

(defn request-data [id]
  (-> constants 
    (assoc :trid id)
    (assoc :timestmp (timestamp))
    with-mac
    keys-as-strings))

(defn response-data [m]
  (-> m
    keys-as-keywords
    (assoc :key (:key constants))
    mac-verified
    (dissoc :key)))

;;
;; Web stuff
;;

(defn field [[k v]]
  (hidden-field k v))

(defpage "/vetuma" []
  (let [stamp (generate-stamp)
        data  (request-data stamp)]
    (session/put! session-key stamp)
    (html
      (form-to [:post (:url constants)]
        (map field data)
        (submit-button "submit")))))

(defpage [:post "/vetuma/:status"] {status :status}
  (-> 
    (:form-params (request/ring-request)) 
    logged
    response-data
    extract-response
    str))
  