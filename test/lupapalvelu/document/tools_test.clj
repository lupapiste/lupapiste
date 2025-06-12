(ns lupapalvelu.document.tools-test
  (:require [clojure.set :as s]
            [clojure.test :refer [deftest testing is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lupapalvelu.document.all-schemas] ;; ensure all schemas are loaded
            [lupapalvelu.document.data-schema :as data-schema]
            [lupapalvelu.document.flags :as flags]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.tools :as tools :refer :all]
            [lupapalvelu.operations] ;; ensure all schemas are loaded
            [lupapalvelu.tasks] ;; ensure all schemas are loaded
            [midje.sweet :refer :all]
            [midje.util :refer [expose-testables testable-privates]]
            [sade.schema-generators :as ssg]
            [sade.util :as util]
            [schema-tools.walk :as walk]
            [sade.env :as env]
            [sade.date :as date]))

(expose-testables lupapalvelu.document.tools)

(testable-privates lupapalvelu.document.tools
                   path-string->absolute-path)

(def schema
  {:info {:name "band"},
   :body
   [{:name "band",
     :type :group,
     :body
     [{:name "name", :type :string}
      {:name "link" :type :textlink :pseudo? true}
      {:name "genre", :type :string}
      {:name "members"
       :type :group
       :repeating true
       :body [{:name "name", :type :string}
              {:name "twitter" :type :textlink :pseudo? true}
              {:name "instrument", :type :string}]}]}]})

(def expected-simple-document
  {:band {:name    ""
          :genre   ""
          :members {:0 {:name       ""
                        :instrument ""}}}})

(def expected-wrapped-simple-document
  {:band {:name    {:value ""}
          :genre   {:value ""}
          :members {:0 {:name       {:value ""}
                        :instrument {:value ""}}}}})

(fact "simple schema"
  (create-unwrapped-data schema (partial default-values {})) => expected-simple-document)

(fact "simple schema with wrapped values"
  (-> schema
      (create-unwrapped-data (partial default-values {}))
      (wrapped :value)) => expected-wrapped-simple-document)

(facts path-string->absolute-path
  (fact "absolute-path"
    (path-string->absolute-path [:foo :bar] "/quu/quz") => [:quu :quz])

  (fact "relative-path"
    (path-string->absolute-path [:foo :bar] "quu/quz") => [:foo :bar :quu :quz]))

;;
;; Public api
;;

(facts "body"
  (fact "flattens stuff into lists"    (body 1 2 [3 4] 5) => [1 2 3 4 5])
  (fact "does not flatten recursively" (body 1 2 [3 4 [5]]) => [1 2 3 4 [5]])
  (fact "does not overflow the stack") (apply body (take 10000 (repeat ["foo"]))) => coll?)


(facts ->select-options
  (fact "->select-options generates options for select component from list of strings"
    (->select-options ["foo" "bar"]) => [{:name "foo"} {:name "bar"}]))

(facts build-body
  (fact "build body applies all with-modification funtions functions to the body content after body-content vector
              is shallow-merged using body function. The each funtions can trust that a vector is passed to them."
    (build-body [(fn [acc] (update-in acc [0 :name] #(str % "+mod1")))
                 (fn [acc] (map (fn [node] (update node :name #(str % "+mod2"))) acc))
                 ; map above returns a lazy sequence, if it's not covnerted into a vector (as it shoul) this
                 ; throws function throws.
                 (fn [acc] (update-in acc [1 :name] #(str % "+mod3")))]
                ; body content can be entered in same format as for body function
                {:name "foo"}
                [{:name "bar"}
                 {:name "biz"}])
    => [{:name "foo+mod1+mod2"}
        {:name "bar+mod2+mod3"}
        {:name "biz+mod2"}]))

(fact no-nodes-has-visibility-condition-in-body?
  (doseq [visibility-attribute [:show-when :hide-when]]
    (fact (format "Should return false if any node in body vector has non-nil %s" visibility-attribute)
      (no-nodes-has-visibility-condition-in-body? [{:name "foo"} {:name "bar" visibility-attribute true}]) => false))
  (fact "Happy case"
    (no-nodes-has-visibility-condition-in-body? [{:name "foo"}]) => true)
  (fact "Only root level attributes are taken into account"
    (no-nodes-has-visibility-condition-in-body? [{:name "foo" :body [{:name "bar" :show-when true}]}]) => true))

(facts with-tiedot-esitetty-liitteessa
  (fact "with-tiedot-esitetty-ottamissuunnitelmassa Adds tiedot-esitetty-ottamissuunnitelmassa in to body-content
              vector as the first field and adds show-when setting to all root-level fields."
    (with-tiedot-esitetty-liitteessa
      [{:name "foo" :type :checkbox}
       {:name "bar"
        :type :group
        :body [{:name :biz :type :string :show-when {:path "foo" :values #{true}}}]}])
    => [{:name "tiedot-esitetty-liitteessa"
         :type :checkbox
         :css [:full-width-component]
         :size :l}
        {:name "tiedot-esitetty-liitteessa-referenssi"
         :type :string
         :css [:full-width-component]
         :size :l
         :show-when {:path "tiedot-esitetty-liitteessa" :values #{true}}}
        {:name      "foo"
         :type      :checkbox
         :show-when {:path "tiedot-esitetty-liitteessa" :values #{false}}}
        {:name      "bar"
         :type      :group
         :show-when {:path "tiedot-esitetty-liitteessa" :values #{false}}
         :body      [{:name :biz :type :string :show-when {:path "foo" :values #{true}}}]}])
  (doseq [visibility-key [:show-when :hide-when]]
    (fact
      (format "with-tiedot-esitetty-ottamissuunnitelmassa should throw if a root level component already contains a
                    %s key because visibility cannot depend on the multiple fields." visibility-key)
      (with-tiedot-esitetty-liitteessa
        [{:name "foo" :type :string}
         {:name          "bar"
          visibility-key {:path "some" :values #{false}}
          :type          :group
          :body          [{:name :biz :type :string}]}])
      => (throws AssertionError))))

(fact "wrapped defaults to :value key"
  (wrapped nil) => {:value nil}
  (wrapped {:value nil}) => {:value {:value nil}})

(fact "unwrapped"
  (unwrapped {:k {:value nil}}) => {:k nil}
  (unwrapped expected-wrapped-simple-document :value) => expected-simple-document
  (unwrapped (wrapped expected-simple-document)) => expected-simple-document)

(fact "create-dummy-document-data"
  (create-document-data schema (partial default-values {})) => expected-wrapped-simple-document)

(def expected-wrapped-simple-document-timestamped
  {:band {:name    {:value "" :modified 12312312123123}
          :genre   {:value "" :modified 12312312123123}
          :members {:0 {:name       {:value "" :modified 12312312123123}
                        :instrument {:value "" :modified 12312312123123}}}}})

(fact "timestampeds"
  (timestamped nil nil) => nil
  (timestamped {} nil) => {}
  (timestamped expected-wrapped-simple-document 12312312123123) => expected-wrapped-simple-document-timestamped)

(fact "schema-body-without-element-by-name"
  (schema-body-without-element-by-name (:body schema) "band") => []
  (schema-body-without-element-by-name (:body schema) "invalid") => (:body schema)
  (schema-body-without-element-by-name (:body schema) "members") => [{:name "band"
                                                                      :type :group
                                                                      :body [{:name "name"
                                                                              :type :string}
                                                                             {:name "link" :type :textlink :pseudo? true}
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

(defn- get-element-from-schema
  "Walks the schema along the given path until it either finds the end of the path or can't continue on it"
  [path schema]
  (loop [path path
         body (:body schema)]
    (if (or (nil? body)
            (empty? path))
      body
      (recur (rest path)
             (let [element (util/find-first #(-> % :name keyword (= (first path))) body)]
               (or (:body element)
                   element))))))

(defn- schema-has-party-id? [schema]
  (some #(get-element-from-schema % schema)
        party-doc-user-id-paths))

(facts "parties-only-on-party-docs"
  ;; These are facts instead of generated property tests because the number of non-party doc schemas
  ;; is so big and they are so deep structures that the Jenkins CI garbage collection and Java stack can't handle
  ;; generating and testing them at the same time
  (fact "party docs have party ids"
    (->> party-doc-schemas
         (every? #(if (contains? #{"tyonjohtaja-v2" "hakija-ark"} (-> % :info :name))
                    (not (schema-has-party-id? %))
                    (schema-has-party-id? %))))
    => true)

  (fact "non-party docs do not have party ids"
    (->> non-party-doc-schemas
         (every? #(not (schema-has-party-id? %))))
    => true))

(def special-subtypes #{:hakija :hakijan-asiamies :maksaja})

(defspec party-doc->user-role_special-subtypes-spec
  (prop/for-all [doc (gen/elements (->> party-doc-schemas
                                        (filter (comp special-subtypes :subtype :info))
                                        (map #(s/rename-keys % {:info :schema-info}))))]
                (= (get-in doc [:schema-info :subtype]) (party-doc->user-role doc))))

(def special-names #{"tyonjohtaja-v2"})

(fact "party-doc->user-role - foreman"
  (->> (ssg/generate (data-schema/doc-data-schema "tyonjohtaja-v2" true))
       party-doc->user-role) => :tyonjohtaja)

(defspec party-doc->user-role_default-spec
  (prop/for-all [doc (gen/elements (->> party-doc-schemas
                                        (remove (comp special-subtypes :subtype :info))
                                        (remove (comp special-names :name :info))
                                        (map #(s/rename-keys % {:info :schema-info}))))]
                (= (keyword (get-in doc [:schema-info :name])) (party-doc->user-role doc))))

(defspec party-doc->user-role_non-party-doc-spec
  (prop/for-all [doc (gen/elements (->> non-party-doc-schemas
                                        (map #(s/rename-keys % {:info :schema-info}))))]
                (nil? (party-doc->user-role doc))))

(comment

  ;; Get all possible party-roles
  (->> (map #(s/rename-keys % {:info :schema-info}) party-doc-schemas)
       (map party-doc->user-role)
       (distinct))

)

(env/with-feature-value :building-product-catalog false
  (facts "schema inclusions"
    (facts resolve-schema-flags
      (resolve-schema-flags nil) => #{}
      (resolve-schema-flags {}) => #{}
      (resolve-schema-flags {:organization {}}) => #{}
      (resolve-schema-flags {:organization {:rakennusluokat-enabled true}}) => #{}
      (resolve-schema-flags {:organization {:rakennusluokat-enabled true
                                            :krysp                  {:R {:version "2.2.4"}}}})
      => #{}
      (resolve-schema-flags {:application {:permitType "R"}}) => #{}
      (resolve-schema-flags {:application  {:permitType "R"}
                             :organization {:rakennusluokat-enabled true
                                            :krysp                  {:R {:version "2.2.4"}}}})
      => #{:rakennusluokka :rakval-224 :rakval-223 :yht-219}
      (resolve-schema-flags {:application  {:permitType "YA"}
                             :organization {:rakennusluokat-enabled true
                                            :krysp                  {:YA {:version "2.2.4"}}}})
      => #{:yht-219}
      (resolve-schema-flags {:application  {:permitType "YA"}
                             :organization {:rakennusluokat-enabled true
                                            :krysp                  {:YA {:version "unknown"}}}})
      => #{}
      (resolve-schema-flags {:application  {:permitType "YA"}
                             :organization {:rakennusluokat-enabled true
                                            :krysp                  {:R  {:version "2.2.4"}
                                                                     :YA {:version "2.2.4"}}}})
      => #{:yht-219}
      (resolve-schema-flags {:application  {:permitType "R"}
                             :organization (delay {:rakennusluokat-enabled true
                                                   :krysp                  {:R {:version "2.2.4"}}})})
      => #{:rakennusluokka :rakval-224 :rakval-223 :yht-219}
      (resolve-schema-flags {:application  {:permitType "R"}
                             :organization {:rakennusluokat-enabled true
                                            :krysp                  {:R {:version "2.2.3"}}}})
      => #{:rakval-223 :yht-219}
      (resolve-schema-flags {:application  {:permitType "R"}
                             :organization {:krysp {:R {:version "2.2.4"}}}})
      => #{:rakval-224 :rakval-223 :yht-219}
      (resolve-schema-flags {:application  {:permitType "R"}
                             :organization {:krysp {:R {:version "2.2.1"}}}}) => #{}
      (facts "ARK is treated like R"
        (resolve-schema-flags {:application {:permitType "ARK"}}) => #{}
        (resolve-schema-flags {:application  {:permitType "ARK"}
                               :organization {:rakennusluokat-enabled true
                                              :krysp                  {:R {:version "2.2.4"}}}})
        => #{:rakennusluokka :rakval-224 :rakval-223 :yht-219}
        (resolve-schema-flags {:application  {:permitType "ARK"}
                               :organization {:rakennusluokat-enabled true
                                              :krysp                  {:R {:version "2.2.3"}}}})
        => #{:rakval-223 :yht-219})
      (facts "Conversion"
        (resolve-schema-flags {:application {:facta-imported true}})
        => #{:conversion}
        (resolve-schema-flags {:application  {:facta-imported true
                                              :permitType     "R"}
                               :organization (delay {:krysp {:R {:version "2.2.3"}}})})
        => #{:conversion :rakval-223 :yht-219})
      (facts "Consent"
        (let [new-year  (date/timestamp "1.1.2025")
              runeberg  "5.2.2025"
              mayday    (date/timestamp "1.5.2025")
              flagtests (fn []
                          (mapv #(resolve-schema-flags {:application %})
                                ;; Six tests
                                [{:submitted mayday :facta-imported true}
                                 {:created new-year :submitted mayday}
                                 {:created new-year}
                                 {:submitted new-year}
                                 {:created mayday}
                                 {:submitted mayday}]))]
          (fact "No consent start date"
            (flagtests) => [#{:conversion} #{} #{} #{} #{} #{}]
            (provided (env/get-config) => {}))
          (fact "Consent start date"
            (flagtests) => [#{:conversion} #{:consent} #{} #{}
                            #{:consent} #{:consent}]
            (provided (env/get-config) => {:consent {:start-date runeberg}}))))
      (facts "Maalampokaivo count"
        (resolve-schema-flags {:organization {:kuntagml-maalampokaivo-count-enabled true}})
        => #{:maalampokaivo-count}
        (resolve-schema-flags {:organization {:kuntagml-maalampokaivo-count-enabled false}})
        => #{}
        (resolve-schema-flags {:organization {:kuntagml-maalampokaivo-count-enabled true
                                              :rakennusluokat-enabled              true
                                              :krysp                               {:R {:version "2.2.4"}}}
                               :application  {:permitType "R"}})
        => #{:maalampokaivo-count :rakennusluokka :rakval-224 :rakval-223 :yht-219}))

    (facts include-schema?
      (include-schema? #{} {:foo :bar}) => true
      (include-schema? #{} {:schema-exclude :x}) => true
      (include-schema? #{:x} {:schema-exclude :x}) => false
      (include-schema? #{} {:schema-include :x}) => false
      (include-schema? #{:x} {:schema-include :x}) => true
      (include-schema? #{:x} {:schema-include :x
                              :schmea-exclude :y}) => true
      (include-schema? #{:y} {:schema-include :x
                              :schema-exclude :y}) => false
      (include-schema? #{:x :y} {:schema-include :x
                                 :schema-exclude :y}) => false
      (include-schema? #{:a :b} {:schema-include :x
                                 :schema-exclude :y}) => false)

    (facts exclude-schema?
      (exclude-schema? #{} {:foo :bar}) => false
      (exclude-schema? #{} {:schema-exclude :x}) => false
      (exclude-schema? #{:x} {:schema-exclude :x}) => true
      (exclude-schema? #{} {:schema-include :x}) => true
      (exclude-schema? #{:x} {:schema-include :x}) => false
      (exclude-schema? #{:x} {:schema-include :x
                              :schmea-exclude :y}) => false
      (exclude-schema? #{:y} {:schema-include :x
                              :schema-exclude :y}) => true
      (exclude-schema? #{:x :y} {:schema-include :x
                                 :schema-exclude :y}) => true
      (exclude-schema? #{:a :b} {:schema-include :x
                                 :schema-exclude :y}) => true)

    (let [schema (-> {:info           {:name "band"}
                      :schema-exclude :nothing
                      :body
                      [{:name "band"
                        :type :group
                        :body
                        [{:name "name" :type :string}
                         {:name "link" :type :textlink :schema-include :links}
                         {:name "genre" :type :string}
                         {:name           "members"
                          :schema-exclude :summary
                          :type           :group
                          :body           [{:name "name" :type :string}
                                           {:name "twitter" :type :textlink :schema-include :links}
                                           {:name "instrument" :type :string}]}]}]}
                     flags/with-flag-paths)]
      (facts "schema flags"
        (flags/get-flag-paths schema) => {:exclude {:nothing [[]]
                                                    :summary [[:body 0 :body 3]]}
                                          :include {:links [[:body 0 :body 1]
                                                            [:body 0 :body 3 :body 1]]}})

      (facts strip-exclusions
        (strip-exclusions {} schema)
        => {:info           {:name "band"}
            :schema-exclude :nothing
            :body
            [{:name "band"
              :type :group
              :body
              [{:name "name" :type :string}
               {:name "genre" :type :string}
               {:name           "members"
                :schema-exclude :summary
                :type           :group
                :body           [{:name "name" :type :string}
                                 {:name "instrument" :type :string}]}]}]}

        (strip-exclusions {} schema)
        => {:info           {:name "band"}
            :schema-exclude :nothing
            :body
            [{:name "band"
              :type :group
              :body
              [{:name "name" :type :string}
               {:name "link" :type :textlink :schema-include :links}
               {:name "genre" :type :string}
               {:name           "members"
                :schema-exclude :summary
                :type           :group
                :body           [{:name "name" :type :string}
                                 {:name "twitter" :type :textlink :schema-include :links}
                                 {:name "instrument" :type :string}]}]}]}
        (provided (resolve-schema-flags anything) => #{:links})

        (strip-exclusions {} schema)
        => {:info           {:name "band"}
            :schema-exclude :nothing
            :body
            [{:name "band"
              :type :group
              :body
              [{:name "name" :type :string}
               {:name "link" :type :textlink :schema-include :links}
               {:name "genre" :type :string}]}]}
        (provided (resolve-schema-flags anything) => #{:links :summary})

        (strip-exclusions {} schema)
        => {:info           {:name "band"}
            :schema-exclude :nothing
            :body
            [{:name "band"
              :type :group
              :body
              [{:name "name" :type :string}
               {:name "genre" :type :string}]}]}
        (provided (resolve-schema-flags anything) => #{:summary})

        (strip-exclusions {} schema) => nil
        (provided (resolve-schema-flags anything) => #{:nothing})))))

(defn strip-exclusions-old
  "Original (slow) implementation of `strip-exclusions`"
  [command schema]
  (let [flags (resolve-schema-flags command)]
    (walk/postwalk (fn [form]
                     (let [form (when (include-schema? flags form)
                                  form)]
                       (util/pcond-> form
                         :body (update :body (partial remove nil?)))))
                   schema)))

(def all-schemas (vals (first (vals (schemas/get-all-schemas)))))

(defspec strip-exclusions-schemas-spec
  (prop/for-all [flags (gen/set (gen/elements [:conversion
                                               :rakennusluokka
                                               :rakval-223
                                               :trakval-224
                                               :rakval-226
                                               :yht-219
                                               :yht-2111
                                               :ryhti-data-2026]))
                 schema (gen/elements all-schemas)]
    ;; Provide the flags directly instead of a mock command
    (with-redefs [tools/resolve-schema-flags identity]
      (let [old-impl (-> (strip-exclusions-old flags schema)
                         ;; remove flag paths, to mimic new implementation
                         flags/clear-flag-paths)
            new-impl (tools/strip-exclusions flags schema)]
        (= old-impl new-impl)))))

(facts "schema alteration"
  (let [schema {:name "schema"
                :body [{:name "band"
                        :type :group
                        :body [{:name "name"
                                :type :string}
                               {:name      "members"
                                :type      :group
                                :repeating true
                                :body      [{:name "name"
                                             :type :string}
                                            {:name "instrument"
                                             :type :string}]}]}]}]

    (fact "Original schema returned when no alterations"
      (alter-schema schema)
      => schema)

    (fact "Original schema returned when no matching alterations"
      (alter-schema schema
                    ["uh oh" :foo :bar])
      => schema)

    (fact "Single field can be added"
      (alter-schema schema
                    ["schema" :is-schema? true])
      => {:name       "schema"
          :is-schema? true
          :body       [{:name "band"
                        :type :group
                        :body [{:name "name"
                                :type :string}
                               {:name      "members"
                                :type      :group
                                :repeating true
                                :body      [{:name "name"
                                             :type :string}
                                            {:name "instrument"
                                             :type :string}]}]}]})

    (fact "Single field can be modified"
      (alter-schema schema
                    ["band" :type :list])
      => {:name "schema"
          :body [{:name "band"
                  :type :list
                  :body [{:name "name"
                          :type :string}
                         {:name      "members"
                          :type      :group
                          :repeating true
                          :body      [{:name "name"
                                       :type :string}
                                      {:name "instrument"
                                       :type :string}]}]}]})

    (fact "Multiple field(s) and values can be added & modified"
      (alter-schema schema
                    ["band" :min 1 :type :list]
                    ["members" :max 10])
      => {:name "schema"
          :body [{:name "band"
                  :type :list
                  :min  1
                  :body [{:name "name"
                          :type :string}
                         {:name      "members"
                          :type      :group
                          :repeating true
                          :max       10
                          :body      [{:name "name"
                                       :type :string}
                                      {:name "instrument"
                                       :type :string}]}]}]})

    (fact "Alteration affects all fields where the field name matches"
      (alter-schema schema
                    ["name" :foo? true])
      => {:name "schema"
          :body [{:name "band"
                  :type :group
                  :body [{:name "name"
                          :foo? true
                          :type :string}
                         {:name      "members"
                          :type      :group
                          :repeating true
                          :body      [{:name "name"
                                       :foo? true
                                       :type :string}
                                      {:name "instrument"
                                       :type :string}]}]}]})))

(deftest flattened-test
  (testing "Flattens lists of maps to a single map"
    (is (= {:a 1 :b 2 :c 3}
           (flattened [{:a 1} {:b 2} {:c 3}])))
    (is (= {:x 10 :y 20}
           (flattened [{:x 10} {:y 20}])))
    (is (= {:key "value"}
           (flattened [{:key "value"}]))))

  (testing "Later maps override earlier maps when keys conflict"
    (is (= {:a 3}
           (flattened [{:a 1} {:a 2} {:a 3}])))
    (is (= {:x 1 :y 2}
           (flattened [{:x 1 :y 1} {:y 2}]))))

  (testing "Removes map entries with empty map values"
    (is (= {:a 1}
           (flattened {:a 1 :b {}})))
    (is (= {}
           (flattened {:empty-map {}})))
    (is (= {:outer {:inner 42}}
           (flattened {:outer {:inner 42 :empty {}}}))))

  (testing "Keeps map entries with non-empty map values"
    (is (= {:a 1 :b {:c 2}}
           (flattened {:a 1 :b {:c 2}})))
    (is (= {:nested {:deep {:value 123}}}
           (flattened {:nested {:deep {:value 123}}}))))

  (testing "Works with nested structures"
    (is (= {:outer {:a 1 :b 2}}
           (flattened {:outer [{:a 1} {:b 2}]})))
    (is (= {:level1 {:level2 {:x 10}}}
           (flattened {:level1 {:level2 [{:x 10}]}}))))

  (testing "Handles empty collections"
    (is (= [] (flattened [])))
    (is (= {} (flattened {}))))

  (testing "Preserves non-map sequences"
    (is (= [1 2 3] (flattened [1 2 3])))
    (is (= [[1 2] [3 4]] (flattened [[1 2] [3 4]]))))

  (testing "Preserves primitive values"
    (is (= {:a 1 :b "string" :c true}
           (flattened {:a 1 :b "string" :c true})))
    (is (= 42 (flattened 42)))
    (is (= "hello" (flattened "hello")))
    (is (= nil (flattened nil))))

  (testing "Removes entries with empty maps at any depth"
    (is (= {:keep {:this {:value 1}}}
           (flattened {:keep    {:this {:value 1 :remove {}}}
                       :discard {}}))))

  (testing "Handles vectors of maps"
    (is (= {:a 1 :b 2}
           (flattened [{:a 1} {:b 2}])))
    (is (= {:x 10 :y 20}
           (flattened (vec [{:x 10} {:y 20}])))))

  (testing "Complex nested scenario"
    (is (= {:data {:users    {:alice 1 :bob 2}
                   :settings {:theme "dark"}}}
           (flattened {:data {:users    [{:alice 1} {:bob 2}]
                              :settings [{:theme "dark"}]
                              :empty    {}}}))))

  (testing "Mixed list with maps and non-maps"
    ;; Only flattens if first element is a map
    (is (= [1 {:a 2}]
           (flattened [1 {:a 2}]))))

  (testing "Empty nested maps are removed recursively"
    (is (= {:a {:b 1}}
           (flattened {:a {:b 1 :c {} :d {:e {}}}})))))
