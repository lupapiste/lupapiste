(ns lupapalvelu.find-address
  (:require [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mml.geocoding.core :as geocoding]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.property-location :as plocation]
            [lupapalvelu.wfs :as wfs]
            [monger.operators :refer :all]
            [monger.query :as q]
            [sade.common-reader :as cr]
            [sade.municipality :as muni]
            [sade.property :as p]
            [sade.strings :as ss]
            [sade.xml :as sxml]))

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

(defn municipality-codes
  ([search-string]
   (municipality-codes search-string ss/starts-with))
  ([search-string pred]
   (let [index (apply concat (vals @municipality-index))
         n     (ss/lower-case (ss/trim search-string))]
     (when-not (ss/blank? n)
       (->> (filter #(pred (first %) n) index)
            (map second)
            set)))))

;;;
;;; All search-... functions return a sequence of items, where each item is a map
;;; of (at least) following keys:
;;;   :kind       One of :poi, :property-id, or :address
;;;   :location   Map with :x and :y
;;;

(defn search-property-id [_ property-id]
  (map (fn [f] {:location (select-keys f [:x :y])
                :property-id (:kiinttunnus f)
                :kind :property-id})
       (plocation/property-lots-info property-id)))

(def max-entries 25)

(defn search-poi [poi]
  (map
    (comp (fn [r] (dissoc r :_id)) (set-kind :poi))
    (mongo/with-collection "poi"
      (q/find {:name {$regex (str \^ (ss/lower-case poi))}})
      (q/sort (array-map :name 1 :priority 1))
      (q/limit max-entries))))

(defn search-street [lang street]
  (->> {:street (ss/trim street)}
       (geocoding/address-by-text! lang)
       (map (set-kind :address :street))))

(defn search-poi-or-street [lang v]
  (take max-entries (concat (take (- max-entries 10) (search-street lang  v)) (search-poi v))))

(defn search-street-with-number [lang street number]
  (->> {:street (ss/trim street) :number (ss/trim number)}
       (geocoding/address-by-text! lang)
       (map (set-kind :address :street-number))))

(defn search-street-city
  [lang street city]
  (->> {:street (ss/trim street) :city (ss/trim city)}
       (geocoding/address-by-text! lang)
       (map (set-kind :address :street-city))))

(defn search-street-maybe-city
  "Checks if city is in municipality list. If not, calls search-street,
   else search with street and city."
  [lang street city]
  (if (empty? (municipality-codes city))
    (search-street lang (str street " " city))
    (search-street-city lang street city)))

(defn search-address [lang street number city]
  (->> {:street (ss/trim street) :number (ss/trim number) :city (ss/trim city)}
       (geocoding/address-by-text! lang)
       (map (set-kind :address :street-number-city))))

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

(defn get-addresses
  [lang street number city]
  (->> {:street (ss/trim street)
        :number (ss/trim number)
        :city   (ss/trim city)}
       (geocoding/address-by-text! lang true)))

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
                            :MAXFEATURES "10"})
        xml (cr/strip-xml-namespaces data)]
    (-> xml
        (sxml/select [:Osoite]))))

(defn search [term lang]
  (condp re-find (ss/trim term)
    #"^(\d{14})$"                                 :>> (apply-search search-property-id lang)
    p/property-id-pattern                         :>> (fn [result] (search-property-id lang (p/to-property-id (first result))))
    #"^(\S+)$"                                    :>> (apply-search search-poi-or-street lang)
    #"^(\D+)\s+(\d+)\s*,?\s*$"                    :>> (apply-search search-street-with-number lang)
    #"^(.+)\s+(\d+)\s*,?\s*(.+)$"                 :>> (apply-search search-address lang)
    #"^([^,]+)[,\s]+(\D+)$"                       :>> (apply-search search-street-maybe-city lang)
    []))
