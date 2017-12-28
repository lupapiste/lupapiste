(ns lupapalvelu.premises
  (:require [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer [$set]]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [slingshot.slingshot :refer [throw+]]
            [swiss.arrows :refer :all]
            [lupapalvelu.action :as action]))

(defn get-huoneistot-from-application [doc-id application]
  (-> (util/find-by-id doc-id (:documents application)) :data :huoneistot))

;;
;;  IFC-model apartment data from Excel file
;;

(def ifc-to-lupapiste-keys
  {"porras"                             {:key "porras"}
   "huoneistonumero"                    {:key "huoneistonumero"}
   "huoneiston jakokirjain"             {:key "jakokirjain"}
;  "sijaintikerros"                     {:key "sijaintikerros"} ;; ei skeemassa
   "huoneiden lukum\u00e4\u00e4r\u00e4" {:key "huoneluku"}
   "keitti\u00f6tyyppi"                 {:key    "keittionTyyppi"
                                         :values {"1" "keittio"
                                                  "2" "keittokomero"
                                                  "3" "keittotila"
                                                  "4" "tupakaittio"
                                                  ""  "ei tiedossa"}}
   "huoneistoala"                       {:key "huoneistoala"}
   "varusteena wc"                      {:key    "WCKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena amme/suihku"             {:key    "ammeTaiSuihkuKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena parveke"                 {:key    "parvekeTaiTerassiKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena sauna"                   {:key    "saunaKytkin"
                                         :values {"1" true
                                                  "0" false}}
   "varusteena l\u00e4mmin vesi"        {:key    "lamminvesiKytkin"
                                         :values {"1" true
                                                  "0" false}}})

(defn header-pairing-with-cells [vecs]
  (let [headers (map #(.toLowerCase %) (first vecs))
        data (rest vecs)]
    (map #(zipmap headers %) data)))

(defn split-with-semicolon [row]
  (map #(ss/split % #";") row))

(defn- pick-only-header-and-data-rows [rows]
  (drop-while #(not (ss/starts-with % "Porras")) rows))

(defn csv-data->ifc-coll [csv]
  (-> csv
      (ss/split #"\n")
      (pick-only-header-and-data-rows)
      (split-with-semicolon)
      (header-pairing-with-cells)))

(defn item->update [premises-number [ifc-key ifc-val]]
  (let [lp-key (-> ifc-key ifc-to-lupapiste-keys :key)
        lp-val (-> ifc-key ifc-to-lupapiste-keys :values)]
    (when (and (not (empty? ifc-val)) lp-key)
      [(ss/join "." ["huoneistot" premises-number lp-key])
       (if (map? lp-val) (-> ifc-val lp-val) (ss/replace ifc-val #"," "."))])))

(defn premise->updates [premise premises-number]
  (remove nil? (map #(item->update premises-number %) premise)))

(defn remove-old-premises [command doc]
  (let [paths (->> command
                   :application
                   (get-huoneistot-from-application doc)
                   (keys)
                   (map (fn [huoneisto-key] [:huoneistot huoneisto-key])))]
    (doc-persistence/remove-document-data command doc paths "documents")))

(defn save-premises-data [premise-data command doc]
  (let [updates (reduce #(concat %1 (premise->updates (nth premise-data %2) %2))
                        []
                        (range (count premise-data)))]
    (remove-old-premises command doc)
    (doc-persistence/update! command doc updates "documents")))
