(ns lupapalvelu.document.tools-test
  (:require [lupapalvelu.document.tools :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [expose-testables]]
            [lupapalvelu.document.data-schema :as data-schema]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [sade.schema-generators :as ssg]
            [lupapalvelu.document.schemas :as schemas]
            ;; ensure all schemas are loaded
            [lupapalvelu.document.poikkeamis-schemas]
            [lupapalvelu.document.vesihuolto-schemas]
            [lupapalvelu.document.ymparisto-schemas]
            [lupapalvelu.document.yleiset-alueet-schemas]))

(expose-testables lupapalvelu.document.tools)

(def schema
  {:info {:name "band"},
   :body
   [{:name "band",
     :type :group,
     :body
     [{:name "name", :type :string}
      {:name "genre", :type :string}
      {:name "members"
       :type :group
       :repeating true
       :body [{:name "name", :type :string}
              {:name "instrument", :type :string}]}]}]})

(def expected-simple-document
  {:band {:name nil
          :genre nil
          :members {:0 {:name nil
                        :instrument nil}}}})

(def expected-wrapped-simple-document
  {:band {:name {:value nil}
          :genre {:value nil}
          :members {:0 {:name {:value nil}
                        :instrument {:value nil}}}}})

(fact "simple schema"
  (create-unwrapped-data schema nil-values) => expected-simple-document)

(fact "simple schema with wrapped values"
  (-> schema
    (create-unwrapped-data nil-values)
    (wrapped :value)) => expected-wrapped-simple-document)

;;
;; Public api
;;

(fact "wrapped defaults to :value key"
  (wrapped nil) => {:value nil}
  (wrapped {:value nil}) => {:value {:value nil}})

(fact "unwrapped"
  (unwrapped {:k {:value nil}}) => {:k nil}
  (unwrapped expected-wrapped-simple-document :value) => expected-simple-document
  (unwrapped (wrapped expected-simple-document)) => expected-simple-document)

(fact "create-dummy-document-data"
  (create-document-data schema) => expected-wrapped-simple-document)

(def expected-wrapped-simple-document-timestamped
  {:band {:name {:value nil :modified nil}
          :genre {:value nil :modified nil}
          :members {:0 {:name {:value nil :modified nil}
                        :instrument {:value nil :modified nil}}}}})

(fact "timestampeds"
  (timestamped nil nil) => nil
  (timestamped {} nil) => {}
  (timestamped expected-wrapped-simple-document nil) => expected-wrapped-simple-document-timestamped)

(fact "schema-body-without-element-by-name"
  (schema-body-without-element-by-name (:body schema) "band") => []
  (schema-body-without-element-by-name (:body schema) "invalid") => (:body schema)
  (schema-body-without-element-by-name (:body schema) "members") => [{:name "band"
                                                                      :type :group
                                                                      :body [{:name "name"
                                                                              :type :string}
                                                                             {:name "genre"
                                                                              :type :string}]}])

(fact "strip-elements-by-name"
  (schema-without-element-by-name schema "band") => {:info {:name "band"} :body []}
  (schema-without-element-by-name schema "INVALID") => schema
  (schema-without-element-by-name schema "band") => {:info {:name "band"} :body []})

(def deep-find-test-data {:a {:1 {:b {:c 1}}
                              :2 {:b {:c 2}}
                              :3 {:c {:b {:c 3}}}}})

(def deep-find-result (deep-find deep-find-test-data [:b :c]))

(fact "Deep find"
      (some #(= % [[:a :1] 1]) deep-find-result) => truthy
      (some #(= % [[:a :2] 2]) deep-find-result) => truthy
      (some #(= % [[:a :3 :c] 3]) deep-find-result) => truthy
      (deep-find deep-find-test-data [:b :e]) => '()
      )

(def updates [["a" 1] ["b" 2] ["c" 3]])

(fact "get-update-item-value"
  (get-update-item-value updates "a") => 1
  (get-update-item-value updates "non-existing") => nil
  )

(def party-doc-schemas
  (->> (vals (first (vals (schemas/get-all-schemas))))
       (filter (comp #{:party} :type :info))))

(def non-party-doc-schemas
  (->> (vals (first (vals (schemas/get-all-schemas))))
       (remove (comp #{:party} :type :info))))

(defspec party-doc-user-id-spec
  (prop/for-all [doc (gen/bind (->> (map (comp :name :info) party-doc-schemas)
                                    gen/elements)
                               #(ssg/generator (data-schema/doc-data-schema % true)))]
                (if (= "tyonjohtaja-v2" (doc-name doc))
                  (nil? (party-doc-user-id doc))
                  (party-doc-user-id doc))))

(defspec party-doc-user-id_non-party-docs-spec
  (prop/for-all [doc (gen/bind (->> (map (comp :name :info) non-party-doc-schemas)
                                    gen/elements)
                               #(ssg/generator (data-schema/doc-data-schema % true)))]
                (nil? (party-doc-user-id doc))))

(def special-subtypes #{:hakija :hakijan-asiamies :maksaja})

(defspec party-doc->user-role_special-subtypes-spec
  (prop/for-all [doc (gen/bind (->> party-doc-schemas
                                    (filter (comp special-subtypes :subtype :info))
                                    (map (comp :name :info))
                                    gen/elements)
                               #(ssg/generator (data-schema/doc-data-schema % true)))]
                (= (get-in doc [:schema-info :subtype]) (party-doc->user-role doc))))

(def special-names #{"tyonjohtaja-v2"})

(fact "party-doc->user-role - foreman"
  (->> (ssg/generate (data-schema/doc-data-schema "tyonjohtaja-v2" true))
       party-doc->user-role) => :tyonjohtaja)

(defspec party-doc->user-role_default-spec
  (prop/for-all [doc (gen/bind (->> party-doc-schemas
                                    (remove (comp special-subtypes :subtype :info))
                                    (remove (comp special-names :name :info))
                                    (map (comp :name :info))
                                    gen/elements)
                               #(ssg/generator (data-schema/doc-data-schema % true)))]
                (= (keyword (get-in doc [:schema-info :name])) (party-doc->user-role doc))))

(defspec party-doc->user-role_non-party-doc-spec
  (prop/for-all [doc (gen/bind (->> non-party-doc-schemas
                                    (map (comp :name :info))
                                    gen/elements)
                               #(ssg/generator (data-schema/doc-data-schema % true)))]
                (nil? (party-doc->user-role doc))))
