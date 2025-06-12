(ns lupapalvelu.reports.fastexcel
  "Mechanisms for generating Excels from map arrays using fastexcel library. Typically used in REPL."
  (:require [clojure.java.io :as io]
            [sade.date :as date]
            [sade.schemas :as ssc]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc])
  (:import [java.time ZonedDateTime]
           [org.dhatim.fastexcel BorderSide
                                 BorderStyle
                                 Workbook
                                 Worksheet]))

(set! *warn-on-reflection* true)

(sc/defschema ^:private Column
  "`path` is kw-path to the data field. The field value is either stored as such (default) or
  processed throw `:fn` if given. `:text` is the column name."
  {:path                 sc/Keyword
   :text                 ssc/NonBlankStr
   (sc/optional-key :fn) ssc/Fn})

(defprotocol ^:private SetValue
  (set-value [v ws r c]))

(defn- set-style
  ([^Worksheet ws ^long r ^long c wrap?]
   (if wrap?
     (do
       (doto (.style ws r c)
         (.indent 1)
         (.verticalAlignment "top")
         (.wrapText true)
         (.set))
       (.width ws c 100))
     (doto (.style ws r c)
         (.indent 1)
         (.verticalAlignment "top")
         (.set))))
  ([ws r c] (set-style ws r c false)))

(extend-protocol SetValue
  Number
  (set-value [v ws r c]
    (.value ^Worksheet ws ^long r ^long c ^Number v)
    (set-style ws r c))
  String
  (set-value [v ws r c]
    (.value ^Worksheet ws ^long r ^long c ^String v)
    (set-style ws r c (or (> (count v) 100)
                          (ss/includes? v "\n"))))
  ZonedDateTime
  (set-value [v ws r c]
    (.value ^Worksheet ws ^long r ^long c ^String (date/iso-datetime v :ms))
    (set-style ws r c))
  nil
  (set-value [_ ws r c]
    (.value ^Worksheet ws ^long r ^long c "")
    (set-style ws r c))
  Boolean
  (set-value [v ws r c]
    (.value ^Worksheet ws ^long r ^long c ^Boolean v)
    (set-style ws r c)))

(sc/defn ^:always-validate ->excel
  [out columns :- [Column] rows]
  (with-open [os (io/output-stream out)
              wb (Workbook. os "Lupapiste" "1.0")
              ws (.newWorksheet wb "Raportti")]
    ;; Header
    (->> columns
         (map-indexed vector)
         (run! (fn [[^Long c {:keys [text]}]]
                 (set-value text ws 0 c)
                 (doto (.style ws 0 c)
                   (.bold)
                   (.borderStyle BorderSide/BOTTOM BorderStyle/MEDIUM)
                   (.wrapText false)
                   (.set)))))
    (.freezePane ws 0 1)
    (->> rows
         (map-indexed vector)
         (run! (fn [[r row]]
                 (->> columns
                      (map-indexed vector)
                      (run! (fn [[c {path :path fun :fn}]]
                              (cond-> (get-in row (util/split-kw-path path))
                                fun  fun
                                true (set-value ws (inc r) c))))))))
    (.finish wb)))
