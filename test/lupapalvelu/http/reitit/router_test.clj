(ns lupapalvelu.http.reitit.router-test
  (:require [clojure.test :refer [deftest is]]
            [lupapalvelu.http.reitit.core :as reitit]))

(deftest routes-are-valid-test
  (is (reitit/make-router {:components {}})))
