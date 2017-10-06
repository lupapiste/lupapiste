(ns lupapalvelu.matti-itest-util
  (:require [lupapalvelu.itest-util :refer :all]))

(defn init-verdict-template [as-user category]
  (let [{id :id :as template} (command as-user :new-verdict-template :category category)]
    template))

(defn publish-verdict-template [as-user id]
  (command as-user :publish-verdict-template :template-id id))