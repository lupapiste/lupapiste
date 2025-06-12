(ns lupapalvelu.application.designer
  (:require [lupapalvelu.document.model :as model]))

(defn designer-document->designer-consent [document]
  (some-> document :data :designerConsent :value))

(defn designer-document->approval-state [application document]
  (cond
    ;; Approve already requires that model is valid and consent is given
    (-> document :meta :_approved :value (= "approved"))
    :approved

    (and (model/validate application document)
         (designer-document->designer-consent document))
    :consented

    :else
    :open))
