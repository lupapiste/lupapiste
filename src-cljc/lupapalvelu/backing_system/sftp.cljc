(ns lupapalvelu.backing-system.sftp
  (:require [lupapalvelu.backing-system.schema :as schemas]
            [sade.shared-schemas :as ssc]
            [schema-tools.coerce :as stc]
            [schema.core :as sc]))

(def options
  [{:option/name   :version
    :option/type   :option/sftp-version
    :option/schema ssc/NonBlankStr
    :option/label  :auth-admin.integration.sftp.version}])

(defn- valid-sftp?
  [{:keys [targets]}]
  (and (= 1 (count targets))
       (not (contains? targets "R"))))

(def schema (-> (schemas/schema-with-options options)
                (sc/constrained valid-sftp?)))

(def checker (sc/checker schema))

(def coercer (stc/coercer schema stc/json-coercion-matcher))

(defmethod schemas/get-properties :sftp
  [_]
  {:integration/name           :auth-admin.integration.sftp
   :integration/options        options
   :integration/single-target? true
   :integration/schema         schema
   :integration/checker        checker
   :integration/coercer        coercer})
