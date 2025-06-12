(ns lupapalvelu.mml.geocoding.util
  (:require [sade.strings :as ss]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn query->search-text
  "Build search text

  Note that the MML geocoding API:
  - claims that wildcards are used by default: not true
  - it seems the wildcard can only be used in the street name, e.g. `\"kaskitie* 1, tampere\"` is fine
    but `\"kaskitie 1*, tampere\"` or `\"kaskitie 1, tampere*\"` return no results."
  [{:keys [street number city postal-code]}]
  (ss/join-non-blanks ", "
                      [(ss/join-non-blanks " " [(str street "*")
                                                number])
                       city postal-code]))

(def ^:private street-name-and-number
  "Construct a comparable value of an address.
  E.g. `{:street \"katu\" :number \"3A1\"}` => `[\"katu\" 3 \"A\" 1]`"
  (let [first-digits (fn [addr]
                       (some->> addr
                                :number
                                (re-seq #"(\p{Alpha}+)|(\d+)")
                                (keep (fn [[_ letters numbers]]
                                        (or letters
                                            (and numbers (Long/parseLong numbers)))))))]
    (fn [addr] (apply vector (:street addr) (first-digits addr)))))

(defn sort-addresses
  "The API returns values sorted lexicographically, eg. Street 10 is before Street 2
   -> Split address fields and sort in numeric order"
  [addresses]
  (sort-by street-name-and-number util/compare-colls addresses))
