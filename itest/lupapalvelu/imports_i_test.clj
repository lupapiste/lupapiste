(ns lupapalvelu.imports-i-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer :all]
            [lupapalvelu.imports.core :as im]
            [lupapalvelu.imports.schemas :as im-sc]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.json :as json]
            [lupapalvelu.minimal-util :as min-util]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.reports.fastexcel :as fast]
            [lupapalvelu.storage.file-storage :as file-storage]
            [lupapalvelu.storage.object-storage :as object-storage]
            [midje.sweet :refer [contains just truthy falsey]]
            [monger.operators :refer :all]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.validators :as v]))

(set! *warn-on-reflection* true)

(use-fixtures :each (local-fixture-factory :db-name "test_imports"))

(defn hex-string? [s]
  (and (ss/not-blank? s) (v/hex-string? s)))

(defn upload-excel [apikey organization-id filename]
  (let [file (fs/file filename)]
    (-> (local-raw apikey :upload-import-excel
                   :organizationId organization-id
                   :file {:filename     (fs/file-name file)
                          :content-type "application/octet-stream"
                          :tempfile     file
                          :size         (fs/size file)})
        (update :body json/decode true)
        (select-keys [:status :body]))))

(defn wait-for-organization-job [apikey organization-id job-id]
  {:pre [job-id]}
  (letfn [(query-job []
            (-> (query apikey :organization-jobs
                       :organizationId organization-id
                       :jobIds [job-id]
                       :poll true)
                :jobs first))]
    (loop [job (query-job)]
      (or job (recur (query-job))))))

(def not-found? (err :error.not-found))

(def schema-fail? (err :error.illegal-value:schema-validation))

(defn validation-error? [column-type {:keys [ok text validation-error]}]
  (and (false? ok)
       (util/=as-kw text :error.invalid-value)
       (util/=as-kw validation-error column-type)))

(defn draft-column? [column index title]
  (testing title
    (is (= {:index index :include true
            :title {:fi title :sv title}
            :type  {:type :text}}
           (select-keys column [:index :include :title :type])))))

(defn modified? [{:keys [modified]}]
  (and (ss/not-blank? (:user-id modified))
       (pos? (:ts modified))))

(defn deleted? [{:keys [deleted]}]
  (and (ss/not-blank? (:user-id deleted))
       (pos? (:ts deleted))))

(defn edit-config [apikey organization-id cfg-id & {:as params}]
  (command apikey :update-import-config
           (merge {:organizationId organization-id
                   :configId cfg-id}
                  params)))

(defn previews
  ([apikey organization-id cfg-id column-id]
   (query apikey :import-config-previews
          (util/assoc-when {:organizationId organization-id
                            :configId       cfg-id}
                           :columnId column-id)))
  ([apikey organization-id cfg-id]
   (previews apikey organization-id cfg-id nil)))

(defn get-config [apikey organization-id cfg-id]
  (:config (query apikey :get-import-config
                  :organizationId organization-id
                  :configId cfg-id)))

(defn edit-cell [apikey organization-id row-id cell-id value]
  (command apikey :edit-import-row
           :organizationId organization-id
           :rowId row-id
           :cellId cell-id
           :value value))

(defn get-row-ids [apikey org-id cfg-id & {:keys [errors deleted added]}]
  (->> (util/assoc-when {:organizationId org-id
                         :configId       cfg-id}
                        :deleted deleted
                        :errors  errors
                        :added   added)
       (query apikey :import-config-row-ids)
       :row-ids))

(defn get-row [apikey organization-id cfg-id row-id]
  (->> (query apikey :import-config-rows
              :organizationId organization-id
              :configId cfg-id
              :rowIds [row-id])
       :rows first))

(deftest upload-import-excel-test
  (testing "No configurations."
    (is (= {:ok true :configs nil}
           (query sipoo :organization-import-configs
                  :organizationId "753-R"))))
  (testing "Upload."
    (let [{:keys [body status]} (upload-excel sipoo "753-R"
                                              "dev-resources/simple-table.xlsx")
          job-id                (:job-id body)
          job                   (wait-for-organization-job sipoo "753-R" job-id)
          cfg-id                (-> job :value :config-id)]
      (is (= 200 status))
      (is (hex-string? job-id))
      (is (hex-string? cfg-id))
      (is (= {:type         "upload-import-excel"
              :organization "753-R"
              :status       "done"
              :version      0
              :user         {:id        "50ac77ecc2e6c2ea6e73f83e"
                             :username  "sipoo"
                             :firstName "Simo"
                             :lastName  "Suurvisiiri"
                             :role      "authority"}
              :data         {:filename "simple-table.xlsx"}}
             (select-keys job [:type :organization :status :version
                               :user :data])))
      (testing "draft configuration is created."
        (let [cfg                      (im/get-configuration "753-R" cfg-id)
              [{one-id :id :as col1} {two-id :id :as col2} {three-id :id :as col3}
               {four-id :id :as col4}] (:columns cfg)
              {:keys [filename object-key
                      n]}              (:rowdata cfg)]
          (draft-column? col1 0 "ÄläMölÖ title")
          (draft-column? col2 1 "Other id (foo)")
          (draft-column? col3 2 "Meeting date")
          (draft-column? col4 3 "Amount")

          (testing "Rowdata."
            (is (true? (file-storage/file-exists-in? object-storage/import-bucket
                                                     object-key)))
            (is (ss/starts-with? object-key "753-R/import-"))
            (is (= 3 n))
            (is (= "simple-table.xlsx" filename)))
          (testing "organization-import-configs."
            (is (= {:ok true :configs nil}
                   (query sipoo-ya :organization-import-configs
                          :organizationId "753-YA")))
            (let [front-cfg (im/get-configuration "753-R" cfg-id true)]
              (is (= {:ok true :configs [front-cfg]}
                     (query sipoo :organization-import-configs
                            :organizationId "753-R")))
              (is (= (-> cfg
                         (assoc :rowdata {:filename filename :n 3})
                         (dissoc :previews))
                     front-cfg))
              (testing "get-import-config."
                (is (not-found? (query sipoo :get-import-config
                                       :organizationId "753-R"
                                       :configId (mongo/create-id))))
                (is (= front-cfg
                       (get-config sipoo "753-R" cfg-id))))))
          (testing "Previews."
            (is (not-found? (previews sipoo "753-R" (mongo/create-id))))
            (is (= [{one-id   {:raw "Hello" :state :ok :value "Hello"}
                     two-id   {:raw "world" :state :ok :value "world"}
                     three-id {:raw "45516" :state :ok :value "45516"}
                     four-id  {:raw "1" :state :ok :value "1"}}
                    {one-id   {:raw "Howdy" :state :ok :value "Howdy"}
                     three-id {:raw "43466" :state :ok :value "43466"}
                     four-id  {:raw "2" :state :ok :value "2"}}
                    {one-id   {:raw   "Something (totally) different"
                               :state :ok
                               :value "Something (totally) different"}
                     two-id   {:raw "123" :state :ok :value "123"}
                     three-id {:raw "45693" :state :ok :value "45693"}
                     four-id  {:raw "2.5" :state :ok :value "2.5"}}]
                   (:previews (query sipoo :import-config-previews
                                     :organizationId "753-R"
                                     :configId cfg-id)))))
          (testing "Previews for one column."
            (is (not-found? (previews sipoo "753-R" cfg-id (mongo/create-id))))
            (is (= [{three-id {:raw "45516" :state :ok :value "45516"}}
                    {three-id {:raw "43466" :state :ok :value "43466"}}
                    {three-id {:raw "45693" :state :ok :value "45693"}}]
                   (:previews (previews sipoo "753-R" cfg-id three-id)))))
          (let [edit-cfg (partial edit-config sipoo "753-R" cfg-id)]
            (testing "Edit config."
              (testing "Fail."
                (is (not-found? (edit-config sipoo "753-R" (mongo/create-id)
                                             :title {:fi "moi"})))
                (is (not-found? (edit-config sipoo-ya "753-YA" cfg-id
                                             :title {:fi "moi"})))
                (is (schema-fail? (edit-cfg :foobar 10))))

              (testing "Exclude second column."
                (is (modified? (edit-cfg :column {:id two-id :include false})))
                (is (nil? (:previews (previews sipoo "753-R" cfg-id two-id)))))
              (testing "Edit main title."
                (is (modified? (edit-cfg :title {:fi " Otsikko "
                                                 :sv " Titel "})))
                (is (= {:fi "Otsikko" :sv "Titel"}
                       (:title (get-config sipoo "753-R" cfg-id)))))

              (testing "Edit column type and title."
                (testing "Good dates."
                  (is (modified? (edit-cfg :column {:id    three-id
                                                    :title {:fi "Pvm"}
                                                    :type  "date"}) ))
                  (is (= (mapv date/timestamp ["12.8.2024" "1.1.2019" "5.2.2025"])
                         (->> (previews sipoo "753-R" cfg-id three-id)
                              :previews
                              (mapv #(:value (get % three-id)))))))
                (testing "Bad numbers."
                  (is (modified? (edit-cfg :column {:id   one-id
                                                    :type "number"
                                                    :unit "m2"}) ))
                  (is (= {:type :number :unit :m2}
                         (->> (get-config sipoo "753-R" cfg-id)
                              :columns
                              (util/find-by-id one-id)
                              :type)))
                  (is (= [{:raw "Hello" :state :error}
                          {:raw "Howdy" :state :error}
                          {:raw "Something (totally) different" :state :error}]
                         (->> (previews sipoo "753-R" cfg-id one-id)
                              :previews
                              (mapcat vals)))))
                (testing "Back to texts."
                  (is (modified? (edit-cfg :column {:id    one-id
                                                    :type  "text"
                                                    :lines 20}) ))
                  (is (= {:type :text :lines 20}
                         (->> (get-config sipoo "753-R" cfg-id)
                              :columns
                              (util/find-by-id one-id)
                              :type)))
                  (is (= [{:raw "Hello" :state :ok :value "Hello"}
                          {:raw "Howdy" :state :ok :value "Howdy"}
                          {:raw   "Something (totally) different" :state :ok
                           :value "Something (totally) different"}]
                         (->> (previews sipoo "753-R" cfg-id one-id)
                              :previews
                              (mapcat vals))))))))

          (testing "Reorder columns."
            (testing "Not found."
              (is (not-found? (command sipoo :reorder-import-config-columns
                                       :organizationId "753-R"
                                       :configId (mongo/create-id)
                                       :columnIds []))))
            (testing "Bad column ids."
              (doseq [ids [[] [(mongo/create-id)]
                           [one-id two-id three-id]
                           [one-id two-id three-id four-id (mongo/create-id)]]]
                (is ((err :error.columns-do-not-match)
                     (command sipoo :reorder-import-config-columns
                              :organizationId "753-R"
                              :configId cfg-id
                              :columnIds ids)))))
            (testing "Success."
              (doseq [_    (range 10)
                      :let [order (shuffle [one-id two-id three-id four-id])]]
                (is (modified? (command sipoo :reorder-import-config-columns
                                        :organizationId "753-R"
                                        :configId cfg-id
                                        :columnIds order)))
                (is (= order (->> (get-config sipoo "753-R" cfg-id)
                                  :columns
                                  (sort-by :index)
                                  (mapv :id)))))))

          (testing "Execute import."
            (testing "Fail."
              (is (not-found? (command sipoo :execute-import-config
                                       :organizationId "753-R"
                                       :configId cfg-id))))
            (testing "Fix columns and import."
              (is (modified? (edit-config sipoo "753-R" cfg-id
                                          :column {:id    one-id
                                                   :title {:fi "Asiointitunnus"}
                                                   :type  "application-id"})))
              (is (modified? (edit-config sipoo "753-R" cfg-id
                                          :column {:id   four-id
                                                   :type "number"
                                                   :unit "eur"})))
              (testing "Roll back."
                (with-redefs [mongo/insert-batch (fn [k batch]
                                                   (doseq [doc batch]
                                                     (mongo/insert k doc))
                                                   (assert (pos? (mongo/count k {})))
                                                   (throw (ex-info "Kaboom!" {})))]
                  (let [{job :job} (command sipoo :execute-import-config
                                            :organizationId "753-R"
                                            :configId cfg-id)]
                    (is (= {:type :import :organization "753-R"
                            :data {:configId cfg-id}}
                           (select-keys job [:type :organization :data])))
                    (is (= {:status "done" :type "import"
                            :value  {:error "error.imports.import" :text "Kaboom!"}}
                           (select-keys (wait-for-organization-job sipoo "753-R" (:id job))
                                        [:status :type :value])))
                    (is (false? (mongo/any? :import-rows {}))))))
              (let [{job :job} (command sipoo :execute-import-config
                                        :organizationId "753-R"
                                        :configId cfg-id)]
                (is (= {:type :import :organization "753-R"
                        :data {:configId cfg-id}}
                       (select-keys job [:type :organization :data])))
                (is (= {:status "done" :type "import"
                        :value  {:n 3}}
                       (select-keys (wait-for-organization-job sipoo "753-R" (:id job))
                                    [:status :type :value])))))
            (testing "Config has been imported."
              (testing "Cannot be reimported nor deleted."
                (is (not-found? (command sipoo :execute-import-config
                                         :organizationId "753-R"
                                         :configId cfg-id)))
                (is (not-found? (command sipoo :delete-import-config
                                         :organizationId "753-R"
                                         :configId cfg-id))))
              (testing "Object has been removed from the cloud."
                (is (false? (file-storage/file-exists-in? object-storage/import-bucket
                                                          object-key))))
              (let [cfg (get-config sipoo "753-R" cfg-id)]
                (is (= (min-util/id-for-key sipoo) (get-in cfg [:imported :user-id])))
                (is (false? (contains? cfg :previews)))
                (is (false? (contains? cfg :importing-job)))
                (is (= {:filename "simple-table.xlsx"}
                       (:rowdata cfg)))
                (is (nil? (some :include (:columns cfg))))))
            (testing "Three rows have been created."
              (let [row-ids (:row-ids (query sipoo :import-config-row-ids
                                             :organizationId "753-R"
                                             :configId cfg-id))
                    rows    (:rows (query sipoo :import-config-rows
                                          :organizationId "753-R"
                                          :configId cfg-id
                                          :rowIds row-ids))
                    cells   (-> rows first :cells)]
                (is (= 3 (count row-ids) (count rows)))
                (is (= (set row-ids) (set (map :id rows))))
                (is (= 3 (-> rows first :cells count)))
                (testing "First row."
                  (let [row-id  (-> rows first :id)
                        [cell1 cell2 cell3
                         cell4] (mapv #(util/find-by-key :column-id % cells)
                                      [one-id two-id three-id four-id])
                        value3  (date/timestamp "12.8.2024")]
                    (is (= #{(:id cell1) "key"} (set (:errors (first rows)))))
                    (is (nil? (:value cell1)))
                    (is (= {:raw "Hello"} (:original cell1)))

                    (is (nil? cell2)) ;; Column not included

                    (is (nil? (:errors cell3)))
                    (is (= value3 (:value cell3)))
                    (is (= {:raw "45516" :value value3} (:original cell3)))

                    (is (nil? (:errors cell4)))
                    (is (= 1 (:value cell4)))
                    (is (= {:raw "1" :value 1} (:original cell4)))

                    (testing "Edit cell: application-id."
                      (is (validation-error? :application-id
                                             (edit-cell sipoo "753-R" row-id (:id cell1)
                                                        "still bad")))
                      (is (modified? (edit-cell sipoo "753-R" row-id (:id cell1)
                                                "  ")))
                      (let [row (get-row sipoo "753-R" cfg-id row-id)]
                        (is (= ["key"] (:errors row)))
                        (is (nil? (:value (util/find-by-id (:id cell1) (:cells row))))))

                      (is (modified? (edit-cell sipoo "753-R" row-id (:id cell1)
                                                " LP-123-2025-09876 ")))
                      (let [row (get-row sipoo "753-R" cfg-id row-id)]
                        (is (nil? (:errors row)))
                        (is (= "LP-123-2025-09876"
                               (:value (util/find-by-id (:id cell1) (:cells row)))))))
                    (testing "Edit cell: date."
                      (is (validation-error? :date
                                             (edit-cell sipoo "753-R" row-id (:id cell3)
                                                        "bad")))
                      ;; Bad data is not stored
                      (is (nil? (:errors (get-row sipoo "753-R" cfg-id row-id))))

                      (is (modified? (edit-cell sipoo "753-R" row-id (:id cell3)
                                                "  ")))
                      (let [row (get-row sipoo "753-R" cfg-id row-id)]
                        (is (nil? (:errors row)))
                        (is (nil? (:value (util/find-by-id (:id cell3) (:cells row))))))

                      (let [ts (date/timestamp "3.4.2025")]
                        (doseq [d    [ts "3.4.2025" (str ts)
                                      (date/timestamp (date/end-of-day ts))]
                                :let [r (edit-cell sipoo "753-R" row-id (:id cell3) d)]]
                          (is (= ts (:value r)))
                          (is (modified? r)))
                        (let [row (get-row sipoo "753-R" cfg-id row-id)]
                          (is (nil? (:errors row)))
                          (is (= ts
                                 (:value (util/find-by-id (:id cell3) (:cells row))))))))

                    (testing "Edit cell: number."
                      (is (validation-error? :number
                                             (edit-cell sipoo "753-R" row-id (:id cell4)
                                                        "bad")))
                      ;; Bad data is not stored
                      (is (nil? (:errors (get-row sipoo "753-R" cfg-id row-id))))

                      (is (modified? (edit-cell sipoo "753-R" row-id (:id cell4)
                                                "  ")))
                      (let [row (get-row sipoo "753-R" cfg-id row-id)]
                        (is (nil? (:errors row)))
                        (is (nil? (:value (util/find-by-id (:id cell4) (:cells row))))))

                      (letfn [(edit-number  [n v]
                                (let [r (edit-cell sipoo "753-R" row-id (:id cell4) n)]
                                  (testing (format "%s -> %s" n v)
                                    (is (modified? r))
                                    (is (= v (:value r))))))]
                        (edit-number 12 12)
                        (edit-number " 12 " 12)
                        (edit-number 34.567 34.57)
                        (edit-number " 34.567 " 34.57)
                        (edit-number " -34.567 " -34.57)))))

                (testing "Deleted and erroneous rows."
                  (let [[row-id1 row-id2
                         row-id3]   row-ids
                        get-row-ids (fn [deleted errors]
                                      (get-row-ids sipoo "753-R" cfg-id
                                                   :deleted deleted
                                                   :errors errors))]
                    (testing "Mark row deleted."
                      (is (deleted? (command sipoo :mark-import-config-row-deleted
                                             :organizationId "753-R"
                                             :rowId row-id2
                                             :deleted true)))
                      (is (deleted? (get-row sipoo "753-R" cfg-id row-id2)))
                      (is (= [row-id1 row-id3]
                             (get-row-ids nil nil)
                             (get-row-ids "exclude" nil)))
                      (is (= [row-id1 row-id2 row-id3] (get-row-ids "include" nil)))
                      (is (= [row-id2] (get-row-ids "only" nil))))
                    (testing "Deleted row can still be edited."
                      (is (modified? (edit-cell sipoo "753-R" row-id2
                                                (-> rows second :cells last :id)
                                                23))))
                    (testing "Erroneous rows."
                      (is (= [row-id1] (get-row-ids nil "exclude")))
                      (is (= [row-id1 row-id3] (get-row-ids nil "include")))
                      (is (= [row-id3] (get-row-ids nil "only"))))
                    (testing "Mixed."
                      (is (= [row-id1 row-id2 row-id3] (get-row-ids "include" "include")))
                      (is (= [row-id2 row-id3] (get-row-ids "include" "only")))
                      (is (= [row-id1] (get-row-ids "exclude" "exclude")))
                      (is (= [row-id2] (get-row-ids "only" "only"))))
                    (testing "Mark row not deleted."
                      (is (ok? (command sipoo :mark-import-config-row-deleted
                                        :organizationId "753-R"
                                        :rowId row-id2
                                        :deleted false)))
                      (is (= [] (get-row-ids "only" nil))))
                    (testing "Mark config deleted."
                      (is (deleted? (command sipoo :mark-import-config-deleted
                                             :organizationId "753-R"
                                             :configId cfg-id
                                             :deleted true)))
                      (is (deleted? (get-config sipoo "753-R" cfg-id)))
                      (is (not-found? (edit-config sipoo "753-R" cfg-id
                                                   :title {:fi "what?"})))
                      (is (not-found? (command sipoo :reorder-import-config-columns
                                               :organizationId "753-R"
                                               :configId cfg-id
                                               :columnIds (map :id [col1 col3 col4]))))
                      (is (not-found? (query sipoo :import-config-row-ids
                                             :organizationId "753-R"
                                             :configId cfg-id)))
                      (is (not-found? (query sipoo :import-config-rows
                                             :organizationId "753-R"
                                             :configId cfg-id
                                             :rowIds row-ids))))
                    (testing "Mark config not deleted."
                      (is (ok? (command sipoo :mark-import-config-deleted
                                        :organizationId "753-R"
                                        :configId cfg-id
                                        :deleted false)))
                      (is (nil? (:deleted (get-config sipoo "753-R" cfg-id))))
                      (is (modified? (edit-config sipoo "753-R" cfg-id
                                                  :title {:fi "what?"})))
                      (is (modified? (command sipoo :reorder-import-config-columns
                                              :organizationId "753-R"
                                              :configId cfg-id
                                              :columnIds (map :id [col1 col3 col4]))))
                      (is (ok? (query sipoo :import-config-row-ids
                                      :organizationId "753-R"
                                      :configId cfg-id)))
                      (is (ok? (query sipoo :import-config-rows
                                      :organizationId "753-R"
                                      :configId cfg-id
                                      :rowIds row-ids)))))))))
          (testing "One configuration."
            (is (= {:ok      true
                    :configs [(get-config sipoo "753-R" cfg-id)]}
                   (query sipoo :organization-import-configs
                          :organizationId "753-R")))))))))

(defn upload-result [apikey organization-id filename]
  (let [{:keys [body]} (upload-excel apikey organization-id filename)
        job-id         (:job-id body)]
    (:value (wait-for-organization-job apikey organization-id job-id))))

(deftest upload-fails-test
  (testing "Bad files."
    (let [file (fs/file "dev-resources/cake.png")]
      (is ((err :error.invalid-filename)
           (local-raw sipoo :upload-import-excel
                      :organizationId "753-R"
                      :file {:filename     (fs/file-name file)
                             :content-type "application/octet-stream"
                             :tempfile     file
                             :size         (fs/size file)})))
      (is ((err :error.file-not-found)
           (local-raw sipoo :upload-import-excel
                      :organizationId "753-R"
                      :file {:filename     "good.xlsx"
                             :content-type "application/octet-stream"
                             :size         (fs/size file)})))
      (is ((err :error.invalid-filesize)
           (local-raw sipoo :upload-import-excel
                      :organizationId "753-R"
                      :file {:filename     "huge.xlsx"
                             :content-type "application/octet-stream"
                             :tempfile     file
                             :size         Long/MAX_VALUE})))))
  (fs/with-temp-dir [dir nil]
    (testing "Bad excel."
      (let [file (fs/create-temp-file {:dir dir :suffix "-bad.xlsx"})]
        (spit (str file) "This is not my beautiful excel.")
        (is (= {:error "error.read-spreadsheet-fast", :text "status: 400"}
               (upload-result sipoo "753-R" file)))))
    (testing "Blank excel."
      (let [file (fs/create-temp-file {:dir dir :suffix "-blank.xlsx"})]
        (fast/->excel (str file) [] [])
        (is (= {:error "error.imports.no-rows"}
               (upload-result sipoo "753-R" file)))))
    (testing "Headers only."
      (let [file (fs/create-temp-file {:dir dir :suffix "-blank.xlsx"})]
        (fast/->excel (str file) [{:path :foo :text "Hello"}] [])
        (is (= {:error "error.imports.no-rows"}
               (upload-result sipoo "753-R" file)))))))

(deftest delete-config-test
  (fs/with-temp-dir [dir nil]
    (testing "Upload excel, blank rows are skipped."
      (let [file                       (fs/create-temp-file {:dir dir :suffix "-skip.xlsx"})
            _                          (fast/->excel (str file) [{:path :a :text "A"}
                                                                 {:path :b :text "B"}
                                                                 {:path :c :text "C"}]
                                                     [{} {} {}
                                                      {:a " one " :b "two" :c "three"}
                                                      {}
                                                      {:a "" :b "four"}
                                                      {}
                                                      {:a " five "}
                                                      {}])
            {cfg-id :config-id}        (upload-result sipoo "753-R" file)
            {:keys [rowdata previews]} (im/get-configuration "753-R" cfg-id)]
        (testing "Blank rows are skipped."
          (is (= [#{"one" "two" "three"}
                  #{"four"}
                  #{"five"}]
                 (map (comp set vals) previews))))
        (testing "File object is stored."
          (is (true? (file-storage/file-exists-in? object-storage/import-bucket
                                                   (:object-key rowdata)))))
        (testing "Delete configuration."
          (is (ok? (command sipoo :delete-import-config
                            :organizationId "753-R"
                            :configId cfg-id)))
          (is (nil? (im/get-configuration "753-R" cfg-id)))
          (is (false? (file-storage/file-exists-in? object-storage/import-bucket
                                                   (:object-key rowdata)))))))))

(defn execute-import [apikey {org-id :organization cfg-id :id}]
  (let [{job :job} (command apikey :execute-import-config
                            :organizationId org-id
                            :configId cfg-id)]
    (is (= {:type :import :organization org-id
                        :data {:configId cfg-id}}
                       (select-keys job [:type :organization :data])))
    (is (= {:status "done" :type "import"}
           (select-keys (wait-for-organization-job sipoo org-id (:id job))
                        [:status :type])))))

(defn column-id-for-title [{:keys [columns]} column-title]
  (->> columns
       (util/find-first (util/fn-> :title :fi (= column-title)))
       :id))

(defn configure-column [apikey {:keys [id organization]
                                :as cfg}
                        column-title
                        & {:as params}]
  (command apikey :update-import-config
           (-> params
               (assoc :organizationId organization
                      :configId id)
               (assoc-in [:column :id] (column-id-for-title cfg column-title)))))

(defn find-cell-ids [{:keys [cells]} & types]
  (mapv #(:id (util/find-by-key :type % cells)) types))

(defn app-edit-cell [apikey app-id row-id cell-id value]
  (command apikey :edit-application-import-row
           :id app-id
           :rowId row-id
           :cellId cell-id
           :value value))

(defn cell-value [row-id cell-id]
  (->> (mongo/by-id :import-rows row-id [:cells])
       :cells (util/find-by-id cell-id) :value))

(deftest application-import-section-test
  (fs/with-temp-dir [dir nil]
    (testing "Upload two excels with four columns and one excel with one column."
      (let [app-id1              (:id (create-and-submit-local-application pena :operation "pientalo"
                                                                           :address "One Outpost"))
            app-id2              (:id (create-and-submit-local-application pena :operation "aita"
                                                                           :address "Two Tower"))
            app-id3              (:id (create-and-submit-local-application pena :operation "poikkeamis"
                                                                           :address "Three Temple"))
            app-id4              (:id (create-and-submit-local-application pena :operation "varasto-tms"
                                                                           :address "Four Flats"))
            jp-id                (:id (create-and-submit-local-application pena :operation "purkaminen"
                                                                           :propertyId jarvenpaa-property-id
                                                                           :address "Jelly Bean"))
            file1                (fs/create-temp-file {:dir dir :suffix "-app1.xlsx"})
            row                  (fn [& xs]
                                   (util/strip-nils (zipmap [:a :b :c :d] xs)))
            _                    (fast/->excel (str file1) [{:path :a :text "Date"}
                                                            {:path :b :text "Info"}
                                                            {:path :c :text "Backend id"}
                                                            {:path :d :text "Application id"}]
                                               [(row "5.2.2025" "Runeberg" "runeberg" app-id1)
                                                (row "18.4.2025" "Good Friday" "friday" app-id2)
                                                (row "1.5.2025" "May Day" "wappu")])
            {cfg-id1 :config-id} (upload-result sipoo "753-R" file1)
            cfg1                 (im/get-configuration "753-R" cfg-id1)
            file2                (fs/create-temp-file {:dir dir :suffix "-app2.xlsx"})
            _                    (fast/->excel (str file2) [{:path :a :text "Label"}
                                                            {:path :b :text "Amount"}
                                                            {:path :c :text "Backend id"}
                                                            {:path :d :text "Application id"}]
                                               [(row "Ten" "10" "ten" app-id1)
                                                (row "Twenty" "20" "twenty")
                                                (row "Thirty" "30" "thirty" app-id3)])
            {cfg-id2 :config-id} (upload-result sipoo "753-R" file2)
            cfg2                 (im/get-configuration "753-R" cfg-id2)
            file3                (fs/create-temp-file {:dir dir :suffix "-app3.xlsx"})
            _                    (fast/->excel (str file3) [{:path :a :text "Greeting"}
                                                            {:path :b :text "Backend id"}]
                                               [(row "Hello" "")
                                                (row "Ni hao" "foo")
                                                (row "Moi" "bar")])
            {cfg-id3 :config-id} (upload-result sipoo "753-R" file3)
            cfg3                 (im/get-configuration "753-R" cfg-id3)]
        (is (ok? (configure-column sipoo cfg1 "Date" :column {:type "date"})))
        (is (ok? (configure-column sipoo cfg1 "Info" :column {:lines "5"})))
        (is (ok? (configure-column sipoo cfg1 "Backend id" :column {:type "backend-id"})))
        (is (ok? (configure-column sipoo cfg1 "Application id" :column {:type "application-id"})))

        (execute-import sipoo cfg1)

        (is (ok? (configure-column sipoo cfg2 "Amount" :column {:type "number" :unit "kpl"})))
        (is (ok? (configure-column sipoo cfg2 "Backend id" :column {:type "backend-id"})))
        (is (ok? (configure-column sipoo cfg2 "Application id" :column {:type "application-id"})))

        (execute-import sipoo cfg2)

        (is (ok? (configure-column sipoo cfg3 "Backend id" :column {:type "backend-id"})))

        (execute-import sipoo cfg3)

        (testing "No configurations in Järvenpää."
          (is (not-found? (query raktark-jarvenpaa :application-imports
                                 :id jp-id))))

        (testing "Application one import section."
          (testing "No section for applicant."
            (is (unauthorized? (query pena :application-imports :id app-id1))))

          (testing "Three configs, two rows for authority."
            (let [{:keys [rows configs]} (query luukas :application-imports
                                                :id app-id1)
                  {row-id1 :id :as row1} (util/find-by-key :config-id cfg-id1 rows)
                  {row-id2 :id :as row2} (util/find-by-key :config-id cfg-id2 rows)
                  [date-cid info-cid]    (find-cell-ids row1 :date :text)
                  [label-cid amount-cid] (find-cell-ids row2 :text :number)]

              (is (every? hex-string? [date-cid info-cid label-cid amount-cid]))

              (is (midje? (just (contains {:id cfg-id1})
                                (contains {:id cfg-id2})
                                (contains {:id cfg-id3})
                                :in-any-order)
                          configs))
              (is (midje? (just truthy truthy) rows))

              (testing "No backend-id or application-id cells."
                (is (= #{:date :text :number}
                       (->> (mapcat :cells rows)
                            (map :type)
                            set))))

              (testing "Luukas or Pena cannot edit row, but Sonja can."
                (is (unauthorized? (app-edit-cell luukas app-id1 row-id1
                                                  info-cid "New information")))
                (is (unauthorized? (app-edit-cell pena app-id1 row-id1
                                                  info-cid "New information")))
                (is (ok? (app-edit-cell sonja app-id1 row-id1
                                        info-cid "New information")))
                (is (= "New information" (cell-value row-id1 info-cid)))

                (is (fail? (app-edit-cell sonja app-id1 row-id1
                                          date-cid "Bad date")))
                (is (not-found? (app-edit-cell sonja app-id1 (mongo/create-id)
                                          date-cid "")))
                (is (not-found? (app-edit-cell sonja app-id1 row-id1
                                               (mongo/create-id) "")))
                (is (ok? (app-edit-cell sonja app-id1 row-id1
                                        date-cid "  ")))
                (is (nil? (cell-value row-id1 date-cid))))

              (testing "New row can be added."
                (is (not-found? (command sonja :add-application-import-row
                                         :id app-id1
                                         :configId (mongo/create-id))))
                (let [new-row       (:row (command sonja :add-application-import-row
                                                   :id app-id1
                                                   :configId cfg-id1))
                      new-mongo-row (im-sc/->row (mongo/by-id :import-rows (:id new-row)))]
                  (is (midje? (contains {:config-id cfg-id1
                                         :added     (just {:id      app-id1
                                                           :user-id sonja-id
                                                           :ts      pos?})
                                         :cells     (just (just {:id        hex-string?
                                                                 :column-id (column-id-for-title cfg1 "Date")
                                                                 :type      :date
                                                                 :modified  truthy})
                                                          (just {:id        hex-string?
                                                                 :column-id (column-id-for-title cfg1 "Info")
                                                                 :type      :text
                                                                 :modified  truthy}))})
                              new-row))
                  (is (midje? (contains {:config-id    cfg-id1
                                         :organization "753-R"
                                         :added        (just {:id      app-id1
                                                              :user-id sonja-id
                                                              :ts      pos?})
                                         :cells        (just (just {:id        hex-string?
                                                                    :column-id (column-id-for-title cfg1 "Date")
                                                                    :type      :date
                                                                    :modified  truthy})
                                                             (just {:id        hex-string?
                                                                    :column-id (column-id-for-title cfg1 "Info")
                                                                    :type      :text
                                                                    :modified  truthy})
                                                             (just {:id        hex-string?
                                                                    :column-id (column-id-for-title cfg1 "Backend id")
                                                                    :type      :backend-id
                                                                    :modified  truthy})
                                                             (just {:id        hex-string?
                                                                    :column-id (column-id-for-title cfg1 "Application id")
                                                                    :type      :application-id
                                                                    :value     app-id1
                                                                    :modified  truthy}))})
                              new-mongo-row))
                  (testing "Added rows in `import-config-row-ids` query"
                    (let [added-id (:id new-row)
                          query-ids (fn [added]
                                      (get-row-ids sipoo "753-R" cfg-id1 :added added))]
                      (is (midje? (contains added-id) (query-ids nil)))
                      (is (= [added-id] (query-ids "only")))
                      (is (not (midje? (contains added-id) (query-ids "exclude"))))))))

              (testing "Mark row deleted."
                (is (unauthorized? (command luukas :mark-application-import-row-deleted
                                            :id app-id1
                                            :rowId row-id2
                                            :deleted true)))
                (is (midje? (just {:ok true :deleted (just {:ts truthy :user-id sonja-id})})
                            (command sonja :mark-application-import-row-deleted
                                     :id app-id1
                                     :rowId row-id2
                                     :deleted true))))

              (testing "Deleted row can be edited (in theory, not in UI)."
                (let [[n-cid] (find-cell-ids row2 :number)]
                  (is (ok? (app-edit-cell sonja app-id1 row-id2
                                          n-cid "987")))
                  (is (= 987 (cell-value row-id2 n-cid)))))

              (testing "Deleted rows are still available for application."
                (is (midje? (contains {:id row-id2 :deleted truthy})
                            (->> (query sonja :application-imports
                                        :id app-id1)
                                 :rows
                                 (util/find-by-id row-id2)))))

              (testing "Restore row."
                (is (nil? (-> (command sonja :mark-application-import-row-deleted
                                       :id app-id1
                                       :rowId row-id2
                                       :deleted false)
                              :row :deleted)))
                (is (nil? (->> (query sonja :application-imports
                                      :id app-id1)
                               :rows
                               (util/find-by-id row-id2)
                               :deleted))))

              (testing "If the config is marked deleted, it and its rows are not available."
                (is (ok? (command sipoo :mark-import-config-deleted
                                  :organizationId "753-R"
                                  :configId cfg-id2
                                  :deleted true)))
                (let [{:keys [configs rows]} (query sonja :application-imports
                                                    :id app-id1)]
                  (is (nil? (util/find-by-id cfg-id2 configs)))
                  (is (nil? (util/find-by-id row-id2 rows))))))))

        (testing "Application and backend id matching."
          (mongo/update-by-id :applications app-id3
                              {$push {:verdicts {:kuntalupatunnus "wappu"}}})
          (let [{:keys [configs rows]} (query sonja :application-imports
                                              :id app-id3)]
            (is (midje? (just cfg-id1 cfg-id3 :in-any-order)
                        (mapv :id configs)))
            (is (= 1 (count rows)))))

        (testing "Application backend id is copied to the new row."
          (let [new-row       (:row (command sonja :add-application-import-row
                                             :id app-id3
                                             :configId cfg-id1))
                new-mongo-row (im-sc/->row (mongo/by-id :import-rows (:id new-row)))]
            (is (midje? (contains {:config-id cfg-id1
                                   :added     truthy
                                   :cells     (just (just {:id        hex-string?
                                                           :column-id (column-id-for-title cfg1 "Date")
                                                           :type      :date
                                                           :modified  truthy})
                                                    (just {:id        hex-string?
                                                           :column-id (column-id-for-title cfg1 "Info")
                                                           :type      :text
                                                           :modified  truthy}))})
                              new-row))
            (is (midje? (contains {:config-id    cfg-id1
                                   :organization "753-R"
                                   :added        truthy
                                   :cells        (just (just {:id        hex-string?
                                                              :column-id (column-id-for-title cfg1 "Date")
                                                              :type      :date
                                                              :modified  truthy})
                                                       (just {:id        hex-string?
                                                              :column-id (column-id-for-title cfg1 "Info")
                                                              :type      :text
                                                              :modified  truthy})
                                                       (just {:id        hex-string?
                                                              :column-id (column-id-for-title cfg1 "Backend id")
                                                              :type      :backend-id
                                                              :value     "wappu"
                                                              :modified  truthy})
                                                       (just {:id        hex-string?
                                                              :column-id (column-id-for-title cfg1 "Application id")
                                                              :type      :application-id
                                                              :value     app-id3
                                                              :modified  truthy}))})
                        new-mongo-row))))

        (testing "Application and configuration without backend id."
          (let [new-row       (:row (command sonja :add-application-import-row
                                             :id app-id4
                                             :configId cfg-id3))
                new-mongo-row (im-sc/->row (mongo/by-id :import-rows (:id new-row)))]
            (is (midje? (contains {:config-id cfg-id3
                                   :added     (just {:id      app-id4
                                                     :user-id sonja-id
                                                     :ts      pos?})
                                   :cells     (just (just {:id        hex-string?
                                                           :column-id (column-id-for-title cfg3 "Greeting")
                                                           :type      :text
                                                           :modified  truthy}))})
                              new-row))
            (is (midje? (contains {:config-id    cfg-id3
                                   :organization "753-R"
                                   :added        (just {:id      app-id4
                                                        :user-id sonja-id
                                                        :ts      pos?})
                                   :cells        (just (just {:id        hex-string?
                                                              :column-id (column-id-for-title cfg3 "Greeting")
                                                              :type      :text
                                                              :modified  truthy})
                                                       (just {:id        hex-string?
                                                              :column-id (column-id-for-title cfg3 "Backend id")
                                                              :type      :backend-id
                                                              :modified  truthy}))})
                        new-mongo-row))
            (is (midje? (contains {:rows (just new-row)})
                        (query sonja :application-imports :id app-id4)))))))))
