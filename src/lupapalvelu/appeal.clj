(ns lupapalvelu.appeal
  (:require [sade.schemas :as ssc]
            [schema.core :refer [defschema] :as sc]))

(defschema Appeal
  {:id            ssc/ObjectIdStr
   :paatos-id     ssc/ObjectIdStr
   :appellant     {:firstName  sc/Str
                   :lastName   sc/Str}
   :made          ssc/Timestamp})
