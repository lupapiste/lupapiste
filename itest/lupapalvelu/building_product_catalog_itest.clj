(ns lupapalvelu.building-product-catalog-itest
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [sade.env :as env]))

(use-fixtures :once (fn [run-tests]
                      (with-test-system (-> {:fixture "minimal"}
                                            test-system/with-remote-components)
                        (env/with-feature-value :building-product-catalog true
                          (run-tests)))))

(env/with-feature-value :building-product-catalog true
  (deftest rakennustuoteluettelo-tests
    (let [app            (create-and-submit-application mikko :operation "kerrostalo-rivitalo")
          application-id (:id app)]
      (let [op-doc-id (:id (domain/get-document-by-name app "uusiRakennus"))
            _         (command mikko :update-doc :id application-id :doc op-doc-id
                               :updates [["rakennusluokka-vahahiilisyys.rakennusluokka-vahahiilisyys", "0110 omakotitalot"]] :collection "documents")
            catalogs  (-> (query-application mikko application-id)
                          :building-product-catalogs)]
        (is (some #(= (-> % :schema-info :name) "rakennustuoteluettelo") catalogs)
            "rakennustuoteluettelo document should be created after kaytto.rakennusluokka update"))
      (let [catalog (-> (query-application mikko application-id)
                        (domain/get-building-product-catalog-by-name "rakennustuoteluettelo"))
            _       (command mikko :update-building-product-catalog :id application-id :doc (:id catalog)
                             :updates [["sisaltyvat-tuotteet.alapohja.0.tuote" "Ruuvi"]])
            catalog (-> (query-application mikko application-id)
                        (domain/get-building-product-catalog-by-name "rakennustuoteluettelo"))]
        (is (= (get-in catalog [:data :sisaltyvat-tuotteet :alapohja :0 :tuote :value]) "Ruuvi")
            "rakennustuoteluettelo document is updated correctly")
        (let [_                    (command mikko :add-operation :id application-id :operation "kerrostalo-rivitalo")
              op-doc-ids           (->> (domain/get-documents-by-name (query-application mikko application-id) "uusiRakennus")
                                        (map :id)
                                        set)
              current-building-ref (-> (query-application mikko application-id)
                                       (domain/get-building-product-catalog-by-name "rakennustuoteluettelo")
                                       :building-ref)
              new-building-ref     (first (set/difference op-doc-ids #{current-building-ref}))
              _                    (command mikko :update-building-product-catalog-operation :id application-id
                                            :doc (:id catalog) :ref new-building-ref)
              catalog              (-> (query-application mikko application-id)
                                       (domain/get-building-product-catalog-by-name "rakennustuoteluettelo"))]
          (is (= (:building-ref catalog) new-building-ref)
              "rakennustuoteluettelo building-ref is updated correctly"))))))
