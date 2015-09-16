(ns lupapalvelu.find-address
  (:require [clojure.string :as s]
            [monger.operators :refer :all]
            [clojure.data.zip.xml :refer [xml-> text]]
            [lupapalvelu.i18n :as i18n]
            [monger.query :as q]
            [sade.strings :as ss]
            [sade.property :as p]
            [lupapalvelu.mongo :as mongo]
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

;;;
;;; All search-... functions return a sequence of items, where each item is a map
;;; of (at least) following keys:
;;;   :kind       One of :poi, :property-id, or :address
;;;   :location   Map with :x and :y
;;;

(defn search-property-id [property-id]
  (map (fn [f] {:location (wfs/feature-to-position f)
                :property-id (:kiinttunnus (wfs/feature-to-property-id f))
                :kind :property-id})
       (wfs/point-by-property-id property-id)))

(def max-entries 25)

(defn search-poi [poi]
  (map
    (comp (fn [r] (dissoc r :_id)) (set-kind :poi))
    (mongo/with-collection "poi"
      (q/find {:name {$regex (str \^ (s/lower-case poi))}})
      (q/sort (array-map :name 1 :priority 1))
      (q/limit max-entries))))

(defn municipality-prop [] (if (= i18n/*lang* "sv") "oso:kuntanimiSwe" "oso:kuntanimiFin"))

(defn search-street [street]
  (map
    (comp (set-kind :address :street) wfs/feature-to-address)
    (wfs/post wfs/maasto
      (wfs/query {"typeName" "oso:Osoitenimi"}
        (wfs/ogc-sort-by [(municipality-prop)])
        (wfs/ogc-filter
          (wfs/ogc-and
            (wfs/property-is-like "oso:katunimi" (str street "*"))
            (wfs/property-is-less "oso:jarjestysnumero" "10")))))))

(defn search-poi-or-street [v]
  (take max-entries (concat (take (- max-entries 10) (search-street v)) (search-poi v))))

(defn search-street-with-number [street number]
  (map
    (comp (set-kind :address :street-number) wfs/feature-to-address)
    (wfs/post wfs/maasto
      (wfs/query {"typeName" "oso:Osoitenimi"}
        (wfs/ogc-sort-by [(municipality-prop)])
        (wfs/ogc-filter
          (wfs/ogc-and
            (wfs/property-is-like "oso:katunimi"   (str street "*"))
            (wfs/property-is-like "oso:katunumero" (str number "*"))
            (wfs/property-is-less "oso:jarjestysnumero" "10")))))))

(defn search-street-with-city [street city]
  (map
    (comp (set-kind :address :street-city) wfs/feature-to-address)
    (wfs/post wfs/maasto
      (wfs/query {"typeName" "oso:Osoitenimi"}
        (wfs/ogc-sort-by ["oso:katunimi" "oso:katunumero"])
        (wfs/ogc-filter
          (wfs/ogc-and
            (wfs/property-is-like "oso:katunimi" (str street "*"))
            (wfs/property-is-like (municipality-prop) (str city "*"))
            (wfs/property-is-less "oso:jarjestysnumero" "10")))))))

(defn search-address [street number city]
  (map
    (comp (set-kind :address :street-number-city) wfs/feature-to-address)
    (wfs/post wfs/maasto
      (wfs/query {"typeName" "oso:Osoitenimi"}
        (wfs/ogc-sort-by ["oso:katunimi" "oso:katunumero"])
        (wfs/ogc-filter
          (wfs/ogc-and
            (wfs/property-is-like "oso:katunimi" (str street "*"))
            (wfs/property-is-like "oso:katunumero" (str number "*"))
            (wfs/property-is-like (municipality-prop) (str city "*"))))))))

;;
;; Utils:
;;

(defn- apply-search
  "Return a function that can be used as a target function in 'search' function.
   The returned function accepts the list as returned from clojure.core/re-find.
   It strips the first element (complete match) and applies rest to provided
   function."
  [f]
  (fn [m] (apply f (drop 1 m))))

;;
;; Public API:
;;

(defn search [term]
  (condp re-find (s/trim term)
    #"^(\d{14})$"                                 :>> (apply-search search-property-id)
    p/property-id-pattern                         :>> (fn [result] (search-property-id (p/to-property-id (first result))))
    #"^(\S+)$"                                    :>> (apply-search search-poi-or-street)
    #"^(\S+)\s+(\d+)\s*,?\s*$"                    :>> (apply-search search-street-with-number)
    #"^(\S+)\s+(\S+)$"                            :>> (apply-search search-street-with-city)
    #"^(\S+)\s+(\d+)\s*,?\s*(\S+)$"               :>> (apply-search search-address)
    []))
