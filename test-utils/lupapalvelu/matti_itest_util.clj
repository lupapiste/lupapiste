(ns lupapalvelu.matti-itest-util
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(defn init-verdict-template [as-user category]
  (let [{id :id :as template} (command as-user :new-verdict-template :category category)]
    template))

(defn set-template-draft-value [template-id path value]
  (fact {:midje/description (format "Draft value: %s %s"
                                    path value)}
        (command sipoo :save-verdict-template-draft-value
                 :template-id template-id
                 :path (flatten [path])
                 :value value) => ok?))

(defn set-template-draft-values [template-id & args]
  (doseq [[path value] (->arg-map args)]
    (set-draft-value template-id path value)))

(defn publish-verdict-template [as-user id]
  (command as-user :publish-verdict-template :template-id id))