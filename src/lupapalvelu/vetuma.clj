(ns lupapalvelu.vetuma
  (:use [clojure.string :only [join]]
        [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [clj-time.local :only [local-now]]
        [hiccup.form])
  (:require [digest]
            [noir.request :as request]
            [noir.session :as session]
            [clj-time.core :as time]
            [clj-time.format :as format]))

;;
;; Configuration
;;

(def session-key "vetuma")

(def mac-keys [:rcvid :appid :timestmp :so :solist :type :au :lg :returl :canurl :errurl :ap :trid])

(def constants 
  {:url    "https://testitunnistus.suomi.fi/VETUMALogin/app"
   :rcvid  "***REMOVED***1"
   :appid  "VETUMA-APP2"
   :so     "6"
   :solist "6,11"
   :type   "LOGIN"
   :au     "EXTAUTH"
   :lg     "fi"
   :returl "https://localhost:8443/vetuma/return"
   :canurl "https://localhost:8443/vetuma/cancel"
   :errurl "https://localhost:8443/vetuma/error"
   :ap     "***REMOVED***"
   :key    "***REMOVED***"})

;;
;; Helpers
;;

(defn url [] (:url constants))

(defn generate-stamp [] (apply str (take 20 (repeatedly #(rand-int 10)))))

(def time-format (format/formatter-local "yyyyMMddHHmmssSSS"))

(defn timestamp [] (format/unparse time-format (local-now)))

(defn string-keys [m] (into {} (for [[k v] m] [(.toUpperCase (name k)) v])))

;;
;; Mac
;;

(defn secret [{rcvid :rcvid key :key}] (str rcvid "-" key))

(defn mac [data]  (-> data digest/sha-256 .toUpperCase))

(defn mac-of [m]
  (->
    (for [k mac-keys] (k m))
    vec
    (conj (secret m))
    (conj "")
    (->> (join "&"))
    mac))

(defn with-mac [m]
  (merge m {:mac (mac-of m)}))

;;
;; Beef
;;

(defn generate-data [id]
  (-> 
    constants 
    (assoc :trid id)
    (assoc :timestmp (timestamp))
    with-mac
    string-keys))

;;
;; Web stuff
;;

(defn field [[k v]]
  (hidden-field k v))

(defpage "/vetuma" []
  (let [stamp (generate-stamp)
        data  (generate-data stamp)]
    (session/put! session-key stamp)
    (html 
      (form-to [:post (url)]
        (map field data)
        (submit-button "submit")))))

(defpage [:post "/vetuma/:status"] {status :status}
  (let [m (:form-params (request/ring-request))]
    (str status " --> " m)))

(def ret {"RCVID" "***REMOVED***1"
          "USERID" "210281-9988"
          "ERRURL" "https://localhost:8443/vetuma/error"
          "RETURL" "https://localhost:8443/vetuma/return"
          "MAC" "CB25FB2CAF6CF7CB2577B053C1604D3F4174A225E94F2551CAA2C9F2669B7CEB"
          "TIMESTMP" "20121004131351353"
          "STATUS" "SUCCESSFUL"
          "SUBJECTDATA" "ETUNIMI=PORTAALIA, SUKUNIMI=TESTAA"
          "TRID" "58775279672526028038"
          "EXTRADATA" "HETU=210281-9988"
          "LG" "fi"
          "SO" "62"
          "CANURL" "https://localhost:8443/vetuma/cancel"})


