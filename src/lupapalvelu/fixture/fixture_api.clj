(ns lupapalvelu.fixture.fixture-api
  (:require [taoensso.timbre :refer [debug info warnf error]]
            [sade.env :refer [in-dev]]
            [sade.core :refer [ok fail]]
            [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.fixture.core :refer :all]))

;; dev tools:

(in-dev

  (defquery apply-fixture
    {:parameters [:name]
     :input-validators [(partial action/non-blank-parameters [:name])]
     :user-roles #{:anonymous}}
    [{{:keys [name]} :data}]
    (require (symbol (str "lupapalvelu.fixture." name)))
    (if (exists? name)
      (do
        (apply-fixture name)
        (ok))
      (do
        (warnf "fixture '%s' not found" name)
        (fail :error.fixture-not-found)))))
