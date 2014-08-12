(ns lupapalvelu.company
  (:require [monger.operators :refer :all]
            [schema.core :as sc]
            [sade.util :refer [max-length max-length-string y?]]
            [lupapalvelu.mongo :as mongo])
  (:import [org.joda.time DateTime]))

;;
;; Utils:
;;

(def Company {:id       #"^\w{24}$"
              :name     (max-length-string 64)
              :y        (sc/pred y? "Not valid Y number")
              :created  DateTime})

; (sc/check Company {:id (mongo/create-id) :name "fo" :y "FI2341528-4" :created (DateTime.)})
