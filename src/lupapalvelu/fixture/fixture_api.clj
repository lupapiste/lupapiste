(ns lupapalvelu.fixture.fixture-api
  (:require [lupapalvelu.action :refer [defquery] :as action]
            [lupapalvelu.fixture.core :as fixture]
            [sade.core :refer [ok fail]]
            [sade.env :refer [in-dev]]))

;; dev tools:

(in-dev
  (require '[lupapalvelu.fixture.fixtures])
  (defquery apply-fixture
    {:parameters       [name]
     :input-validators [(partial action/non-blank-parameters [:name])]
     :feature          :dev-mode-supported
     :user-roles       #{:anonymous}}
    [{:keys [system]}]
    (if (fixture/apply-fixture name system)
      (ok)
      (fail :error.fixture-not-found))))
