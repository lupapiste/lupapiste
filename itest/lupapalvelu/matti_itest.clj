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
             :value -8) => (err :error.invalid-value)
    (command sipoo :save-verdict-template-draft-value
             :template-id id
             :path ["matti-appeal" "0" "type"]
             :value "bad") => (err :error.invalid-value)))
