(ns lupapalvelu.vetuma
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error errorf fatal]]
            [clojure.set :refer [rename-keys]]
            [noir.core :refer [defpage]]
            [noir.request :as request]
            [noir.response :as response]
            [hiccup.core :refer [html]]
            [hiccup.form :as form]
            [monger.operators :refer :all]
            [clj-time.local :refer [local-now]]
            [clj-time.format :as format]
            [pandect.core :as pandect]
            [sade.core :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.vtj :as vtj]))

;;
;; Configuration
;;

(def encoding "ISO-8859-1")

(def request-mac-keys  [:rcvid :appid :timestmp :so :solist :type :au :lg :returl :canurl :errurl :ap :extradata :appname :trid])
(def response-mac-keys [:rcvid :timestmp :so :userid :lg :returl :canurl :errurl :subjectdata :extradata :status :trid :vtjdata])

(defn config []
  {:url       (env/value :vetuma :url)
   :rcvid     (env/value :vetuma :rcvid)
   :appid     "Lupapiste"
   :so        "6"
   :solist    "6" #_"6,11"
   :type      "LOGIN"
   :au        "EXTAUTH"
   :lg        "fi"
   :returl    "{host}/api/vetuma"
   :canurl    "{host}/api/vetuma/cancel"
   :errurl    "{host}/api/vetuma/error"
   :ap        (env/value :vetuma :ap)
   :appname   "Lupapiste"
   :extradata "VTJTT=VTJ-VETUMA-Perus"
   :key       (env/value :vetuma :key)})

;; log error for all missing env keys.
(doseq [[k v] (config)]
  (when (nil? v) (errorf "missing key '%s' value from property file" (name k))))

;;
;; Helpers
;;

(def time-format (format/formatter-local "yyyyMMddHHmmssSSS"))

(defn- timestamp [] (format/unparse time-format (local-now)))

(defn- generate-stamp [] (apply str (repeatedly 20 #(rand-int 10))))

(defn- keys-as [f m] (into {} (for [[k v] m] [(f k) v])))
(defn- keys-as-strings [m] (keys-as #(.toUpperCase (name %)) m))
(defn- keys-as-keywords [m] (keys-as #(keyword (.toLowerCase %)) m))

(defn- logged [m]
  (info (-> m (dissoc "ERRURL") (dissoc :errurl) str))
  m)

(defn apply-template
  "changes all variables in braces {} with keywords with same name.
   for example (apply-template \"hi {name}\" {:name \"Teppo\"}) returns \"hi Teppo\""
  [v m] (ss/replace v #"\{(\w+)\}" (fn [[_ word]] (or (m (keyword word)) ""))))

(defn apply-templates
  "runs apply-template on all values, using the map as input"
  [m] (into {} (for [[k v] m] [k (apply-template v m)])))

;;
;; Mac
;;

(defn- secret [{rcvid :rcvid key :key}] (str rcvid "-" key))
(defn mac [data]  (-> data (.getBytes encoding) pandect/sha256 .toUpperCase))

(defn- mac-of [m keys]
  (->
    (for [k keys] (k m))
    vec
    (conj (secret m))
    (conj "")
    (->> (ss/join "&"))
    mac))

(defn- with-mac [m]
  (merge m {:mac (mac-of m request-mac-keys)}))

(defn- mac-verified [{:keys [mac] :as m}]
  (if (= mac (mac-of m response-mac-keys))
    m
    (do (error "invalid mac: " (dissoc m :key))
      (throw (IllegalArgumentException. "invalid mac.")))))

;;
;; response parsing
;;

(defn extract-subjectdata [{s :subjectdata}]
  (when (ss/contains? s ",")
    (-> s
      (ss/split #", ")
      (->> (map #(ss/split % #"=")))
      (->> (into {}))
      keys-as-keywords
      (rename-keys {:etunimi :firstname})
      (rename-keys {:sukunimi :lastname}))))

(defn- extract-vtjdata [{:keys [vtjdata]}]
  (vtj/extract-vtj vtjdata))

(defn- extract-userid [{s :extradata}]
  {:userid (last (ss/split s #"="))})

(defn- extract-request-id [{id :trid}]
  {:pre [id]}
  {:stamp id})

(defn user-extracted [m]
  (merge (extract-subjectdata m)
         (extract-vtjdata m)
         (extract-userid m)
         (extract-request-id m)))

;;
;; Request & Response mapping to clojure
;;

(def supported-langs #{"fi" "sv" "en"})

(defn request-data [host lang]
  (-> (config)
    (assoc :lg lang)
    (assoc :trid (generate-stamp))
    (assoc :timestmp (timestamp))
    (assoc :host  host)
    apply-templates
    with-mac
    (dissoc :key)
    (dissoc :url)
    (dissoc :host)
    keys-as-strings))

(defn parsed [m]
  (-> m
    keys-as-keywords
    (assoc :key (:key (config)))
    mac-verified
    (dissoc :key)))

;;
;; Web stuff
;;

(defn session-id [] (get-in (request/ring-request) [:session :id]))

(defn- field [[k v]]
  (form/hidden-field k v))

(defn host-and-ssl-port
  "returns host with port changed from 8000 to 8443. Shitty crap."
  [host] (ss/replace host #":8000" ":8443"))

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

(defpage "/api/vetuma" {:keys [success cancel error language] :as data}
  (let [paths     {:success success :error error :cancel cancel}
        lang  (get supported-langs language (name i18n/default-lang))
        sessionid (session-id)
        vetuma-request (request-data (host :secure) lang)
        trid      (vetuma-request "TRID")
        label     (i18n/localize lang "vetuma.continue")]

    (if sessionid
      (if (every? util/relative-local-url? (vals paths))
        (do
          (mongo/update :vetuma {:sessionid sessionid :trid trid} {:sessionid sessionid :paths paths :trid trid :created-at (java.util.Date.)} :upsert true)
          (html
            (form/form-to [:post (:url (config))]
              (map field vetuma-request)
              (form/submit-button label))))
        (response/status 400 (response/content-type "text/plain" "invalid return paths")))
      (response/status 400 (response/content-type "test/plain" "Session not initialized")))))

(defpage [:post "/api/vetuma"] []
  (let [form-params (:form-params (request/ring-request))
        user (-> form-params
               logged
               parsed
               user-extracted
               logged)
        trid (form-params "TRID")
        data (mongo/update-one-and-return :vetuma {:sessionid (session-id) :trid trid} {$set {:user user}})
        uri  (or (get-in data [:paths :success]) (str (host) "/app/fi/welcome#!/register2"))]
    (response/redirect uri)))

(def- error-status-codes
  ; From Vetuma_palvelun_kutsurajapinnan_maarittely_v3_0.pdf
  {"REJECTED" "Kutsun palveleminen ep\u00e4onnistui, koska se k\u00e4ytt\u00e4j\u00e4n valitsema vuorovaikutteinen taustapalvelu johon Vetuma-palvelu ohjasi k\u00e4ytt\u00e4j\u00e4n toimintoa suorittamaan hylk\u00e4si toiminnon suorittaminen."
   "ERROR" "Kutsu oli virheellinen."
   "FAILURE" "Kutsun palveleminen ep\u00e4onnistui jostain muusta syyst\u00e4 kuin siit\u00e4, ett\u00e4 taustapalvelu hylk\u00e4si suorittamisen."})

(defpage [:any ["/api/vetuma/:status" :status #"(cancel|error)"]] {status :status}
  (let [params       (:form-params (request/ring-request))
        status-param (get params "STATUS")
        trid         (get params "TRID")
        data         (mongo/select-one :vetuma {:sessionid (session-id) :trid trid})
        return-uri   (get-in data [:paths (keyword status)])
        return-uri   (or return-uri "/")]

    (case status
      "cancel" (info "Vetuma cancel")
      "error"  (error "Vetuma failure, STATUS =" status-param "=" (get error-status-codes status-param) "Request parameters:" (keys-as-keywords params)))

    (response/redirect return-uri)))

(defpage "/api/vetuma/user" []
  (let [data (last (mongo/select :vetuma {:sessionid (session-id), :user.stamp {$exists true}} [:user] {:created-at 1}))
        user (:user data)]
    (response/json user)))

;;
;; public local api
;;

(defn- get-data [stamp]
  (mongo/select-one :vetuma {:user.stamp stamp}))

(defn get-user [stamp]
  (:user (get-data stamp)))

(defn consume-user [stamp]
  (when-let [user (get-data stamp)]
    (mongo/remove-many :vetuma {:_id (:id user)})
    (:user user)))

;;
;; dev test api
;;

(env/in-dev
  (defpage "/dev/api/vetuma" {:as data}
    (let [stamp (generate-stamp)
          user  (select-keys data [:userid :firstname :lastname])
          user  (assoc user :stamp stamp)]
      (mongo/insert :vetuma {:user user :created-at (java.util.Date.)})
      (response/json user))))
