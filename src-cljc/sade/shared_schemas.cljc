(ns sade.shared-schemas
  (:require [sade.strings :as ss]
            [sade.validators :as v]
            [schema.core :refer [defschema] :as sc]))

(defn matches? [re s] (boolean (when (string? s) (re-matches re s))))

(defschema Nat
  "A schema for natural number integer"
  (sc/constrained sc/Int (comp not neg?) "Natural number"))

(defschema PosInt
  "A schema for positive integer"
  (sc/constrained sc/Int pos? "Positive integer"))

(def object-id-pattern #"^[0-9a-f]{24}(?:-pdfa)?$")

(defschema ObjectIdStr
  (sc/pred (partial matches? object-id-pattern) "ObjectId hex string"))

(def valid-object-id-str! (sc/validator ObjectIdStr))

(def valid-object-id-str? (comp not (sc/checker ObjectIdStr)))

(defschema ObjectIdKeyword
  (sc/pred #(and (keyword? %)
                 (matches? object-id-pattern (name %)))
           "ObjectId hex keyword"))

(def uuid-pattern #"^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}(?:-pdfa)?$")

(defschema UUIDStr
  (sc/pred (partial matches? uuid-pattern) "UUID hex string"))

(defschema ApplicationId
  (sc/pred v/application-id? "Application ID"))

(def valid-application-id! (sc/validator ApplicationId))

(defschema FileId
  (sc/cond-pre UUIDStr ObjectIdStr))

(defn max-length-constraint [max-len]
  (fn [v] (<= (count v) max-len)))

(defschema Email
  "A simple schema for email"
  (sc/constrained sc/Str (every-pred v/valid-email? ss/in-lower-case? (max-length-constraint 255)) "Email"))

(def email-checker (sc/checker Email))

(defschema EmailSpaced
  "Like `Email` but allows surrounding whitespace"
  (sc/pred #(->> % ss/trim email-checker nil?)))

(defschema StorageSystem
  "NOTE: S3/Ceph is no longer supported, but old archived attachments may still refer to it."
  (sc/enum :mongodb :s3 :gcs "mongodb" "s3" "gcs"))

(defschema BlankStr
  "A schema for empty or nil valued string"
  (sc/if string? (sc/pred empty? "Empty string") (sc/pred nil? "Nil")))

(defschema NonBlankStr
  (sc/constrained sc/Str ss/not-blank? "Non-blank string"))

(defschema Timestamp
  "A schema for timestamp. Min date 1.1.1900 and max date is 31.12.9999."
  (sc/constrained sc/Int (every-pred (partial < -2208988800000) (partial > 253402214400000)) "Timestamp (long)"))

(def valid-timestamp! (sc/validator Timestamp))

(defonce e-invoice-operators
  ["BAWCFI22"           ;; Basware Oyj
   "003714377140"       ;; Ropo Capital Oy
   "003708599126"       ;; Open Text Oy
   "HELSFIHH"           ;; Aktia Säästöpankki Oyj
   "POPFFI22"           ;; Paikallisosuuspankit
   "HANDFIHH"           ;; Handelsbanken
   "003721291126"       ;; Maventa
   "003723327487"       ;; Apix Messaging Oy
   "003717203971"       ;; Notebeat Oy
   "003723609900"       ;; (tai PAGERO) Pagero
   "003701150617"       ;; Strålfors Oy
   "FIYAPSOL"           ;; YAP Solutions Oy
   "00885060259470028"  ;; Tradeshift
   "TAPIFI22"           ;; S-Pankki Oy (vanha, ent Lähi-Tapiola)
   "INEXCHANGE"         ;; InExchange Factorum AB
   "DNBAFIHX"           ;; DNB Bank ASA
   "ITELFIHH"           ;; Säästöpankit
   "E204503"            ;; OpusCapita Solutions Oy
   "00885790000000418"  ;; HighJump AS
   "NDEAFIHH"           ;; Nordea
   "OKOYFIHH"           ;; OP-Pohjola-ryhmä
   "003701011385"       ;; Tieto Oyj
   "DABAFIHH"           ;; Danske Bank Oyj
   "003703575029"       ;; CGI / TeliaSonera Finland Oyj
   "AABAFI22"           ;; Ålandsbanken Abp
   "SBANFIHH"           ;; S-Pankki Oy (uusi)
   "003726044706"       ;; Netbox Finland Oy
   "FI28768767"         ;; Posti Messaging Oy
   ])

(def valid-ryhti-identifier! (sc/validator UUIDStr))

