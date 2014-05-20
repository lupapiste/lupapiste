(ns user
  (:require [lupapalvelu.server :as server]
            [sade.env :as env]
            [lupapalvelu.fixture :as fixture]
            [lupapalvelu.mongo :as mongo]
            [monger.operators :refer :all]
            [monger.core :as m]
            [monger.collection :as mc]
            [monger.db :as db]
            [monger.gridfs :as gfs]
            [monger.command :refer [server-status]]))

(def go server/-main)
(println "Ready")
