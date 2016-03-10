(ns lupapalvelu.municipality-api
  (:require [taoensso.timbre :as timbre :refer [trace debug debugf info warn error errorf fatal]]
            [sade.core :refer :all]
            [lupapalvelu.action :refer [defquery]]))

