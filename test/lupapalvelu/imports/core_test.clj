(ns lupapalvelu.imports.core-test
  (:require [clojure.test :refer :all]
            [lupapalvelu.imports.core :as im]
            [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.test-util :refer [err]]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]))

(def title-io #'im/make-title-in-outs)

(deftest make-title-in-outs-test
  (testing "do nothing"
    (is (nil? (title-io {})))
    (is (nil? (title-io nil))))
  (testing "all in"
    (is (= {:in {:title.fi "moro"
                 :title.sv "hej"
                 :title.en "howdy"}}
           (title-io {:fi "moro" :sv "hej" :en "howdy"}))))
  (testing "all out"
    (is (= {:out #{:title.fi :title.sv :title.en}}
           (title-io {:fi "" :sv " " :en nil}))))
  (testing "mixed"
    (is (= {:in  {:title.fi "moro"}
            :out #{:title.sv}}
           (title-io {:fi " moro " :sv ""})))))

(def column-io #'im/make-column-in-outs)

(deftest make-column-in-outs-test
  (testing "do nothing"
    (is (nil? (column-io nil nil)))
    (is (nil? (column-io {} {})))
    (is (nil? (column-io {:columns [{:id "one"} {:id "two"}]}
                         {:id "three"})))
    (is (nil? (column-io {:columns [{:id "one"} {:id "two"}]}
                         {:id "two"})))
    (is (nil? (column-io {:columns [{:id "one"} {:id "two"}]}
                         {:id "two" :foo :bar :hello 10}))))
  (testing "title"
    (is (= {:in {:columns.$.title.fi "moro"
                 :columns.$.title.sv "hej"}}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}]}
                      {:id "one" :title {:fi "moro" :sv "hej"}})))
    (is (= {:in  {:columns.$.title.fi "moro"}
            :out [:columns.$.title.sv]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}]}
                      {:id "one" :title {:fi "moro" :sv "  "}})))
    (is (= {:out [:columns.$.title.sv :columns.$.title.fi]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}]}
                      {:id "one" :title {:fi "" :sv nil}}))))
  (testing "text"
    (is (= {:in  {:columns.$.type.lines 5
                  :columns.$.type.type  :text}
            :out [:columns.$.type.unit]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :type :text :lines "5"})))
    (is (= {:in  {:columns.$.type.type :text}
            :out [:columns.$.type.lines :columns.$.type.unit]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :type :text :lines 1})))
    (is (= {:in  {:columns.$.type.type :text}
            :out [:columns.$.type.unit]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :type :text}))))
  (testing "number"
    (is (= {:in  {:columns.$.type.unit "kpl"
                  :columns.$.type.type :number}
            :out [:columns.$.type.lines]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :type :number :unit "kpl"})))
    (is (= {:in  {:columns.$.type.type :number}
            :out [:columns.$.type.lines]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :type :number})))
    (is (= {:in  {:columns.$.type.type :number}
            :out [:columns.$.type.lines :columns.$.type.unit]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :type :number :unit ""}))))
  (doseq [t [:date :application-id :backend-id]]
    (testing (str t)
      (is (= {:in  {:columns.$.type.type t}
              :out [:columns.$.type.lines :columns.$.type.unit]}
             (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                   {:id "two" :type {:type :text}}]}
                        {:id "two" :type t})))))
  (testing "include"
    (is (= {:in {:columns.$.include true}}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :include true})))
    (is (= {:in {:columns.$.include false}}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :include false})))
    ;; Typing is caller's responsibility
    (is (= {:in {:columns.$.include "not a boolean?"}}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id "two" :include "not a boolean?"}))))
  (testing "mixed"
    (is (= {:in  {:columns.$.title.fi  "rahaa"
                  :columns.$.title.sv  "pengar"
                  :columns.$.type.type :number
                  :columns.$.type.unit "eur"}
            :out [:columns.$.type.lines :columns.$.title.en]}
           (column-io {:columns [{:id "one" :title {:fi "hei"}}
                                 {:id "two" :type {:type :text}}]}
                      {:id    "two" :type :number :unit "eur"
                       :title {:fi "rahaa" :sv "pengar" :en ""}}))))
  (testing "imported"
    (is (= {:in  {:columns.$.title.fi  "rahaa"
                  :columns.$.title.sv  "pengar"
                  :columns.$.type.unit "eur"}
            :out [:columns.$.title.en]}
           (column-io {:imported {}
                       :columns  [{:id "one" :title {:fi "hei"}}
                                  {:id "two" :type {:type :text}}]}
                      {:id    "two" :type :number :unit "eur"
                       :title {:fi "rahaa" :sv "pengar" :en ""}})))
    (is (nil? (column-io {:imported {}
                          :columns [{:id "one" :title {:fi "hei"}}
                                    {:id "two" :type {:type :text}}]}
                         {:id "two" :include true})))))

(def edit-cmu #'im/edit-config-mongo-updates)

(deftest edit-config-mongo-updates-test
  (testing "type and title"
    (is (= {:query {:_id "cfg", :columns {"$elemMatch" {:id "two"}}},
            :updates
            {$set
             {:title.fi            "Hyvää iltaa"
              :columns.$.type.type :number,
              :columns.$.type.unit "eur",
              :columns.$.title.fi  "rahaa",
              :columns.$.title.sv  "pengar"},
             $unset {:columns.$.type.lines true :columns.$.title.en true}}}
           (edit-cmu {:id      "cfg"
                      :columns [{:id "one" :title {:fi "hei"}}
                                {:id "two" :type {:type :text}}]}
                     {:data {:title  {:fi "Hyvää iltaa"}
                             :column {:id    "two" :type :number :unit "eur"
                                      :title {:fi "rahaa" :sv "pengar" :en ""}}}})))
    (is (nil? (edit-cmu {:id      "cfg"
                         :columns [{:id "one" :title {:fi "hei"}}
                                   {:id "two" :type {:type :text}}]}
                        {:data {:foo    {:fi "Hyvää iltaa"}
                                :column {:id    "three" :type :number :unit "eur"
                                         :title {:fi "rahaa" :sv "pengar" :en ""}}}})))))

(def cfg-id "67ea526e5e37ab2527ae12b2")
(def txt-id "67ea5c675e37ab2527ae12b3")
(def nope-id "67ea5c675e37ab2527ae12b4")
(def n-id "67ea5c675e37ab2527ae12b5")
(def date-id "67ea5c675e37ab2527ae12b6")
(def b-id "67ea5c675e37ab2527ae12b7")
(def app-id "67ea5c675e37ab2527ae12b8")


(def mongo-cfg {:id           cfg-id
                :organization "123-R"
                :title        {:fi "Testikonfiguraatio"}
                :rowdata      {:filename "foobar.xlsx"}
                :modified     {:user-id "bob" :ts 12345678}
                :columns      [{:id      txt-id
                                :title   {:fi "Selite"}
                                :type    {:type "text"}
                                :index   0
                                :include true}
                               {:id      nope-id
                                :title   {:fi "Ei kiitos"}
                                :type    {:type "text"}
                                :index   1
                                :include false}
                               {:id      n-id
                                :title   {:fi "Määrä"}
                                :type    {:type "number"
                                          :unit "kpl"}
                                :index   2
                                :include true}
                               {:id      date-id
                                :title   {:fi "Päiväys"}
                                :type    {:type "date"}
                                :index   3
                                :include true}
                               {:id      b-id
                                :title   {:fi "Kuntalupatunnus"}
                                :type    {:type "backend-id"}
                                :index   4
                                :include true}
                               {:id      app-id
                                :title   {:fi "LP-tunnus"}
                                :type    {:type "application-id"}
                                :index   5
                                :include true}]
                :previews     [{txt-id "One"     nope-id "nope" n-id "12" date-id "40729"
                                b-id   "town-id" app-id  "LP-123-2025-00001"}
                               {txt-id "Two"           nope-id "nope"
                                n-id   "1743418734076" date-id "1743418734076"
                                b-id   "city-id"       app-id  ""}
                               {txt-id "Three" nope-id "nope" n-id "bad" date-id "1"
                                b-id   ""      app-id  "LP-bad"}
                               {txt-id "Four" nope-id "nope" n-id "12,345" date-id "5.2.2025"}]})

(def cfg (im-sc/->config mongo-cfg))

(deftest reorder-columns-test
  (with-redefs [im/command->config (constantly cfg)
                mongo/update-by-id (fn [k id updates]
                                     {:pre [(= k :import-configs)
                                            (= id (:id cfg))]}
                                     updates)]
    (testing "fail"
      (doseq [ids [[] [txt-id n-id date-id b-id app-id]
                   [txt-id n-id date-id b-id app-id nope-id "foo-id"]]]
        (is (thrown-with-msg? Exception
                              #"error.columns-do-not-match"
                              (im/reorder-columns {:user {:id "bob"} :created 12345
                                                   :data {:columnIds ids}})))))
    (testing "success"
      (is (= {$set {:columns.0.index 5 :columns.1.index 4
                    :columns.2.index 3 :columns.3.index 2
                    :columns.4.index 1 :columns.5.index 0
                    :modified        {:user-id "bob" :ts 12345}}}
             (im/reorder-columns {:user {:id "bob"} :created 12345
                                  :data {:columnIds [app-id b-id date-id n-id
                                                     nope-id txt-id]}}))))))


(deftest previews-test
  (with-redefs [im/command->config (constantly cfg)]
    (testing "Four previews"
      (is (= [{txt-id  {:raw "One" :state :ok :value "One"}
               n-id    {:raw "12" :state :ok :value 12}
               date-id {:raw   "40729" :state :ok
                        :value (date/timestamp "5.7.2011")}
               b-id    {:raw "town-id" :state :ok :value "town-id"}
               app-id  {:raw "LP-123-2025-00001" :state :ok :value "LP-123-2025-00001"}}
              {txt-id  {:raw "Two" :state :ok :value "Two"}
               ;; Number too large
               n-id    {:raw "1743418734076" :state :error}
               date-id {:raw   "1743418734076" :state :ok
                        :value 1743368400000}
               b-id    {:raw "city-id" :state :ok :value "city-id"}}
              {txt-id  {:raw "Three" :state :ok :value "Three"}
               n-id    {:raw "bad" :state :error}
               date-id {:raw   "1" :state :ok
                        :value (date/timestamp "1.1.1900")}
               app-id  {:raw "LP-bad" :state :error}}
              {txt-id  {:raw "Four" :state :ok :value "Four"}
               n-id    {:raw "12,345" :state :ok :value 12.35}
               date-id {:raw   "5.2.2025" :state :ok
                        :value (date/timestamp "5.2.2025")}}]
             (im/previews {}))))))

(def make-cd #'im/make-cell-data)

(def column-by-id #(util/find-by-id % (:columns cfg)))
(def cell-id "67ea88de5e37ab2527ae12b4")

(defn cell? [column-id raw & {:keys [value error]}]
  (let [column (column-by-id column-id)
        data   (make-cd {:cfg     cfg
                         :user    {:id "bob-id"}
                         :created 12345}
                        column raw)
        base   {:cell {:id        cell-id
                       :column-id (:id column)
                       :type      (-> column :type :type)
                       :modified  {:user-id "bob-id"
                                   :ts      12345}
                       :original  {:raw raw}}}]
    (is (= (cond
             (not (:include column)) nil
             (ss/blank? raw)         (util/dissoc-in base [:cell :original])
             value                   (util/deep-merge base {:cell {:value    value
                                                                   :original {:value value}}})
             error                   (assoc base :error cell-id))
           data))))

(deftest make-cell-data-test
  (with-redefs [mongo/create-id (constantly cell-id)]
    (testing "text"
      (cell? txt-id "Howdy" :value "Howdy")
      (cell? txt-id ""))
    (testing "Not included"
      (cell? nope-id "Howdy")
      (cell? nope-id ""))
    (testing "number"
      (cell? n-id " -12.35 " :value -12.35)
      (cell? n-id " -12ddf.35 " :error true)
      (cell? n-id "  "))
    (testing "date"
      (cell? date-id " 1 " :value (date/timestamp "1.1.1900"))
      (cell? date-id "5.2.2025" :value (date/timestamp "5.2.2025"))
      (cell? date-id " -10000000 " :value (date/timestamp "31.12.1969"))
      (cell? date-id " 1743429737050 " :value 1743368400000)
      (cell? date-id " bad " :error true)
      (cell? date-id "  "))
    (testing "Backend-id"
      (cell? b-id "hello" :value "hello")
      (cell? b-id " "))
    (testing "Application-id"
      (cell? app-id "LX-186-2025-10001" :value "LX-186-2025-10001")
      (cell? app-id "hello" :error true)
      (cell? app-id " "))))

(deftest key-cell?-test
  (testing "true"
    (is (true? (im-sc/key-cell? {:type "backend-id"})))
    (is (true? (im-sc/key-cell? {:type :application-id}))))
  (testing "false"
    (doseq [t [:text :number :date]]
      (is (false? (im-sc/key-cell? {:type t})))
      (is (false? (im-sc/key-cell? {:type (name t)}))))))

(def key-m? #'im/key-missing?)

(deftest key-missing?-test
  (testing "true"
    (doseq [cells [[{:type "number" :value 1} {:type :backend-id}]
                   [{:type "number" :value 1} {:type "backend-id"}
                    {:type "application-id"}]
                   [{:type "number" :value 1} {:type :application-id}]
                   [{:type "number" :value 1}]]]
      (is (true? (key-m? cells)))))
  (testing "false"
    (doseq [cells [[{:type "number" :value 1} {:type :backend-id :value "hii"}]
                   [{:type "number" :value 1} {:type :backend-id :value "yeah"}
                    {:type "application-id" :value "LP-753-2025-11223"}]
                   [{:type "number" :value 1}
                    {:type "application-id" :value "LP-753-2025-11223"}]
                   [{:type "number" :value 1} {:type :backend-id :value "yeah"}
                    {:type "application-id"}]
                   [{:type "number" :value 1} {:type :backend-id}
                    {:type "application-id" :value "LP-753-2025-11223"}]]]
      (is (false? (key-m? cells))))))

(def make-rd #'im/make-row-document)

(defn column-cell-id [row column-id]
  (->> (:cells row)
       (util/find-by-key :column-id column-id)
       :id))

(deftest make-row-document-test
  (let [m {:cfg cfg :user {:id "bob"} :created 12345}]
    (testing "no errors"
      (let [row (make-rd m ["hello" "nope" "10" "1.4.2025" "123-000 R"])]
        (is (true? (v/hex-string? (:id row))))
        (is (= {:organization "123-R"
                :config-id    cfg-id
                :cells        [{:id        (column-cell-id row txt-id)
                                :column-id txt-id
                                :type      :text
                                :value     "hello"
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "hello" :value "hello"}}
                               {:id        (column-cell-id row n-id)
                                :column-id n-id
                                :type      :number
                                :value     10
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "10" :value 10}}
                               {:id        (column-cell-id row date-id)
                                :column-id date-id
                                :type      :date
                                :value     (date/timestamp "1.4.2025")
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw   "1.4.2025"
                                            :value (date/timestamp "1.4.2025")}}
                               {:id        (column-cell-id row b-id)
                                :column-id b-id
                                :type      :backend-id
                                :value     "123-000 R"
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "123-000 R" :value "123-000 R"}}
                               {:id        (column-cell-id row app-id)
                                :column-id app-id
                                :type      :application-id
                                :modified  {:ts 12345 :user-id "bob"}}]}
               (dissoc row :id)))))
    (testing "errors"
      (let [row          (make-rd m ["hello" "nope" "bad" "yikes"])
            n-cell-id    (column-cell-id row n-id)
            date-cell-id (column-cell-id row date-id)]
        (is (= {:organization "123-R"
                :config-id    cfg-id
                :cells        [{:id        (column-cell-id row txt-id)
                                :column-id txt-id
                                :type      :text
                                :value     "hello"
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "hello" :value "hello"}}
                               {:id        n-cell-id
                                :column-id n-id
                                :type      :number
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "bad"}}
                               {:id        date-cell-id
                                :column-id date-id
                                :type      :date
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "yikes"}}
                               {:id        (column-cell-id row b-id)
                                :column-id b-id
                                :type      :backend-id
                                :modified  {:ts 12345 :user-id "bob"}}
                               {:id        (column-cell-id row app-id)
                                :column-id app-id
                                :type      :application-id
                                :modified  {:ts 12345 :user-id "bob"}}]
                :errors       #{n-cell-id date-cell-id "key"}}
               (dissoc row :id))))

      (let [row         (make-rd m ["hello" "nope" "10" "1.4.2025" "123-000 R" "bad"])
            app-cell-id (column-cell-id row app-id)]
        (is (= {:organization "123-R"
                :config-id    cfg-id
                :cells        [{:id        (column-cell-id row txt-id)
                                :column-id txt-id
                                :type      :text
                                :value     "hello"
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "hello" :value "hello"}}
                               {:id        (column-cell-id row n-id)
                                :column-id n-id
                                :type      :number
                                :value     10
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "10" :value 10}}
                               {:id        (column-cell-id row date-id)
                                :column-id date-id
                                :type      :date
                                :value     (date/timestamp "1.4.2025")
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw   "1.4.2025"
                                            :value (date/timestamp "1.4.2025")}}
                               {:id        (column-cell-id row b-id)
                                :column-id b-id
                                :type      :backend-id
                                :value     "123-000 R"
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "123-000 R" :value "123-000 R"}}
                               {:id        app-cell-id
                                :column-id app-id
                                :type      :application-id
                                :modified  {:ts 12345 :user-id "bob"}
                                :original  {:raw "bad"}}]
                :errors       #{app-cell-id}}
               (dissoc row :id)))))
    (testing "All blanks (should not happen)"
      (let [row (make-rd m nil)
            ](is (= {:organization "123-R"
                     :config-id    cfg-id
                     :cells        [{:id        (column-cell-id row txt-id)
                                     :column-id txt-id
                                     :type      :text
                                     :modified  {:ts 12345 :user-id "bob"}}
                                    {:id        (column-cell-id row n-id)
                                     :column-id n-id
                                     :type      :number
                                     :modified  {:ts 12345 :user-id "bob"}}
                                    {:id        (column-cell-id row date-id)
                                     :column-id date-id
                                     :type      :date
                                     :modified  {:ts 12345 :user-id "bob"}}
                                    {:id        (column-cell-id row b-id)
                                     :column-id b-id
                                     :type      :backend-id
                                     :modified  {:ts 12345 :user-id "bob"}}
                                    {:id        (column-cell-id row app-id)
                                     :column-id app-id
                                     :type      :application-id
                                     :modified  {:ts 12345 :user-id "bob"}}]
                     :errors       #{"key"}}
                    (dissoc row :id)))))))

(defn cfg-check [cfg & ks]
  (with-redefs [mongo/select-one (constantly cfg)]
    ((apply im/config-exists ks) {:data {:organizationId "123-R"
                                         :configId       (mongo/create-id)}})))

(defn make-column
  ([col-type include?]
   (util/strip-nils {:id      (mongo/create-id)
                     :title   {:fi (name col-type)}
                     :type    {:type col-type}
                     :index   (rand-int 10)
                     :include include?}))
  ([col-type] (make-column col-type true))
  ([] (make-column :text true)))

(def not-found? (err :error.not-found))

(deftest config-exists-test
  (testing "Not found"
    (is (not-found? (cfg-check nil))))
  (let [cfg {:id           (mongo/create-id)
             :organization "123-R"
             :title        {:fi "Moro"}
             :rowdata      {:filename "source.xlsx"}
             :modified     {:user-id "bob" :ts 12345}
             :columns      [(make-column) (make-column :date)]}]
    (testing "Imported"
      (is (not-found? (cfg-check cfg :imported)))
      (is (nil? (cfg-check (assoc cfg :imported {:user-id "dot-id" :ts 34567})
                           :imported))))
    (testing "not-deleted"
      (is (nil? (cfg-check cfg :not-deleted)))
      (is (not-found? (cfg-check (assoc cfg :deleted {:user-id "dot-id" :ts 34567})
                                 :not-deleted))))
    (testing "Previews"
      (is (not-found? (cfg-check cfg :previews)))
      (is (nil? (cfg-check (assoc cfg :previews [{(mongo/create-id) "yeah"}])
                           :previews))))
    (testing "Draft"
      (is (nil? (cfg-check cfg :draft)))
      (is (not-found? (cfg-check (assoc cfg :imported {:user-id "dot-id" :ts 34567})
                                 :draft)))
      (is (not-found? (cfg-check (assoc cfg :importing-job (mongo/create-id))
                                 :draft))))

    (testing "key-column"
      (is (not-found? (cfg-check cfg :key-column)))
      (is (not-found? (cfg-check (assoc cfg :columns []) :key-column)))
      (is (nil? (cfg-check (update cfg :columns conj (make-column :application-id))
                           :key-column)))
      (is (not-found? (cfg-check (update cfg :columns conj (make-column :application-id false))
                                 :key-column)))
      (is (nil? (cfg-check (update cfg :columns conj (make-column :backend-id))
                           :key-column)))
      (is (not-found? (cfg-check (update cfg :columns conj (make-column :backend-id false))
                                 :key-column))))

    (testing "other-columns"
      (is (nil? (cfg-check cfg :other-columns)))
      (is (not-found? (cfg-check (assoc cfg :columns []) :other-columns)))
      (is (not-found? (cfg-check (assoc cfg :columns [(make-column :text false)
                                                      (make-column :application-id)
                                                      (make-column :backend-id)])
                                 :other-columns)))
      (is (nil? (cfg-check (assoc cfg :columns [(make-column :text false)
                                                (make-column :application-id)
                                                (make-column :backend-id)
                                                (make-column)])
                           :other-columns))))

    (testing "Mixed"
      (is (nil? (cfg-check cfg :other-columns :draft)))
      (is (not-found? (cfg-check cfg :other-columns :draft :previews)))
      (is (nil? (cfg-check (assoc cfg :previews [{(mongo/create-id) "yes"}])
                           :other-columns :draft :previews)))
      (is (not-found? (cfg-check cfg :key-column :imported)))
      (is (nil? (cfg-check (-> cfg
                               (assoc :imported {:user-id "enzo-id" :ts 56789})
                               (update :columns conj (make-column :backend-id)))
                           :key-column :imported))))))

(def mair #'im/make-application-import-row)

(deftest make-application-import-row-test
  (let [{txt-id :id :as txt-col}   (make-column :text nil)
        {date-id :id :as date-col} (make-column :date nil)
        {bid :id :as b-col}        (make-column :backend-id nil)
        {app-id :id :as app-col}   (make-column :application-id nil)
        cfg                        {:id           (mongo/create-id)
                                    :organization "123-R"
                                    :columns      [txt-col date-col b-col app-col]}
        cmd                        {:user {:id "bob-id"} :created 12345}
        user-ts                    {:user-id "bob-id" :ts 12345}]
    (testing "No backend id"
      (let [row (mair (assoc cmd :application {:id "LP-123-2025-00012"})
                      cfg)]
        (is (boolean (im-sc/->row row)))
        (is (= [{:column-id txt-id :modified user-ts :type :text}
                {:column-id date-id :modified user-ts :type :date}
                {:column-id bid :modified user-ts :type :backend-id}
                {:column-id app-id :modified user-ts :type :application-id}]
               (mapv #(select-keys % [:column-id :modified :type])
                      (:cells row))))
        (is (= {:config-id (:id cfg) :organization "123-R"
                :added     (assoc user-ts :id "LP-123-2025-00012")}
               (select-keys row [:config-id :organization :added])))
        (is (= [{:value "LP-123-2025-00012" :type :application-id}]
               (->> (:cells row)
                    (filter :value)
                    (map #(select-keys % [:value :type])))))))
    (testing "With backend-id"
      (let [row (mair (assoc cmd :application {:id       "LP-123-2025-00012"
                                               :verdicts [{:kuntalupatunnus "hello"}]})
                      cfg)]
        (is (= [{:value "hello" :type :backend-id}
                {:value "LP-123-2025-00012" :type :application-id}]
               (->> (:cells row)
                    (filter :value)
                    (map #(select-keys % [:value :type])))))))))
