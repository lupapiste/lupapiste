(ns lupapalvelu.matti-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.factlet :refer :all]))

(apply-remote-minimal)

(defn err [error]
  (partial expected-failure? error))

(defn prefix-keys [m prefix]
  (reduce (fn [acc [k v]]
            (assoc acc
                   (->> k name (str (name prefix)) keyword) v))
          {}
          m))

(facts "Settings"
  (fact "Bad category"
    (query sipoo :verdict-template-settings
           :category "foo") => (err :error.invalid-category))
  (fact "No settings"
    (query sipoo :verdict-template-settings
           :category "r")=> (just {:ok true}))
  (fact "Save settings draft"
    (let [{modified :modified}
          (command sipoo :save-verdict-template-settings-value
                   :category "r"
                   :path [:one :two]
                   :value ["a" "b" "c"])]
      modified => pos?
      (fact "Query settings"
        (query sipoo :verdict-template-settings
               :category "r")
        => (contains {:settings {:draft    {:one {:two ["a" "b" "c"]}}
                                 :modified modified}})))))

(fact "Sipoo categories"
  (:categories (query sipoo :verdict-template-categories))
  => (contains ["r" "p" "ymp" "kt"] :in-any-order))

(fact "Create new template"
  (let [{:keys [id name draft modified category]}
        (command sipoo :new-verdict-template :category "r")]
    id => string?
    name => "P\u00e4\u00e4t\u00f6spohja"
    draft => {}
    modified => pos?
    category => "r"
    (fact "Fetch draft"
      (query sipoo :verdict-template :template-id id)
      => (contains {:id       id
                    :name     name
                    :draft    {}
                    :modified modified}))
    (fact "Template list"
      (query sipoo :verdict-templates)
      => (contains {:verdict-templates [{:id        id
                                         :name      name
                                         :modified  modified
                                         :deleted   false
                                         :category  "r"
                                         :published nil}]}))
    (fact "Change the name"
      (let [{later :modified}
            (command sipoo :set-verdict-template-name
                     :template-id id
                     :name "Uusi nimi")]
        (- later modified) => pos?
        (fact "Save draft data value"
          (let [{even-later :modified}
                (command sipoo :save-verdict-template-draft-value
                         :template-id id
                         :path [:matti-foremen :pdf]
                         :value true)]
            (- even-later later) => pos?
            (fact "Fetch draft again"
              (query sipoo :verdict-template :template-id id)
              => (contains {:id       id
                            :name     "Uusi nimi"
                            :draft    {:matti-foremen {:pdf true}}
                            :modified even-later}))
            (fact "Publish template"
              (let [{published :published}
                    (command sipoo :publish-verdict-template
                             :template-id id)]
                (- published even-later) => pos?
                (fact "Delete template"
                  (command sipoo :toggle-delete-verdict-template
                           :template-id id
                           :delete true) => ok?)
                (fact "Template list again. Publishing and deletion do not affect modified timestamp."
                  (query sipoo :verdict-templates)
                  => (contains {:verdict-templates [{:id        id
                                                     :name      "Uusi nimi"
                                                     :modified  even-later
                                                     :deleted   true
                                                     :category  "r"
                                                     :published published}]}))
                (fact "Name change not allowed for deleted template"
                  (command sipoo :set-verdict-template-name
                           :template-id id
                           :name "Foo")
                  => (err :error.verdict-template-deleted))
                (fact "Draft data update not allowed for deleted template"
                  (command sipoo :save-verdict-template-draft-value
                           :template-id id
                           :path [:hii]
                           :value "Foo")
                  => (err :error.verdict-template-deleted))
                (fact "Publish not allowed for deleted template"
                  (command sipoo :publish-verdict-template
                           :template-id id)
                  => (err :error.verdict-template-deleted))
                (fact "Fetch draft not allowed for deleted template"
                  (query sipoo :verdict-template
                         :template-id id)
                  => (err :error.verdict-template-deleted))
                (fact "Copying is allowed also for deleted templates"
                  (let [{:keys [copy-id copy-modified copy-published
                                copy-deleted copy-draft copy-name
                                copy-category]}
                        (prefix-keys (command sipoo :copy-verdict-template
                                              :template-id id)
                                     :copy-)]
                    copy-id =not=> id
                    (- copy-modified published) => pos?
                    copy-published => nil
                    copy-category => "r"
                    copy-name => "Uusi nimi (kopio)"
                    copy-draft => {:matti-foremen {:pdf true}}
                    (fact "Editing copy draft does not affect original"
                      (command sipoo :save-verdict-template-draft-value
                               :template-id copy-id
                               :path [:matti-verdict :2 :giver]
                               :value "lautakunta") => ok?
                      (fact "Copy has new data"
                        (query sipoo :verdict-template
                               :template-id copy-id)
                        => (contains {:draft {:matti-foremen {:pdf true}
                                              :matti-verdict {:2 {:giver "lautakunta"}}}
                                      :name "Uusi nimi (kopio)"}))
                      (fact "Restore the deleted template"
                        (command sipoo :toggle-delete-verdict-template
                                 :template-id id
                                 :delete false) => ok?)
                      (fact "The original (restored) template does not have new data"
                        (query sipoo :verdict-template
                               :template-id id)
                        => (contains {:draft {:matti-foremen {:pdf true}}
                                      :name "Uusi nimi"}))
                      (fact "Template list has both templates"
                        (->> (query sipoo :verdict-templates)
                             :verdict-templates
                             (map #(select-keys % [:id :name])))
                        =>  [{:id id :name "Uusi nimi"}
                             {:id copy-id :name "Uusi nimi (kopio)"}]))))))))))))

(facts "Data path and value validation"
  (let [{id :id} (command sipoo :new-verdict-template :category "r")]
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path [:matti-foremen :pdf]
             :value "bad")=> (err :error.invalid-value)
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path [:bad :path]
             :value false)=> (err :error.invalid-value-path)
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path ["matti-verdict" "1" "valitus" "delta"]
             :value -8) => (err :error.invalid-value)))

(facts "Reviews"
  (fact "Initially empty"
    (query sipoo :verdict-template-reviews :category "r")
    => {:ok      true
        :reviews []})
  (fact "Add new review"
    (let [{id :id} (:review (command sipoo :add-verdict-template-review
                                     :category "r"))]
      id => truthy
      (fact "Fetch reviews again"
        (:reviews (query sipoo :verdict-template-reviews :category "r"))
        => (just [(contains {:name     {:fi "Katselmus"
                                        :sv "Syn"
                                        :en "Review"}
                             :category "r"
                             :deleted  false
                             :type     "muu-katselmus"})]))
      (fact "Give name to the review"
        (:review (command sipoo :update-verdict-template-review
                          :review-id id
                          :fi "Nimi" :sv "Namn" :en "Name"))
        => (contains {:id       id
                      :name     {:fi "Nimi" :sv "Namn" :en "Name"}
                      :deleted  false
                      :category "r"
                      :type     "muu-katselmus"}))
      (fact "New name available"
        (:reviews (query sipoo :verdict-template-reviews :category "r"))
        => (just [(contains {:name     {:fi "Nimi" :sv "Namn" :en "Name"}
                             :category "r"
                             :deleted  false
                             :type     "muu-katselmus"})]))
      (facts "Update review details"
        (fact "Finnish name"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :fi "Moro")
          => (contains {:review (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Namn"
                                                  :en "Name"}})}))
        (fact "Name cannot be empty"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :en "  ")
          => (err :error.name-blank))
        (fact "Review type"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous)
          => (contains {:review (contains {:id id
                                           :type "aloituskokous"})}))
        (fact "Invalid review type"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :hiihoo)
          => (err :error.invalid-review-type))
        (fact "Swedish and English names, review type"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :sv "Stockholm" :en "London" :type :lvi-katselmus)
          => (contains {:review (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Stockholm"
                                                  :en "London"}
                                           :type "lvi-katselmus"})}))
        (fact "Unsupported params"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous
                   :foo "bar")
          => (err :error.unsupported-parameters))
        (fact "Mark review as deleted"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :deleted true)
          => (contains {:review (contains {:id id
                                           :deleted true})}))
        (fact "Deleted review cannot be re-deleted"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :deleted true)
          => (err :error.settings-item-deleted))
        (fact "Deleted reviews cannot be edited"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous
                   :fi "Hei")
          => (err :error.settings-item-deleted))
        (fact "Deleted reviews can be restored (and edited)"
          (command sipoo :update-verdict-template-review
                   :review-id id
                   :type :aloituskokous
                   :fi "Hei"
                   :deleted false)
          => (contains {:review (contains {:id id
                                           :type "aloituskokous"
                                           :name (contains {:fi "Hei"})
                                           :deleted false})}))
        (fact "Review not found"
          (command sipoo :update-verdict-template-review
                   :review-id "notfoun"
                   :type :aloituskokous
                   :fi "Hei")
          => (err :error.settings-item-not-found))))))

(facts "Plans"
  (fact "Initially empty"
    (query sipoo :verdict-template-plans :category "r")
    => {:ok      true
        :plans []})
  (fact "Add new plan"
    (let [{id :id} (:plan (command sipoo :add-verdict-template-plan
                                     :category "r"))]
      id => truthy
      (fact "Fetch plans again"
        (:plans (query sipoo :verdict-template-plans :category "r"))
        => (just [(contains {:name     {:fi "Suunnitelmat"
                                        :sv "Planer"
                                        :en "Plans"}
                             :category "r"
                             :deleted  false})]))
      (fact "Give name to the plan"
        (:plan (command sipoo :update-verdict-template-plan
                          :plan-id id
                          :fi "Nimi" :sv "Namn" :en "Name"))
        => (contains {:id       id
                      :name     {:fi "Nimi" :sv "Namn" :en "Name"}
                      :deleted  false
                      :category "r"}))
      (fact "New name available"
        (:plans (query sipoo :verdict-template-plans :category "r"))
        => (just [(contains {:name     {:fi "Nimi" :sv "Namn" :en "Name"}
                             :category "r"
                             :deleted  false})]))
      (facts "Update plan details"
        (fact "Finnish name"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :fi "Moro")
          => (contains {:plan (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Namn"
                                                  :en "Name"}})}))
        (fact "Name cannot be empty"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :en "  ")
          => (err :error.name-blank))
        (fact "Swedish and English names"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :sv "Stockholm" :en "London")
          => (contains {:plan (contains {:id id
                                           :name {:fi "Moro"
                                                  :sv "Stockholm"
                                                  :en "London"}})}))
        (fact "Unsupported params"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :type :aloituskokous)
          => (err :error.unsupported-parameters))
        (fact "Mark plan as deleted"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :deleted true)
          => (contains {:plan (contains {:id id
                                           :deleted true})}))
        (fact "Deleted plan cannot be re-deleted"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :deleted true)
          => (err :error.settings-item-deleted))
        (fact "Deleted plans cannot be edited"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :fi "Hei")
          => (err :error.settings-item-deleted))
        (fact "Deleted plans can be restored (and edited)"
          (command sipoo :update-verdict-template-plan
                   :plan-id id
                   :fi "Hei"
                   :deleted false)
          => (contains {:plan (contains {:id id
                                         :name (contains {:fi "Hei"})
                                         :deleted false})}))
        (fact "Plan not found"
          (command sipoo :update-verdict-template-plan
                   :plan-id "notfound"
                   :fi "Hei")
          => (err :error.settings-item-not-found))))))
