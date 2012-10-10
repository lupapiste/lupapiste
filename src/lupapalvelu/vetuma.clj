(ns lupapalvelu.vetuma
  (:use [clojure.string :only [join split]]
        [clojure.set :only [rename-keys]]
        [noir.core :only [defpage]]
        [noir.response :only [redirect status json]]
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

(def session-keys {:path "vetuma-return-path"
                   :user "vetuma-user"})

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
   :extradata "VTJTT=VTJ-VETUMA-Perus" 
   :key       "***REMOVED***"})

;;
;; Helpers
;;

(def time-format (format/formatter-local "yyyyMMddHHmmssSSS"))

(defn- timestamp [] (format/unparse time-format (local-now)))

(defn- generate-stamp [] (apply str (take 20 (repeatedly #(rand-int 10)))))

(defn- keys-as [f m] (into {} (for [[k v] m] [(f k) v])))
(defn- keys-as-strings [m] (keys-as #(.toUpperCase (name %)) m))
(defn- keys-as-keywords [m] (keys-as #(keyword (.toLowerCase %)) m))

(defn- logged [m] (info "%s" (str m)) m)

;;
;; Mac
;;

(defn- secret [{rcvid :rcvid key :key}] (str rcvid "-" key))

(defn- mac [data]  (-> data digest/sha-256 .toUpperCase))

(defn- mac-of [m keys]
  (->
    (for [k keys] (k m))
    vec
    (conj (secret m))
    (conj "")
    (->> (join "&"))
    mac))

(defn- with-mac [m]
  (merge m {:mac (mac-of m request-mac-keys)}))

(defn- mac-verified [m]
  (if (= (:mac m) (mac-of m response-mac-keys)) m {}))

;;
;; response parsing
;;

(defn- extract-subjectdata [{s :subjectdata}]
  (-> s
    (split #", ")
    (->> (map #(split % #"=")))
    (->> (into {}))
    keys-as-keywords
    (rename-keys {:etunimi :firstName})
    (rename-keys {:sukunimi :lastName})))

(defn- extract-userid [{s :extradata}] 
  {:userid (last (split s #"="))})

(defn- extract-request-id [{id :trid}]
  {:stamp id})

(defn- user-extracted [m]
  (merge (extract-subjectdata m)
         (extract-userid m)
         (extract-request-id m)))

;;
;; Request & Response mapping to clojure
;;

(defn- request-data []
  (-> constants 
    (assoc :trid (generate-stamp))
    (assoc :timestmp (timestamp))
    with-mac
    keys-as-strings))

(defn- parsed [m]
  (-> m
    keys-as-keywords
    (assoc :key (:key constants))
    mac-verified
    (dissoc :key)))

;;
;; Persistent storage
;;

(defonce mem (atom {}))

;;
;; Web stuff 
;;

(defn- field [[k v]]
  (hidden-field k v))

(defn- local? [s] (= -1 (.indexOf s ":")))

(defpage "/vetuma" {:keys [path]}
  (if (not (local? path))
    (status 400 (format "invalid return path: %s" path))
    (do
      (session/put! (:path session-keys) path)
      (html
        (form-to [:post (:url constants)]
          (map field (request-data))
          (submit-button "submit"))))))

(defpage [:post "/vetuma/:status"] {status :status}
  (let [user (-> (:form-params (request/ring-request))
               logged
               parsed
               user-extracted
               logged)]
    (session/put! (:user session-keys) user)
    (println user)
    (swap! mem assoc (:stamp user) user)
    (println @mem)
    (redirect (session/get! (:path session-keys)))))

(defpage "/vetuma/user" []
  (json (session/get (:user session-keys))))

(defpage "/vetuma/stamp/:stamp" {:keys [stamp]}
  (println stamp)
  (let [user (@mem stamp)]
    (swap! mem dissoc stamp)
    (json user)))