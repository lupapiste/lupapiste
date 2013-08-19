(ns lupapalvelu.document.canonical-common
  (:require [clj-time.format :as timeformat]
            [clj-time.coerce :as tc]))


; Empty String will be rendered as empty XML element
(def empty-tag "")

; State of the content when it is send over KRYSP
; NOT the same as the state of the application!
(def toimituksenTiedot-tila "keskener\u00e4inen")

(defn to-xml-date [timestamp]
  (let [d (tc/from-long timestamp)]
    (if-not (nil? timestamp)
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd") d))))

(defn to-xml-datetime [timestamp]
  (let [d (tc/from-long timestamp)]
    (if-not (nil? timestamp)
      (timeformat/unparse (timeformat/formatter "YYYY-MM-dd'T'HH:mm:ss") d))))

(defn to-xml-date-from-string [date-as-string]
  (let [d (timeformat/parse-local-date (timeformat/formatter "dd.MM.YYYY" ) date-as-string)]
    (timeformat/unparse-local-date (timeformat/formatter "YYYY-MM-dd") d)))

(defn by-type [documents]
  (group-by #(keyword (get-in % [:schema :info :name])) documents))


(def puolto-mapping {:condition "ehdoilla"
                     :no "ei puolla"
                     :yes "puoltaa"})

(defn- get-statement [statement]
  (let [lausunto {:Lausunto
                  {:id (:id statement)
                   :viranomainen (get-in statement [:person :text])
                   :pyyntoPvm (to-xml-date (:requested statement))}}]
    (if-not (:status statement)
      lausunto
      (assoc-in lausunto [:Lausunto :lausuntotieto] {:Lausunto
                                                     {:viranomainen (get-in statement [:person :text])
                                                      :lausunto (:text statement)
                                                      :lausuntoPvm (to-xml-date (:given statement))
                                                      :puoltotieto
                                                      {:Puolto
                                                       {:puolto ((keyword (:status statement)) puolto-mapping)}}}}))))

(defn- get-statements [statements]
  ;Returing vector because this element to be Associative
  (vec (map get-statement statements)))
