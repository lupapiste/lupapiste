(ns lupapalvelu.matti-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(fact "Create new template"
  (let [{:keys [id name draft]} (command sipoo :new-verdict-template)]
    id => string?
    name => "Päätöspohja"
    draft => {}
    (fact "Fetch draft"
      (query sipoo :verdict-template-draft :template-id id)
      => (contains {:draft {}}))))
