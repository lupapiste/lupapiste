(ns lupapalvelu.matti-itest-util
  (:require [lupapalvelu.itest-util :refer :all]))

(defn init-verdict-template [as-user]
  (let [{id :id} (command as-user :new-verdict-template :category "r")]
    id))

(defn publish-template [as-user id]
  (command as-user :publish-verdict-template :template-id id))