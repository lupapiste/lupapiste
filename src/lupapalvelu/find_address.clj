(ns lupapalvelu.find-address
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error]]
            [clojure.data.zip.xml :refer [xml-> text]]
            [monger.operators :refer :all]
            [monger.query :as q]
            [sade.municipality :as muni]
            [sade.strings :as ss]
            [sade.property :as p]
            [sade.util :as util]
            [sade.xml :as sxml]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.property-location :as plocation]
            [lupapalvelu.wfs :as wfs]))

;; Should be in util or sumthin...

(defn uniq-by
  [by coll]
  (let [step (fn step [xs prev]
               (lazy-seq
                 ((fn [[f :as xs] prev]
                    (when-let [s (seq xs)]
                      (if (= (by f) prev)
                        (recur (rest s) prev)
                          (cons f (step (rest s) (by f))))))
                   xs prev)))]
    (step coll nil)))

(defn- set-kind
  ([k]
    (fn [result] (assoc result :kind k)))
  ([k t]
    (fn [result] (assoc result :kind k :type t))))

;; Municipality data

(defn- municipality-index-for [lang]
  (map (fn [code] [(ss/lower-case (i18n/localize lang :municipality code)) code])
       muni/municipality-codes))

(def municipality-index
  (delay (reduce (fn [m lang] (assoc m lang (municipality-index-for lang))) {} i18n/supported-langs)))

(defn municipality-codes [municipality-name-starts]
  (let [index (apply concat (vals @municipality-index))
        n (ss/lower-case (ss/trim municipality-name-starts))]
    (when (not (ss/blank? n))
      (->> (filter #(ss/starts-with (first %) n) index)
           (map second)
           set))))

;;;
;;; All search-... functions return a sequence of items, where each item is a map
;;; of (at least) following keys:
;;;   :kind       One of :poi, :property-id, or :address
;;;   :location   Map with :x and :y
;;;

(defn search-property-id [lang property-id]
  (map (fn [f] {:location (select-keys f [:x :y])
                :property-id (:kiinttunnus f)
                :kind :property-id})
       (plocation/property-location-info property-id)))

(def max-entries 25)

(defn search-poi [poi]
  (map
    (comp (fn [r] (dissoc r :_id)) (set-kind :poi))
    (mongo/with-collection "poi"
      (q/find {:name {$regex (str \^ (ss/lower-case poi))}})
      (q/sort (array-map :name 1 :priority 1))
      (q/limit max-entries))))

(defn- municipality-prop [lang] (if (= lang "sv") "oso:kuntanimiSwe" "oso:kuntanimiFin"))

(defn search-street [lang street]
  (map
    (comp (set-kind :address :street) wfs/feature-to-address)
    (wfs/post wfs/maasto
      (wfs/query {"typeName" "oso:Osoitenimi"}
        (wfs/ogc-sort-by ["oso:katunimi" "oso:katunumero" (municipality-prop lang)] "asc")
        (wfs/ogc-filter
          (wfs/ogc-and
            (wfs/property-is-like "oso:katunimi" (str street "*"))
            (wfs/property-is-less "oso:jarjestysnumero" "10")))))))

(defn search-poi-or-street [lang v]
  (take max-entries (concat (take (- max-entries 10) (search-street lang  v)) (search-poi v))))

(defn search-street-with-number [lang street number]
  (map
    (comp (set-kind :address :street-number) wfs/feature-to-address)
    (wfs/post wfs/maasto
      (wfs/query {"typeName" "oso:Osoitenimi"}
        (wfs/ogc-sort-by ["oso:katunimi" "oso:katunumero" (municipality-prop lang)] "asc")
        (wfs/ogc-filter
          (wfs/ogc-and
            (wfs/property-is-like "oso:katunimi"   (str street "*"))
            (wfs/property-is-like "oso:katunumero" (str number "*"))
            (when (>= 10 (util/->long number))
              (wfs/property-is-less "oso:jarjestysnumero" "10"))))))))

(defn search-street-maybe-city
  "Checks if city is in municipality list. If not, calls search-street,
   else search with street and city."
  [lang street city]
  (let [street (ss/trim street)]
    (if (empty? (municipality-codes city))
      (search-street lang (str street " " city))
      (map
        (comp (set-kind :address :street-city) wfs/feature-to-address)
        (wfs/post wfs/maasto
                  (wfs/query {"typeName" "oso:Osoitenimi"}
                             (wfs/ogc-sort-by ["oso:katunimi" "oso:katunumero" (municipality-prop lang)] "asc")
                             (wfs/ogc-filter
                               (wfs/ogc-and
                                 (wfs/property-is-like "oso:katunimi" (str street "*"))
                                 (wfs/property-is-like (municipality-prop lang) (str city "*"))
                                 (wfs/property-is-less "oso:jarjestysnumero" "10")))))))))

(defn search-address [lang street number city]
  (map
    (comp (set-kind :address :street-number-city) wfs/feature-to-address)
    (wfs/post wfs/maasto
      (wfs/query {"typeName" "oso:Osoitenimi"}
        (wfs/ogc-sort-by ["oso:katunimi" "oso:katunumero" (municipality-prop lang)] "asc")
        (wfs/ogc-filter
          (wfs/ogc-and
            (wfs/property-is-like "oso:katunimi" (str street "*"))
            (wfs/property-is-like "oso:katunumero" (str number "*"))
            (wfs/property-is-like (municipality-prop lang) (str city "*"))))))))

;;
;; Utils:
;;

(defn- apply-search
  "Return a function that can be used as a target function in 'search' function.
   The returned function accepts the list as returned from clojure.core/re-find.
   It strips the first element (complete match) and applies rest to provided
   function."
  [f lang]
  (fn [m] (apply f (cons lang (drop 1 m)))))

;;
;; Public API:
;;

(defn get-addresses [street number city]
  (wfs/post wfs/maasto
    (wfs/query {"typeName" "oso:Osoitenimi"}
      (wfs/ogc-sort-by ["oso:katunumero"])
      (wfs/ogc-filter
        (wfs/ogc-and
          (wfs/property-is-like "oso:katunimi"     street)
          (wfs/property-is-like "oso:katunumero"   number)
          (wfs/ogc-or
            (wfs/property-is-like "oso:kuntanimiFin" city)
            (wfs/property-is-like "oso:kuntanimiSwe" city)))))))

(defn get-addresses-from-municipality [street number {:keys [url credentials]}]
  (let [filter-xml (wfs/ogc-filter
                     (wfs/ogc-and
                       (wfs/property-is-like "mkos:Osoite/yht:osoitenimi/yht:teksti" street)
                       (wfs/property-is-equal "mkos:Osoite/yht:osoitenumero" number)))
        filter-str (sxml/element-to-string (assoc filter-xml :attrs wfs/krysp-namespaces))
        data (wfs/exec-get-xml :get url
                           credentials
                           {:REQUEST "GetFeature"
                            :SERVICE "WFS"
                            :VERSION "1.1.0"
                            :TYPENAME "mkos:Osoite"
                            :SRSNAME "EPSG:3067"
                            :FILTER filter-str
                            :MAXFEATURES "10"})]
    (-> data
        (sxml/select [:mkos:Osoite]))))

(defn search [term lang]
  (condp re-find (ss/trim term)
    #"^(\d{14})$"                                 :>> (apply-search search-property-id lang)
    p/property-id-pattern                         :>> (fn [result] (search-property-id lang (p/to-property-id (first result))))
    #"^(\S+)$"                                    :>> (apply-search search-poi-or-street lang)
    #"^(\D+)\s+(\d+)\s*,?\s*$"                    :>> (apply-search search-street-with-number lang)
    #"^(.+)\s+(\d+)\s*,?\s*(.+)$"                 :>> (apply-search search-address lang)
    #"^([^,]+)[,\s]+(\D+)$"                       :>> (apply-search search-street-maybe-city lang)
    []))
