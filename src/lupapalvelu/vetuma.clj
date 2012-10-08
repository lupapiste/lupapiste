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
(def request-mac-keys  [:rcvid :appid :timestmp :so :solist :type :au :lg :returl :canurl :errurl :ap :appname :trid])
(def response-mac-keys [:rcvid :timestmp :so :userid :lg :returl :canurl :errurl :subjectdata :extradata :status :trid])

(def constants 
  {:url     "https://testitunnistus.suomi.fi/VETUMALogin/app"
   :rcvid   "***REMOVED***1"
   :appid   "VETUMA-APP2"
   :so      "6"
   :solist  "6,11"
   :type    "LOGIN"
   :au      "EXTAUTH"
   :lg      "fi"
   :returl  "https://localhost:8443/vetuma/return"
   :canurl  "https://localhost:8443/vetuma/cancel"
   :errurl  "https://localhost:8443/vetuma/error"
   :ap      "***REMOVED***"
   :appname "Lupapiste"
   :key     "***REMOVED***"})

;;
;; Helpers
;;

(defn generate-stamp [] (apply str (take 20 (repeatedly #(rand-int 10)))))

(def time-format (format/formatter-local "yyyyMMddHHmmssSSS"))

(defn timestamp [] (format/unparse time-format (local-now)))

(defn keys-as-strings [m] (into {} (for [[k v] m] [(.toUpperCase (name k)) v])))
(defn keys-as-keywords [m] (into {} (for [[k v] m] [(keyword (.toLowerCase k)) v])))

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
;; Beef
;;

(defn request-data [id]
  (-> 
    constants 
    (assoc :trid id)
    (assoc :timestmp (timestamp))
    with-mac
    keys-as-strings))

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
  (str (->
         (request/ring-request)
         :form-params
         keys-as-keywords
         (assoc :key (:key constants))
         mac-verified
         (dissoc :key))))