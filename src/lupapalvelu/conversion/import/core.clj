(ns lupapalvelu.conversion.import.core
  "Support for importing various extraneous bits of data that are not part of the actual
  KuntaGML-based conversion process. Each namespace that contains `import--items`
  multimethod implementation must be required."
  (:require [lupapalvelu.conversion.import.description]
            [lupapalvelu.conversion.import.note]
            [lupapalvelu.conversion.import.shared :refer [import--items]]
            [lupapalvelu.conversion.import.statement]
            [lupapalvelu.conversion.import.task]
            [lupapalvelu.conversion.schemas :as conv-sc]
            [lupapalvelu.conversion.section]
            [lupapalvelu.logging :as log]
            [lupapalvelu.user :as usr]
            [monger.operators :refer :all]
            [sade.strings :as ss]
            [sade.util :as util]
            [schema.core :as sc]))

(defn conversion--import
  [filename]
  (let [{:keys [organization-id op]
         :as   cfg} (->> (util/read-edn-file filename)
                         ss/trimwalk
                         (sc/validate conv-sc/ImportConfig))
        user        (usr/batchrun-user (if organization-id
                                         [organization-id]
                                         []))]
    (log/with-logging-context {:userId   (:id user)
                               :importOp op
                               :filename filename}
      (import--items (assoc cfg :user user))
      nil)))
