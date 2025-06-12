(ns lupapalvelu.document.fix-rakennusoikeus-valinta-test
  (:require [clojure.test :refer [deftest is testing]]
            [lupapalvelu.application :as app]
            [lupapalvelu.document.all-schemas] ;; Ensure all document schemas are registered
            [lupapalvelu.document.rakennusoikeus-validator :refer [ryhti-schemas ryhti-laajentaminen-schemas]]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools]
            [sade.util :as util]))

(defn make-xf
  [{:keys [clear-area?]}]
  (comp
    ;; Find all registered schemas, which define a `rakennusoikeudellinenKerrosala` field
    (filter (fn [[_schema-name schema]]
              (->> schema
                   ;; Child nodes are under :body -> {:body [ ... children ...]}
                   (tree-seq :body :body)
                   (some (fn [node]
                           (= "rakennusoikeudellinenKerrosala" (:name node)))))))
    ;; Transform schemas to dummy documents
    (map (fn [[schema-name schema]]
           [schema-name {:data (tools/create-document-data schema (partial tools/dummy-values "123"))}]))
    ;; Remove any generated "rakennusoikeusValinta" fields
    (map (fn [[schema-name doc]]
           [schema-name (util/postwalk-map
                          (fn [{:keys [rakennusoikeusValinta] :as m}]
                            (if rakennusoikeusValinta
                              (dissoc m :rakennusoikeusValinta)
                              m))
                          doc)]))
    ;; Optionally set any `rakennusoikeudellinenKerrosala` fields to ""
    (map (if clear-area?
           (fn [[schema-name doc]]
             [schema-name (util/postwalk-map
                            (fn [{:keys [rakennusoikeudellinenKerrosala] :as m}]
                              (if rakennusoikeudellinenKerrosala
                                (assoc m :rakennusoikeudellinenKerrosala {:value ""})
                                m))
                            doc)])
           identity))
    ;; Run the fix, which should add the "rakennusoikeusValinta" field back where appropriate
    (map (fn [[schema-name doc]]
           [schema-name (app/fix-default-rakennusoikeusValinta doc)]))
    ;; Check that every "rakennusoikeudellinenKerrosala" is paired with a corresponding
    ;; "rakennusoikeusValinta" field.
    (filter (fn [[_schema-name doc]]
              (->> doc
                   ;; Documents are just nested maps
                   (tree-seq map? vals)
                   (filter map?)
                   (some (fn [{:keys [rakennusoikeudellinenKerrosala
                                      rakennusoikeusValinta]}]
                           (and rakennusoikeudellinenKerrosala
                                (not= {:value schemas/default-rakennusoikeusValinta}
                                      rakennusoikeusValinta)))))))
    ;; Collect all schema names where "rakennusoikeusValinta" was not set
    (map first)))

(deftest fix-default-rakennusoikeusvalinta-test
  ;; Check that no schema specifies a "rakennusoikeudellinenKerrosala" field
  ;; in such a way that `app/fix-default-rakennusoikeusValinta` can't find it.
  (let [non-ryhti-schemas (filter (fn [[schema-name _v]]
                                    (and (not (ryhti-schemas schema-name))
                                         (not (ryhti-laajentaminen-schemas schema-name))))
                                  (schemas/get-schemas 1))]

    (is (empty? (into #{} (make-xf {}) non-ryhti-schemas)))

    (testing "Also generate field if rakennusoikeudellinenKerrosala is empty"
      (is (empty? (into #{} (make-xf {:clear-area? true}) non-ryhti-schemas))))))
