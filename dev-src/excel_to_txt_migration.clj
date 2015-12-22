 (ns excel-to-txt-migration
   (:require [clojure.java.io :as io]
             [ontodev.excel :as xls]
             [lupapiste-commons.i18n.resources :as commons-resources]))

(defn- excel-to-txts
  "Helper for excel -> txt migration.
   Writes localization txt files for each sheet in excel."
  [resource-name]
  (with-open [in (io/input-stream (io/resource resource-name))]
    (let [wb      (xls/load-workbook in)
          sheets  (seq wb)]
      (doseq [sheet sheets
              :let [sheet-name (.getSheetName sheet)]]
        (commons-resources/write-txt
          (commons-resources/sheet->map sheet)
          (io/file (str "resources/i18n/" sheet-name ".txt")))))))
