(ns lupapalvelu.conversion.section-itest
  (:require [babashka.fs :as fs]
            [lupapalvelu.conversion.core :as conv]
            [lupapalvelu.conversion.document :as conv-doc]
            [lupapalvelu.conversion.itest-util :as conv-itest]
            [lupapalvelu.conversion.section :as conv-sec]
            [lupapalvelu.fixture.core :as fixture]
            [lupapalvelu.itest-system :as test-system]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.user :as usr]
            [midje.sweet :refer :all]
            [monger.operators :refer :all]
            [sade.util :as util]))

(set! *warn-on-reflection* true)

(defn check-metadata
  ([m value ts user]
   (fact "Metadata check"
     m => (just {:_value value
                 :_modified ts
                 :_user user})))
  ([m value ts]
   (check-metadata m value ts "batchrun-user"))
  ([m value]
   (check-metadata m value truthy)))

(defn batchrun-import [dir cfg]
  (let [filename (str (fs/path dir (str (random-uuid) ".edn")))]
    (util/write-edn-file filename cfg)
    (conv/execute-conversion-command {:cmd      :import
                                      :cfgfile  filename
                                      :cmd-help (constantly nil)
                                      :system   (test-system/get-common-components)})))

(with-test-system (-> {:fixture "minimal"}
                      test-system/with-remote-components)
  (let [test-dir              (conv-itest/mk-test-dir)
        ^String test-contents (->> (slurp conv-itest/testfile)
                                   test-system/fix-urls)
        testfile-name         (fs/file-name conv-itest/testfile)
        target-path           (fs/path test-dir testfile-name)
        _                     (fs/write-bytes target-path (.getBytes test-contents))
        edn-file              (conv-itest/write-config test-dir "092-R" :glob testfile-name)
        execute-import        (partial batchrun-import test-dir)]

    (with-redefs
      [;; By default, the local action users are taken from the minimal.
       fixture/find-user-by-apikey (fn [_fixture apikey]
                                     (usr/get-user-with-apikey apikey))]
      (against-background
          [(after :contents (conv-itest/rm-test-dir test-dir))]

        (with-local-actions
          (fact "Disable archiving in 092-R"
            (command admin :set-organization-boolean-attribute
                     :organizationId "092-R"
                     :enabled false
                     :attribute "permanent-archive-enabled") => ok?)

          (facts "Section schemas"
            (fact "Import bad schema"
              (conv-sec/import-schemas [{:op         :add
                                         :name       "bad"
                                         :rows       []
                                         :dictionary {:bad {:foo {}}}}])
              => (throws #"Input to import-schemas does not match schema"))
            (fact "Upsert good schema"
              (conv-sec/import-schemas [{:op         :add
                                         :name       "good"
                                         :title      :my-title
                                         :dictionary {:good {:toggle {}}}
                                         :rows       []}])
              => (just (just {:id         truthy
                              :name       "good"
                              :dictionary {:good {:toggle {}}}
                              :title      :my-title
                              :rows       []})))
            (fact "Upserting existing schema needs replace or merge operation"
              (conv-sec/import-schemas  [{:op         :add
                                          :name       "good"
                                          :title      :my-title
                                          :dictionary {:good {:toggle {}}}
                                          :rows       []}])
              => (throws #"Schema good already exists")
              (conv-sec/import-schemas  [{:op         :replace
                                          :name       "good"
                                          :title      :new-title
                                          :dictionary {:howdy {:text {}}}
                                          :rows       []}])
              => (just (just {:id         truthy
                              :name       "good"
                              :dictionary {:howdy {:text {}}}
                              :title      :new-title
                              :rows       []}))
              (conv-sec/import-schemas [{:op         :merge
                                         :name       "good"
                                         :dictionary {:good {:toggle {}}}}])
              => (just (just {:id         truthy
                              :name       "good"
                              :dictionary {:good  {:toggle {}}
                                           :howdy {:text {}}}
                              :title      :new-title
                              :rows       []})))

            (fact "Import schema names must be unique"
              (conv-sec/import-schemas [{:op         :add
                                         :name       "menu"
                                         :title      :menu-title
                                         :dictionary {:color {:select {:items [:red :green :blue]}}}
                                         :rows       []}
                                        {:op         :merge
                                         :name       "menu"
                                         :title      :new-title
                                         :dictionary {:drink {:select {:items [:soda :beer :wine]}}}
                                         :rows       []}])
              => (throws #"Duplicate name menu in import"))

            (fact "Add another schema"
              (execute-import {:op    :schemas
                               :items [{:op         :add
                                        :name       "menu"
                                        :title      :menu-title
                                        :dictionary {:color {:select {:items [:red :green :blue]}}
                                                     :drink {:select {:items [:soda :beer :wine]}}}
                                        :rows       []}]})
              => nil
              (mongo/any? :conversion-section-schemas {:name "menu"})
              => true))

          (facts "Conversion sections"
            (fact "Conversion store"
              (conv/execute-conversion-command {:cmd     :store
                                                :cfgfile edn-file
                                                :system  (test-system/get-common-components)}) => nil)
            (fact "Conversion convert"
              (conv/execute-conversion-command {:cmd     :convert
                                                :cfgfile edn-file
                                                :system  (test-system/get-common-components)}) => nil)

            (let [get-doc         #(conv-doc/document {:converted true})
                  {:keys  [backend-id]
                   app-id :LP-id} (get-doc)
                  import-sections #(conv-sec/import-sections (get-doc) %)]
              (fact "Add luukas as reader to 092-R"
                (mongo/update-by-id :users luukas-id {$push {:orgAuthz.092-R :reader}})
                (query luukas :application :id app-id) => ok?)
              (fact "Auth pena to the application"
                (mongo/update-by-id :applications app-id {$push {:auth {:id   pena-id
                                                                        :role :writer}}})
                (query pena :application :id app-id) => ok?)
              (fact "Initially, no sections"
                (:sections (get-doc)) => nil?
                (query vantaa-r-biller :conversion-sections :id app-id)
                => (err :error.not-found)
                (query luukas :conversion-sections :id app-id)
                => (err :error.not-found)
                (query pena :conversion-sections :id app-id)
                => (err :error.unauthorized))
              (fact "Import bad section"
                (import-sections [{:op          :add
                                   :name        "one"
                                   :schema-name "not-found"
                                   :data        {}}])
                => (throws #"Schema not-found not found.")
                (import-sections  [{:op          :add
                                    :name        "one"
                                    :schema-name "good"
                                    :data        {:bad "field"}}])
                => (throws #"Bad data in section one:"))

              (fact "Import good, but duplicate sections"
                (import-sections [{:op          :add
                                   :name        "one"
                                   :schema-name "good"
                                   :data        {:howdy "hello"}
                                   :title       :title1}
                                  {:op          :add
                                   :name        "one"
                                   :schema-name "good"
                                   :data        {}}])
                => (throws #"Duplicate name one"))

              (fact "Import good section"
                (execute-import {:organization-id "092-R"
                                 :op              :sections
                                 :items           {backend-id [{:op          :add
                                                                :name        "one"
                                                                :schema-name "good"
                                                                :data        {:howdy "hello"}
                                                                :title       :my-title}]}})
                => nil
                (let [{:keys [sections]} (get-doc)
                      one                (first sections)
                      ts                 (:modified one)]
                  ts => truthy
                  sections => [one]
                  one => (just {:data        {:howdy {:_modified ts
                                                      :_user     "batchrun-user"
                                                      :_value    "hello"}}
                                :id          truthy
                                :modified    ts
                                :name        "one"
                                :schema-name "good"
                                :title       :my-title})))
              (fact "Section names must be unique within document"
                (import-sections [{:op          :add
                                   :name        "one"
                                   :schema-name "good"
                                   :data        {:howdy "bye"}
                                   :title       :my-title}])
                => (throws #"Section one already exists."))
              (facts "Conversion sections query now works"
                (let [good? (just {:ok       true
                                   :sections (just (just {:data     {:howdy "hello"}
                                                          :id       truthy
                                                          :modified pos?
                                                          :schema   {:dictionary {:good  {:toggle {}}
                                                                                  :howdy {:text {}}}
                                                                     :rows       []}
                                                          :title    :my-title}))})]
                  (fact "Authority"
                    (query vantaa-r-biller :conversion-sections :id app-id) => good?)
                  (fact "Reader"
                    (query luukas :conversion-sections :id app-id) => good?)
                  (fact "Applicant"
                    (query pena :conversion-sections :id app-id)
                    => (err :error.unauthorized))))
              (fact "Add another section and upsert the first one"
                (execute-import {:organization-id "092-R"
                                 :op              :sections
                                 :items           {backend-id [{:op          :add
                                                                :name        "two"
                                                                :schema-name "menu"
                                                                :data        {:color "red"
                                                                              :drink :wine}}
                                                               {:op          :merge
                                                                :name        "one"
                                                                :schema-name "good"
                                                                :data        {:howdy "Ni hao"
                                                                              :good  false}}]} })
                => nil
                (let [[one two
                       :as xs] (:sections (get-doc))]
                  xs => [one two]
                  (check-metadata (-> one :data :howdy) "Ni hao")
                  (check-metadata (-> one :data :good) false)
                  (:title one) => :my-title
                  two => (contains {:name        "two"
                                    :schema-name "menu"})
                  (check-metadata (-> two :data :color) "red")
                  ;; Keyword values are stored as strings in mongo.
                  (check-metadata (-> two :data :drink) "wine")))

              (facts "Edit sections"
                (let [{:keys [sections]} (query vantaa-r-biller :conversion-sections
                                                :id app-id)
                      [one-id two-id]    (map :id sections)]
                  (fact "Initial data"
                    sections => (just (just {:id       one-id
                                             :modified pos?
                                             :schema   truthy
                                             :data     {:good false :howdy "Ni hao"}
                                             :title    :my-title})
                                      (just {:id       two-id
                                             :modified pos?
                                             :data     {:color "red" :drink "wine"}
                                             :schema   truthy
                                             :title    :menu-title})))
                  (facts "Bad edits"
                    (fact "No such section"
                      (command vantaa-r-biller :edit-conversion-section
                               :id app-id
                               :section-id "bad-id"
                               :path [:foo]
                               :value "whatever")
                      => (err :error.not-found))
                    (fact "Bad path or value"
                      (command vantaa-r-biller :edit-conversion-section
                               :id app-id
                               :section-id one-id
                               :path [:foo]
                               :value "whatever")
                      => (err :error.invalid-value-path)
                      (command vantaa-r-biller :edit-conversion-section
                               :id app-id
                               :section-id two-id
                               :path [:drink]
                               :value "tea")
                      => {:ok     true
                          :errors [[[:drink] :error.invalid-value]]}))
                  (fact "Reader or applicant cannot edit"
                    (doseq [apikey [luukas pena]]
                      (command apikey :edit-conversion-section
                               :id app-id
                               :section-id two-id
                               :path [:drink]
                               :value "beer")
                      => (err :error.unauthorized)))
                  (facts "Good edits"
                    (command vantaa-r-biller :edit-conversion-section
                             :id app-id
                             :section-id two-id
                             :path [:drink]
                             :value "beer")
                    => {:ok true :modified 123456789}
                    (provided
                      (sade.core/now) => 123456789)
                    (let [{:keys [modified data]} (->> (mongo/select-one :conversion
                                                                         {:LP-id app-id}
                                                                         [:sections])
                                                       :sections
                                                       (util/find-by-id two-id))]
                      modified => 123456789
                      (:drink data) => {:_user     (min-util/id-for-key vantaa-r-biller)
                                        :_modified 123456789
                                        :_value    "beer"})))))))))))
