(ns lupapalvelu.bim.checking-plan.schema
  "Schemas for BIM integration checking plans;
  determine which checksets are available for validation and compliance checking and
  how they are configured for organisation."
  (:require [lupapalvelu.bim.schema :as bim-schema]
            [sade.shared-schemas :as ssc]
            [schema.core :as sc]))

;; Vectors so order is maintained for UI
(def checking-types ["model-validation" "code-compliance"])

(sc/defschema CheckingType
  (apply sc/enum checking-types))

(sc/defschema CheckingPlanFile
  {:bucket     ssc/NonBlankStr
   :object-key ssc/NonBlankStr
   :filename   ssc/NonBlankStr
   :created    ssc/Timestamp})

(sc/defschema CheckingPlan
  {:id            ssc/NonBlankStr
   :enabled       sc/Bool
   :title         ssc/NonBlankStr
   :description   sc/Str
   :checking-type CheckingType
   :model-type    bim-schema/ModelType
   :file          CheckingPlanFile})

(def admin-editable-keys
  "The :file values are based on the uploaded file so not directly editable"
  #{:enabled :title :description :checking-type :model-type})

(defn linked-file-version-check-result [check-type version]
  (some-> version
          :bim
          (get (-> check-type name (str "-result") keyword))))

(defn check-type->sub-task-name [check-type]
  (if (= "compliance" (name check-type))
    "compliance-check"
    "validation-check"))

(defn sub-task-name->check-type [sub-task-name]
  (-> sub-task-name
      keyword
      {:compliance-check "compliance"
       :validation-check "validation"}))

(defn check-type->solibri-checking-type [check-type]
  (if (= "compliance" (name check-type))
    "code-compliance"
    "model-validation"))
