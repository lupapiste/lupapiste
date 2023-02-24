(ns lupapalvelu.backing-system.krysp.kuntagml-yht-version
  (:require [lupapalvelu.permit :as permit]
            [sade.core :refer :all]))

(def- rakval-yht {"2.1.2" "2.1.0"
                  "2.1.3" "2.1.1"
                  "2.1.4" "2.1.2"
                  "2.1.5" "2.1.3"
                  "2.1.6" "2.1.5"
                  "2.1.8" "2.1.5"
                  "2.2.0" "2.1.6"
                  "2.2.2" "2.1.8"
                  "2.2.3" "2.1.9"
                  "2.2.4" "2.1.9"})

(def- ya-yht {"2.1.2" "2.1.0"
              "2.1.3" "2.1.3"
              "2.2.0" "2.1.5"
              "2.2.1" "2.1.6"
              "2.2.3" "2.1.8"
              "2.2.4" "2.1.9"})

(def- poik-yht {"2.1.2" "2.1.0"
                "2.1.3" "2.1.1"
                "2.1.4" "2.1.2"
                "2.1.5" "2.1.3"
                "2.2.0" "2.1.5"
                "2.2.1" "2.1.6"
                "2.2.3" "2.1.8"
                "2.2.4" "2.1.9"})

(def- ymp-yht {"2.1.2" "2.1.3"
               "2.2.1" "2.1.6"
               "2.2.3" "2.1.8"
               "2.2.4" "2.1.9"})

(def- vvvl-yht {"2.1.3" "2.1.3"
                "2.2.1" "2.1.6"
                "2.2.3" "2.1.8"
                "2.2.4" "2.1.9"})

(def- kt-yht {"0.9"   "2.1.3"
              "0.9.1" "2.1.4"
              "0.9.2" "2.1.5"
              "1.0.0" "2.1.5"
              "1.0.1" "2.1.5"
              "1.0.2" "2.1.6"
              "1.0.5" "2.1.8"
              "1.0.6" "2.1.9"})

(def- mm-yht {"0.9"   "2.1.5"
              "1.0.0" "2.1.5"
              "1.0.1" "2.1.6"
              "1.0.3" "2.1.8"})

(def- yht-version
      {:R rakval-yht
       :P poik-yht
       :YA ya-yht
       :MAL ymp-yht
       :YI ymp-yht
       :YL ymp-yht
       :VVVL vvvl-yht
       :KT kt-yht
       :MM mm-yht})

(defn get-yht-version [permit-type ns-version]
  {:pre [(permit/valid-permit-type? (name permit-type))
         ((-> yht-version keys set) (keyword permit-type))]}
  (get-in yht-version [(keyword permit-type) ns-version] "0.0.0"))