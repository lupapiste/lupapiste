(ns lupapalvelu.find-address
  (:use [monger.operators]
        [clojure.data.zip.xml :only [xml-> text]]
        [lupapalvelu.i18n :only [*lang* with-lang]])
  (:require [clojure.string :as s]
            [monger.query :as q]
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
  ; FIXME: Fix result to property format
  (map (set-kind :property-id)
       (wfs/point-by-property-id property-id)))

(defn search-poi [poi]
  (let [name-elem (str "name." *lang*)]
    (->> (q/with-collection "poi"
           (q/find {name-elem {$regex (str \^ (s/lower-case poi))}})
           (q/sort {name-elem -1})
           (q/limit 10))
      (map (comp (set-kind :poi) (fn [d] (assoc d :text (get-in d [:text (keyword *lang*)]))))))))

(defn search-street [street]
  (let [mun-prop "oso:kuntanimiFin"]
    (->> (wfs/post wfs/maasto
           (wfs/query {"typeName" "oso:Osoitenimi"}
             (wfs/sort-by [mun-prop])
               (wfs/filter
                 (wfs/and
                   (wfs/property-is-like "oso:katunimi"   (str street "*"))
                   (wfs/property-is-less "oso:jarjestysnumero" "10")))))
      (map (comp (set-kind :address :street-number) wfs/feature-to-address)))))

(defn search-poi-or-street [v]
  (take 15 (concat (take 10 (search-street v)) (take 10 (search-poi v)))))

(defn search-street-with-number [street number]
  (let [mun-prop "oso:kuntanimiFin"]
    (->> (wfs/post wfs/maasto
           (wfs/query {"typeName" "oso:Osoitenimi"}
             (wfs/sort-by [mun-prop])
               (wfs/filter
                 (wfs/and
                   (wfs/property-is-like "oso:katunimi"   (str street "*"))
                   (wfs/property-is-like "oso:katunumero" (str number "*"))
                   (wfs/property-is-less "oso:jarjestysnumero" "10")))))
      (map (comp (set-kind :address :street-number) wfs/feature-to-address)))))

(defn search-street-with-city [street city]
  (let [mun-prop "oso:kuntanimiFin"]
    (->> (wfs/post wfs/maasto
           (wfs/query {"typeName" "oso:Osoitenimi"}
             (wfs/sort-by ["oso:katunimi" "oso:katunumero"])
               (wfs/filter
                 (wfs/and
                   (wfs/property-is-like "oso:katunimi" (str street "*"))
                   (wfs/property-is-like mun-prop (str city "*"))
                   (wfs/property-is-less "oso:jarjestysnumero" "10")))))
      (map (comp (set-kind :address :street-city) wfs/feature-to-address)))))

(defn search-address [street number city]
  (let [mun-prop "oso:kuntanimiFin"]
    (->> (wfs/post wfs/maasto
           (wfs/query {"typeName" "oso:Osoitenimi"}
             (wfs/sort-by ["oso:katunimi" "oso:katunumero"])
               (wfs/filter
                 (wfs/and
                   (wfs/property-is-like "oso:katunimi" (str street "*"))
                   (wfs/property-is-like "oso:katunumero" (str number "*"))
                   (wfs/property-is-like mun-prop (str city "*"))
                   (wfs/property-is-less "oso:jarjestysnumero" "10")))))
    (map (comp (set-kind :address :street-number-city) wfs/feature-to-address)))))

;;
;; Utils:
;;

(defn- pwz
  "Pad 's' with zeros so that its at least 'c' characters long"
  [c s]
  (apply str (conj (vec (repeat (- c (count s)) \0)) s)))

(defn- to-property-id
  "Convert property ID elements to 'database' format"
  [a b c d]
  (str (pwz 3 a) (pwz 3 b) (pwz 4 c) (pwz 4 d)))

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
    #"^(\d{1,3})-(\d{1,3})-(\d{1,4})-(\d{1,4})$"  :>> (fn [[_ a b c d]] (search-property-id (to-property-id a b c d)))
    #"^(\S+)$"                                    :>> (apply-search search-poi-or-street)
    #"^(\S+)\s+(\d+)\s*,?\s*$"                    :>> (apply-search search-street-with-number)
    #"^(\S+)\s+(\S+)$"                            :>> (apply-search search-street-with-city)
    #"^(\S+)\s+(\d+)\s*,?\s*(\S+)$"               :>> (apply-search search-address)
    []))
