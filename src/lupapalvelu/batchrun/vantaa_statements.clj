(ns lupapalvelu.batchrun.vantaa-statements
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.statement-schemas :refer [Statement StatementGiver statement-statuses]]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.core :refer [now]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]
            [taoensso.timbre :as timbre]))

(def ORG-ID "092-R")

(def BatchData {sc/Str [Statement]})

(sc/defn ^:always-validate resolve-status  :- (apply sc/enum statement-statuses)
  [status]
  (let [statuset (set statement-statuses)]
    (or (statuset status)
        (statuset (ss/replace status #"\s+" "-")))))

(sc/defn ^:always-validate make-person :- StatementGiver
  [giver :- sc/Str]
  (let [{:keys [id username]} (usr/batchrun-user ORG-ID)]
    {:userId id :email username :name giver}))

(sc/defn ^:always-validate make-statement :- {:municipal-id sc/Str
                                              :statement    Statement}
  [[municipal-id giver request-date due-date given-date status text]]
  (util/strip-blanks {:municipal-id municipal-id
                      :statement    {:id        (mongo/create-id)
                                     :state     :given
                                     :requested (date/timestamp request-date)
                                     :status    (resolve-status status)
                                     :text      text
                                     :dueDate   (date/timestamp due-date)
                                     ;; Rami reporting requires given timestamp, when the
                                     ;; state state is given.
                                     :given     (or (date/timestamp given-date)
                                                    (date/timestamp (date/today)))
                                     :person    (make-person giver)}}))

(defn- process-row [row]
  (->> (xls/cell-seq row)
       (map xls/read-cell)
       ss/trimwalk))

(sc/defn ^:always-validate read-excel :- BatchData
  [excel-path]
  (let [[hdr & rows] (->> (xls/load-workbook-from-file excel-path)
                          (xls/select-sheet (constantly true))
                          (xls/row-seq)
                          (map process-row))]
    (assert (= hdr ["LUPATUNNUS" "LAUSUNNON_ANTAJA" "PYYNTOPVM" "MÄÄRÄAIKA"
                    "LAUSANTOPVM" "LAUSUNNON_TULOS" "LAUSUNTO"])
            "Correct header columns")
    (->> rows
         (map-indexed (fn [i row]
                        (try
                          (make-statement row)
                          (catch Exception e
                            (timbre/warn (ex-message e)
                                         {:n (+ 2 i) :columns row})
                            (throw e)))))
         (group-by :municipal-id)
         (util/map-values #(map :statement %)))))

(sc/defn ^:always-validate filter-batch :- BatchData
  "The filtered batch contains only municipal-ids that match EXACTLY one application."
  [data :- BatchData]
  (let [target-munids   (keys data)
        ;; List of munid sets.
        existing-munids (->> (mongo/select :applications
                                           {:organization             ORG-ID
                                            :facta-imported           true
                                            :verdicts.kuntalupatunnus {$in target-munids}}
                                           [:verdicts.kuntalupatunnus])
                             (map (fn [{:keys [verdicts]}]
                                    (set (map :kuntalupatunnus verdicts)))))
        grouped         (group-by (fn [munid]
                                    (count (filter #(% munid) existing-munids)))
                                  target-munids)]
    (doseq [[n munids] (dissoc grouped 1)]
      (timbre/warnf "%d converted applications with any of the %d kuntalupatunnus %s."
                    n (count munids) munids))
    (select-keys data  (get grouped 1))))

(sc/defn ^:always-validate write-statements
  [data :- BatchData note :- sc/Str]
  (let [note {:created (now) :note note}]
    (doseq [[munid statements] data]
      (mongo/update :applications
                    {:organization             ORG-ID
                     :facta-imported           true
                     :verdicts.kuntalupatunnus munid}
                    {$push {:_sheriff-notes note}
                     $set  {:statements statements}}))))

(defn statements-from-excel [filename]
  (let [data (filter-batch (read-excel filename))]
    (write-statements data (->> (ss/split filename #"/")
                                last
                                (str "LPK-6396 Read statements from ")))
    (timbre/info (count data) "applications updated.")))
