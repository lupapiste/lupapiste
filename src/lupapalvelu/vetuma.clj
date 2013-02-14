(ns lupapalvelu.vetuma
  (:use [clojure.set :only [rename-keys]]
        [noir.core :only [defpage]]
        [noir.response :only [redirect status json]]
        [hiccup.core :only [html]]
        [monger.operators]
        [clj-time.local :only [local-now]]
        [hiccup.form]
        [clojure.tools.logging])
  (:require [digest]
            [clojure.string :as string]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.vtj :as vtj]
            [noir.request :as request]
            [noir.session :as session]
            [clj-time.core :as time]
            [clj-time.format :as format]))

;;
;; Configuration
;;

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
   :returl    "{host}/vetuma"
   :canurl    "{host}/vetuma/cancel"
   :errurl    "{host}/vetuma/error"
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

(defn apply-template
  "changes all variables in braces {} with keywords with same name.
   for example (apply-template \"hi {name}\" {:name \"Teppo\"}) returns \"hi Teppo\""
  [v m] (string/replace v #"\{(\w+)\}" (fn [[_ word]] (or (m (keyword word)) ""))))

(defn apply-templates
  "runs apply-template on all values, using the map as input"
  [m] (into {} (for [[k v] m] [k (apply-template v m)])))

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
    (->> (string/join "&"))
    mac))

(defn- with-mac [m] (merge m {:mac (mac-of m request-mac-keys)}))
(defn- mac-verified [m] (if (= (:mac m) (mac-of m response-mac-keys)) m {}))

;;
;; response parsing
;;

(defn- extract-subjectdata [{s :subjectdata}]
  (-> s
    (string/split #", ")
    (->> (map #(string/split % #"=")))
    (->> (into {}))
    keys-as-keywords
    (rename-keys {:etunimi :firstname})
    (rename-keys {:sukunimi :lastname})))

(defn- extract-vtjdata [{:keys [vtjdata]}]
  (vtj/extract-vtj vtjdata))

(defn- extract-userid [{s :extradata}]
  {:userid (last (string/split s #"="))})

(defn- extract-request-id [{id :trid}]
  {:stamp id})

(defn- user-extracted [m]
  (merge (extract-subjectdata m)
         (extract-vtjdata m)
         (extract-userid m)
         (extract-request-id m)))

;;
;; Request & Response mapping to clojure
;;

(defn- request-data [host]
  (-> constants
    (assoc :trid (generate-stamp))
    (assoc :timestmp (timestamp))
    (assoc :host  host)
    apply-templates
    with-mac
    (dissoc :key)
    keys-as-strings))

(defn- parsed [m]
  (-> m
    keys-as-keywords
    (assoc :key (:key constants))
    mac-verified
    (dissoc :key)))

;;
;; Web stuff
;;

(defn session-id [] (get-in (request/ring-request) [:cookies "ring-session" :value]))

(defn- field [[k v]]
  (hidden-field k v))

(defn- non-local? [paths] (some #(not= -1 (.indexOf % ":")) (vals paths)))

(defn host-and-ssl-port
  "returns host with port changed from 8000 to 8443. Shitty crap."
  [host] (string/replace host #":8000" ":8443"))

(defn host
  ([] (host :current))
  ([mode]
    (let [request (request/ring-request)
          scheme  (name (:scheme request))
          hostie  (get-in request [:headers "host"])]
      (case mode
        :current (str scheme "://" hostie)
        :secure  (if (= scheme "https")
                   (host :current)
                   (str "https://" (host-and-ssl-port hostie)))))))

(defpage "/vetuma" {:keys [success, cancel, error] :or {success "" cancel "" error ""} :as data}
  (let [paths     {:success success :error error :cancel cancel}
        sessionid (session-id)]
    (if (non-local? paths)
      (status 400 (format "invalid return paths: %s" paths))
      (do
        (mongo/update-one-and-return :vetuma {:sessionid sessionid} {:sessionid sessionid :paths paths} :upsert true)
        (html
          (form-to [:post (:url constants)]
                   (map field (request-data (host :secure)))
                   (submit-button "submit")))))))

(defpage [:post "/vetuma"] []
  (let [user (-> (:form-params (request/ring-request))
               logged
               parsed
               user-extracted
               logged)
        data (mongo/update-one-and-return :vetuma {:sessionid (session-id)} {$set {:user user}})
        uri  (get-in data [:paths :success])]
    (redirect uri)))

(defpage [:post "/vetuma/:status"] {status :status}
  (let [data       (mongo/select-one :vetuma {:sessionid (session-id)})
        return-uri (get-in data [:paths (keyword status)])]
    (redirect return-uri)))

(defpage "/vetuma/user" []
  (let [data (mongo/select-one :vetuma {:sessionid (session-id)})
        user (-> data :user)]
    (json user)))

(defpage "/vetuma/stamp/:stamp" {:keys [stamp]}
  (let [data (mongo/select-one :vetuma {:user.stamp stamp})
        user (-> data :user)
        id   (:id data)]
    (mongo/remove-many :vetuma {:_id id})
    (json user)))
