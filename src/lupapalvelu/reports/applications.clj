(ns lupapalvelu.reports.applications
  (:require [dk.ative.docjure.spreadsheet :as spreadsheet]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.i18n :as i18n]
            [sade.core :refer [now]]
            [lupapalvelu.action :refer [defraw]])
  (:import (java.io File FileOutputStream)))

(defn- open-applications-for-organization [organizationId]
  (mongo/select :applications {:organization organizationId
                               :state {$in ["submitted" "open" "draft"]}
                               :infoRequest false
                               :primaryOperation.name {$nin [:tyonjohtajan-nimeaminen-v2]}}
                {:_id 1 :state 1 :created 1 :opened 1 :submitted 1 :modified 1 :authority.firstName 1 :authority.lastName 1}
                {:authority.lastName 1}))

(defn- authority [app]
  (->> app :authority ((juxt :firstName :lastName)) (ss/join " ")))

(defn- localized-state [app]
  (i18n/localize "fi" (get app :state)))

(defn- date-value [key app]
  (util/to-local-datetime (get app key)))

(defn open-applications-for-organization-in-excel! [organizationId]
  ;; Create a spreadsheet and save it
  (let [file (File/createTempFile "open-applications" "xlsx")
        data   (open-applications-for-organization organizationId)
        _ (println data organizationId)
        sheet-name (str "Avoimet " (util/to-local-date (now)))
        row-fn (juxt :id
                     authority
                     localized-state
                     (partial date-value :opened)
                     (partial date-value :submitted)
                     (partial date-value :modified))
        wb     (spreadsheet/create-workbook
                 sheet-name
                 (concat [["LP-tunnus" "Käsittelijä" "Tila" "Avattu" "Jätetty" "Muokattu viimeksi"]] (map row-fn data)))
        sheet  (first (spreadsheet/sheet-seq wb))
        header-row (-> sheet spreadsheet/row-seq first)]
    (spreadsheet/set-row-style! header-row (spreadsheet/create-cell-style! wb {:font {:bold true}}))
    (.autoSizeColumn sheet 0)
    (.autoSizeColumn sheet 1)
    (.autoSizeColumn sheet 2)
    (.autoSizeColumn sheet 3)
    (.autoSizeColumn sheet 4)
    (.autoSizeColumn sheet 5)
    (with-open [out (FileOutputStream. file)]
      (spreadsheet/save-workbook-into-stream! out wb))
    file))

