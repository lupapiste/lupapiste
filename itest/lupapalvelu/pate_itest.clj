(ns lupapalvelu.pate-itest
  (:require [clj-http.client :as http-client]
            [clojure.data.xml :as cxml]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [lupapalvelu.pate-itest-util :refer :all]
            [lupapalvelu.xml.validator :as validator]
            [midje.sweet :refer :all]
            [sade.coordinate :as coord]
            [sade.core :refer [def- now]]
            [sade.date :as date]
            [sade.strings :as ss]
            [sade.util :as util]
            [sade.xml :as xml]))

(apply-remote-minimal)

(def- sipoo-R-org-id "753-R")
(def- sipoo-YA-org-id "753-YA")

(defn mangle-keys [m fun]
  (reduce-kv (fn [acc k v]
               (assoc acc (fun k) v))
             {}
             m))

(defn prefix-keys [m prefix]
  (mangle-keys m (util/fn->> name (str (name prefix)) keyword)))

(defn toggle-sipoo-pate [flag]
  (toggle-pate sipoo-R-org-id flag))

(defn check-kuntagml [{:keys [id organization permitType]} verdict-date]
  (if-let [data (some->> (integration-messages id) (filter (comp string? :data)) last :data)]
    (let [xml           (cxml/parse-str data)
          organization  (organization-from-minimal-by-id organization)
          krysp-version (get-in organization [:krysp (keyword permitType) :version])]
      (fact "XML is valid"
        (validator/validate data (keyword permitType) krysp-version) => nil)
      (facts "paatostieto"
        (fact "element exists"
          (xml/select1 xml [:paatostieto]) => truthy)
        (fact "paatosPvm"
          (xml/get-text xml [:paatostieto :poytakirja :paatospvm])
          => (date/xml-date verdict-date)))
      xml)
    (do
      (fact "no integration message xml found"
        id => nil)
      nil)))

(defn new-attachment-version-fails [apikey app-id attachment-id]
  (let [file-id (get-in (upload-file apikey "dev-resources/test-attachment.txt") [:files 0 :fileId])
        resp (command apikey :bind-attachment
                      :id app-id
                      :attachmentId attachment-id
                      :fileId file-id)]
    (fact "Bind-attachment command fails" resp => fail?)
    (-> resp :text keyword)))

(facts "Pate enabled"
  (fact "Disable Pate in Sipoo"
    (toggle-sipoo-pate false)
    (query sipoo :pate-enabled :organizationId sipoo-R-org-id)
    => (err :error.pate-disabled))
  (fact "Enable Pate in Sipoo"
    (toggle-sipoo-pate true)
    (query sipoo :pate-enabled :organizationId sipoo-R-org-id) => ok?))

(facts "Settings"
  (fact "Bad organizationId"
    (query sipoo :verdict-template-settings
           :organizationId "bad"
           :category "r") => (err :error.unauthorized))
  (fact "Bad category"
    (query sipoo :verdict-template-settings
           :organizationId sipoo-R-org-id
           :category "foo") => (err :error.invalid-category))
  (fact "No settings"
    (query sipoo :verdict-template-settings
           :organizationId sipoo-R-org-id
           :category "r")=> (just {:ok true
                                   :filled false
                                   :settings nil}))
  (fact "Save to bad path"
    (command sipoo :save-verdict-template-settings-value
             :organizationId sipoo-R-org-id
             :category "r"
             :path [:one :two]
             :value ["a" "b" "c"])
    => (err :error.invalid-value-path))
  (fact "Save bad value"
    (command sipoo :save-verdict-template-settings-value
             :organizationId sipoo-R-org-id
             :category "r"
             :path [:verdict-code]
             :value [:bad-code])
    => invalid-value?)
  (fact "No authority-admin auths"
    (command sipoo-ya :save-verdict-template-settings-value
             :organizationId sipoo-R-org-id
             :category "r"
             :path [:verdict-code]
             :value [:ehdollinen :ei-puollettu
                     :evatty :hyvaksytty])
    => (err :error.unauthorized))
  (fact "Save settings draft"
    (let [{modified :modified}
          (command sipoo :save-verdict-template-settings-value
                   :organizationId sipoo-R-org-id
                   :category "r"
                   :path [:verdict-code]
                   :value [:ehdollinen :ei-puollettu
                           :evatty :hyvaksytty])]
      modified => pos?
      (fact "Query settings"
        (query sipoo :verdict-template-settings
               :organizationId sipoo-R-org-id
               :category "r")
        => (contains {:settings {:draft    {:verdict-code ["ehdollinen" "ei-puollettu"
                                                           "evatty" "hyvaksytty"]}
                                 :modified modified}
                      :filled   false}))))
  (facts "Verdict date deltas"
    (letfn [(set-date-delta [k delta]
              (fact {:midje/description (format "Set %s delta to %s" k delta)}
                (command sipoo :save-verdict-template-settings-value
                         :organizationId sipoo-R-org-id
                         :category :r
                         :path [k]
                         :value (str delta)) => ok?))]
      (set-date-delta "julkipano" 1)
      (set-date-delta "anto" 2)
      (set-date-delta "muutoksenhaku" 3)
      (set-date-delta "lainvoimainen" 4)
      (set-date-delta "aloitettava" 1) ;; years
      (set-date-delta "voimassa" 2) ;; years
      ))
  (fact "Date delta validation"
    (command sipoo :save-verdict-template-settings-value
             :organizationId sipoo-R-org-id
             :category :r
             :path [:muutoksenhaku]
             :value "-8")=> invalid-value?)
  (fact "Board"
    (command sipoo :save-verdict-template-settings-value
             :organizationId sipoo-R-org-id
             :category :r
             :path [:lautakunta-muutoksenhaku]
             :value "10")=> ok?
    (command sipoo :save-verdict-template-settings-value
             :organizationId sipoo-R-org-id
             :category :r
             :path [:boardname]
             :value "Board of peers")=> ok?)
  (fact "Organization name"
    (command sipoo :save-verdict-template-settings-value
             :organizationId sipoo-R-org-id
             :category :r
             :path [:organization-name]
             :value "The Management") => ok?)
  (fact "The settings are now filled"
    (query sipoo :verdict-template-settings :organizationId sipoo-R-org-id :category "r")
    => (contains {:filled true})))

(fact "Sipoo categories"
  (:categories (query sipoo :verdict-template-categories :organizationId sipoo-R-org-id))
  => (contains ["r" "tj"] :in-any-order))


(fact "Create new template"
  (let [{:keys [id name draft
                modified category]} (init-verdict-template sipoo sipoo-R-org-id :r)]
    id => string?
    name => "P\u00e4\u00e4t\u00f6spohja"
    draft => nil
    modified => pos?
    category => "r"
    (fact "Fetch draft"
      (query sipoo :verdict-template :organizationId sipoo-R-org-id :template-id id)
      => (contains {:id       id
                    :name     name
                    :draft    nil
                    :modified modified}))
    (fact "Template list"
      (query sipoo :verdict-templates :organizationId sipoo-R-org-id)
      => (contains {:verdict-templates [{:id        id
                                         :name      name
                                         :modified  modified
                                         :deleted   false
                                         :category  "r"
                                         :published nil}]}))
    (fact "Change the name"
      (let [{later :modified}
            (command sipoo :set-verdict-template-name
                     :organizationId sipoo-R-org-id
                     :template-id id
                     :name "Uusi nimi")]
        (- later modified) => pos?
        (fact "Save draft data value"
          (let [{even-later :modified}
                (command sipoo :save-verdict-template-draft-value
                         :organizationId sipoo-R-org-id
                         :template-id id
                         :path [:giver]
                         :value :viranhaltija)]
            (- even-later later) => pos?
            (fact "Fetch draft again"
              (query sipoo :verdict-template :organizationId sipoo-R-org-id :template-id id)
              => (contains {:id       id
                            :name     "Uusi nimi"
                            :draft    {:giver "viranhaltija"}
                            :modified even-later}))
            (fact "Enable every delta"
              (command sipoo :save-verdict-template-draft-value
                       :organizationId sipoo-R-org-id
                       :template-id id
                       :path [:verdict-dates]
                       :value [:julkipano :anto :muutoksenhaku :lainvoimainen
                               :aloitettava :voimassa]) => ok?)
            (fact "Bad foreman value"
              (command sipoo :save-verdict-template-draft-value
                       :organizationId sipoo-R-org-id
                       :template-id id
                       :path [:vv-tj]
                       :value ["hiihoo"])
              => invalid-value?)
            (fact "Dynamic repeating conditions"
              (fact "Add conditions"
                (let [condition-id1   (add-template-condition sipoo sipoo-R-org-id id "Strict condition")
                      condition-id2   (add-template-condition sipoo sipoo-R-org-id id "Other condition")
                      empty-condition (add-template-condition sipoo sipoo-R-org-id id nil)]
                  (check-template-conditions sipoo
                                             sipoo-R-org-id
                                             id
                                             condition-id1 "Strict condition"
                                             condition-id2 "Other condition"
                                             empty-condition nil)
                  (fact "Remove condition"
                    (remove-template-condition sipoo sipoo-R-org-id id condition-id2)
                    (check-template-conditions sipoo sipoo-R-org-id id
                                               condition-id1 "Strict condition"
                                               empty-condition nil)))))
            (fact "Set foremen removed"
              (command sipoo :save-verdict-template-draft-value
                       :organizationId sipoo-R-org-id
                       :template-id id
                       :path [:removed-sections :foremen]
                       :value true) => ok?)
            (facts "Set plans removed"
              (let [{last-edit :modified}
                    (command sipoo :save-verdict-template-draft-value
                             :organizationId sipoo-R-org-id
                             :template-id id
                             :path [:removed-sections :plans]
                             :value true) => ok?]
                (fact "Fetch draft to see the compound items are OK"
                  (query sipoo :verdict-template :organizationId sipoo-R-org-id :template-id id)
                  => (contains {:id       id
                                :name     "Uusi nimi"
                                :draft    (contains {:giver            "viranhaltija"
                                                     :verdict-dates    ["julkipano" "anto"
                                                                        "muutoksenhaku" "lainvoimainen"
                                                                        "aloitettava" "voimassa"]
                                                     :removed-sections {:foremen true
                                                                        :plans   true}})
                                :modified last-edit
                                :filled   true}))))
            (fact "Change verdict template title"
              (command sipoo :save-verdict-template-draft-value
                       :organizationId sipoo-R-org-id
                       :template-id id
                       :path [:title]
                       :value "My title") => ok?)
            (fact "Change verdict template subtitle"
              (command sipoo :save-verdict-template-draft-value
                       :organizationId sipoo-R-org-id
                       :template-id id
                       :path [:subtitle]
                       :value "My other title") => ok?)
            (facts "Publish template"
              (publish-verdict-template sipoo sipoo-R-org-id id) => ok?
              (facts "Required fields"
                (fact "The template cannot be published if any settings required field is empty"
                  (command sipoo :save-verdict-template-settings-value
                           :organizationId sipoo-R-org-id
                           :category "r" :path [:boardname] :value "")
                  => (contains {:filled false})
                  (publish-verdict-template sipoo sipoo-R-org-id id) => (err :pate.required-fields)
                  (command sipoo :save-verdict-template-settings-value
                           :organizationId sipoo-R-org-id
                           :category "r" :path [:boardname] :value "Board of peers")
                  => (contains {:filled true})
                  (publish-verdict-template sipoo sipoo-R-org-id id) => ok?)
                (fact "The template cannot be published if any required field is empty"
                  (command sipoo :save-verdict-template-draft-value
                           :organizationId sipoo-R-org-id
                           :template-id id :path [:giver] :value "")
                  => (contains {:filled false})
                  (publish-verdict-template sipoo sipoo-R-org-id id) => (err :pate.required-fields)))
              (let [{filled    :filled
                     last-edit :modified}  (command sipoo :save-verdict-template-draft-value
                                                    :organizationId sipoo-R-org-id
                                                    :template-id id
                                                    :path [:giver]
                                                    :value :viranhaltija)
                    {published :published} (publish-verdict-template sipoo sipoo-R-org-id id)]
                filled => true
                (- published last-edit) => pos?
                (fact "Delete template"
                  (command sipoo :toggle-delete-verdict-template
                           :organizationId sipoo-R-org-id
                           :template-id id
                           :delete true) => ok?)
                (fact "Template list again. Publishing and deletion do not affect modified timestamp."
                  (-> (query sipoo :verdict-templates :organizationId sipoo-R-org-id) :verdict-templates first)
                  => (contains {:id        id
                                :name      "Uusi nimi"
                                :modified  last-edit
                                :deleted   true
                                :category  "r"
                                :published published}))
                (fact "Name change not allowed for deleted template"
                  (command sipoo :set-verdict-template-name
                           :organizationId sipoo-R-org-id
                           :template-id id
                           :name "Foo")
                  => (err :error.verdict-template-deleted))
                (fact "Draft data update not allowed for deleted template"
                  (command sipoo :save-verdict-template-draft-value
                           :organizationId sipoo-R-org-id
                           :template-id id
                           :path [:hii]
                           :value "Foo")
                  => (err :error.verdict-template-deleted))
                (fact "Publish not allowed for deleted template"
                  (publish-verdict-template sipoo sipoo-R-org-id id)
                  => (err :error.verdict-template-deleted))
                (fact "Fetch draft not allowed for deleted template"
                  (query sipoo :verdict-template
                         :organizationId sipoo-R-org-id
                         :template-id id)
                  => (err :error.verdict-template-deleted))
                (fact "Copying is allowed also for deleted templates"
                  (let [{:keys [copy-id copy-modified copy-published copy-draft copy-name copy-category]}
                        (prefix-keys (command sipoo :copy-verdict-template
                                              :organizationId sipoo-R-org-id
                                              :template-id id)
                                     :copy-)]
                    copy-id =not=> id
                    (- copy-modified published) => pos?
                    copy-published => nil
                    copy-category => "r"
                    copy-name => "Uusi nimi (kopio)"
                    copy-draft => (contains {:giver            "viranhaltija"
                                             :verdict-dates    ["julkipano" "anto"
                                                                "muutoksenhaku" "lainvoimainen"
                                                                "aloitettava" "voimassa"]
                                             :removed-sections {:foremen true
                                                                :plans   true}})
                    (fact "Conditions are copied"
                      (-> copy-draft :conditions vals)
                      => (just [{:condition "Strict condition"} {}] :in-any-order))
                    (fact "Editing copy draft does not affect original"
                      (command sipoo :save-verdict-template-draft-value
                               :organizationId sipoo-R-org-id
                               :template-id copy-id
                               :path [:paatosteksti]
                               :value "This is the verdict.") => ok?
                      (fact "Copy has new data"
                        (-> (query sipoo :verdict-template
                                   :organizationId sipoo-R-org-id
                                   :template-id copy-id)
                            :draft :paatosteksti)
                        => "This is the verdict.")
                      (fact "Restore the deleted template"
                        (command sipoo :toggle-delete-verdict-template
                                 :organizationId sipoo-R-org-id
                                 :template-id id
                                 :delete false) => ok?)
                      (fact "The original (restored) template does not have new data"
                        (-> (query sipoo :verdict-template
                                   :organizationId sipoo-R-org-id
                                   :template-id id)
                            :draft :paatosteksti)
                        => nil)
                      (fact "Template list has both templates"
                        (->> (query sipoo :verdict-templates :organizationId sipoo-R-org-id)
                             :verdict-templates
                             (map #(select-keys % [:id :name])))
                        =>  [{:id id :name "Uusi nimi"}
                             {:id copy-id :name "Uusi nimi (kopio)"}]))))))))))))

(fact "Delete nonexisting template"
  (command sipoo :toggle-delete-verdict-template
           :organizationId sipoo-R-org-id
           :template-id "bad-id" :delete true)
  => (err :error.verdict-template-not-found))

(fact "Copy nonexisting template"
  (command sipoo :copy-verdict-template
           :organizationId sipoo-R-org-id
           :template-id "bad-id")
  => (err :error.verdict-template-not-found))

(facts "Data path and value validation"
  (let [{id :id} (init-verdict-template sipoo sipoo-R-org-id :r)]
    (command sipoo :save-verdict-template-draft-value
             :organizationId sipoo-R-org-id
             :template-id id
             :path [:verdict-dates]
             :value ["bad"])=> invalid-value?
    (command sipoo :save-verdict-template-draft-value
             :organizationId sipoo-R-org-id
             :template-id id
             :path [:bad :path]
             :value false)=> (err :error.invalid-value-path)
    (command sipoo :save-verdict-template-draft-value
             :organizationId sipoo-R-org-id
             :template-id id
             :path [:verdict-code]
             :value :bad) => invalid-value?
    (command sipoo :save-verdict-template-draft-value
             :organizationId sipoo-R-org-id
             :template-id id
             :path [:verdict-code]
             :value :hyvaksytty) => ok?))

(facts "Operation default verdict template"
  (fact "No defaults yet"
    (:templates (query sipoo :default-operation-verdict-templates
                       :organizationId sipoo-R-org-id))
    => {})
  (let [{old-id :id}      (->> (query sipoo :verdict-templates
                                      :organizationId sipoo-R-org-id)
                               :verdict-templates
                               (util/find-first :published))
        {template-id :id} (init-verdict-template sipoo sipoo-R-org-id :r)]
    (fact "Fill required fields"
      (command sipoo :save-verdict-template-draft-value
               :organizationId sipoo-R-org-id
               :template-id template-id
               :path [:giver]
               :value :viranhaltija)
      => (contains {:filled true}))
    (fact "Verdict template not published"
      (command sipoo :set-default-operation-verdict-template
               :organizationId sipoo-R-org-id
               :operation "pientalo" :template-id template-id)
      => (err :error.verdict-template-not-published))
    (fact "Publish the verdict template"
      (publish-verdict-template sipoo sipoo-R-org-id template-id)
      => ok?)
    (fact "Selectable verdict templates"
      (:items (query sipoo :selectable-verdict-templates
                     :organizationId sipoo-R-org-id))
      => {:R  [{:id old-id :name "Uusi nimi"}
                {:id template-id :name "P\u00e4\u00e4t\u00f6spohja"}]
          :P  []
          :YA []
          :tyonjohtajan-nimeaminen-v2 []})
    (fact "Delete verdict template"
      (command sipoo :toggle-delete-verdict-template
               :organizationId sipoo-R-org-id
               :template-id template-id :delete true)
      => ok?)
    (fact "Verdict template not editable"
      (command sipoo :set-default-operation-verdict-template
               :organizationId sipoo-R-org-id
               :operation "pientalo" :template-id template-id)
      => (err :error.verdict-template-deleted))
    (fact "Delete verdict template"
      (command sipoo :toggle-delete-verdict-template
               :organizationId sipoo-R-org-id
               :template-id template-id :delete false)
      => ok?)
    (fact "Bad operation"
      (command sipoo :set-default-operation-verdict-template
               :organizationId sipoo-R-org-id
               :operation "bad-operation" :template-id template-id)
      => (err :error.unknown-operation))
    (fact "Bad operation category"
      (command sipoo :set-default-operation-verdict-template
               :organizationId sipoo-R-org-id
               :operation "rasitetoimitus" :template-id template-id)
      => (err :error.invalid-category))
    (fact "Set template for operation"
      (command sipoo :set-default-operation-verdict-template
               :organizationId sipoo-R-org-id
               :operation "pientalo" :template-id template-id)
      => ok?)
    (fact "Defaults are no longer empty"
      (:templates (query sipoo :default-operation-verdict-templates
                         :organizationId sipoo-R-org-id))
      => {:pientalo template-id})
    (fact "Set template for another operation"
      (command sipoo :set-default-operation-verdict-template
               :organizationId sipoo-R-org-id
               :operation "muu-laajentaminen" :template-id template-id)
      => ok?)
    (fact "Defaults has two items"
      (:templates (query sipoo :default-operation-verdict-templates
                         :organizationId sipoo-R-org-id))
      => {:pientalo template-id :muu-laajentaminen template-id})
    (fact "Empty template-id clears default"
      (command sipoo :set-default-operation-verdict-template
               :organizationId sipoo-R-org-id
               :operation "muu-laajentaminen" :template-id "")
      => ok?
      (:templates (query sipoo :default-operation-verdict-templates
                         :organizationId sipoo-R-org-id))
      => {:pientalo template-id})
    (fact "Deleted template can no longer be default"
      (command sipoo :toggle-delete-verdict-template
               :organizationId sipoo-R-org-id
               :template-id template-id :delete true)
      => ok?
      (:templates (query sipoo :default-operation-verdict-templates
                         :organizationId sipoo-R-org-id))
      => {})
    (fact "Undoing deletion makes template default again"
      (command sipoo :toggle-delete-verdict-template
               :organizationId sipoo-R-org-id
               :template-id template-id :delete false)
      => ok?
      (:templates (query sipoo :default-operation-verdict-templates
                         :organizationId sipoo-R-org-id))
      => {:pientalo template-id})

    (facts "Application verdict templates"
      (let [{app-id :id} (create-and-submit-application pena
                                                        :address "Jianguomen"
                                                        :operation :pientalo
                                                        :propertyId sipoo-property-id)]
        (fact "Create and delete verdict template"
          (let [{tmp-id :id} (init-verdict-template sipoo sipoo-R-org-id :r)]
            (command sipoo :toggle-delete-verdict-template
                     :organizationId sipoo-R-org-id
                     :template-id tmp-id :delete true) => ok?))
        (fact "Rename assumed default"
          (command sipoo :set-verdict-template-name
                   :organizationId sipoo-R-org-id
                   :template-id template-id
                   :name "Oletus"))
        (:templates (query sonja :application-verdict-templates :id app-id))
        => (just [(just {:id       template-id
                         :default? true
                         :name     "Oletus"})
                  (just {:id       #"\w+"
                         :default? false
                         :name     "Uusi nimi"})] :in-any-order)))))



;;; Verdicts

(defn find-plan-id [references fi]
  (:id (util/find-by-key :fi fi (:plans references))))

(defn find-review-id [references fi]
  (:id (util/find-by-key :fi fi (:reviews references))))

(defn verdict-neighbors [app-id open-verdict]
  (facts "Verdict neighbors"
    (fact "Add two neighbors to the application"
      (let [first-prop-id  "75341600880088"
            second-prop-id "75341600990099"
            first-id       (:neighborId (command sonja :neighbor-add :id app-id
                                                 :name "First neighbor"
                                                 :street "Naapurintie 4"
                                                 :city "Sipoo"
                                                 :zip "12345"
                                                 :email "first.neighbor@example.com"
                                                 :propertyId first-prop-id))
            second-id      (:neighborId (command sonja :neighbor-add :id app-id
                                                 :name "Second neighbor"
                                                 :street "Naapurintie 6"
                                                 :city "Sipoo"
                                                 :zip "12345"
                                                 :email "second.neighbor@example.com"
                                                 :propertyId second-prop-id))]
        first-id => ss/not-blank?
        second-id => ss/not-blank?
        (fact "Verdict has now neighbor states"
          (-> (open-verdict)
              :verdict :data :neighbor-states)
          => (just [(just {:property-id first-prop-id :state "open" :created pos?})
                    (just {:property-id second-prop-id :state "open" :created pos?})]
                   :in-any-order))
        (fact "Mark the first neighbor heard"
          (command sonja :neighbor-mark-done :id app-id
                   :lang :fi
                   :neighborId first-id) => ok?)
        (fact "Neighbor states have been updated"
          (-> (open-verdict)
              :verdict :data :neighbor-states)
          => (just [(just {:property-id first-prop-id :state "mark-done" :created pos?})
                    (just {:property-id second-prop-id :state "open" :created pos?})]
                   :in-any-order))
        (fact "Remove the second neighbor"
          (command sonja :neighbor-remove :id app-id
                   :neighborId second-id) => ok?)
        (fact "Neighbor states have been updated"
          (-> (open-verdict)
              :verdict :data :neighbor-states)
          => (just [(just {:property-id first-prop-id :state "mark-done" :created pos?})]))))))

(defn check-post-verdict-building-data [{:keys [buildings documents]} doc-id operation-id]
  (fact "pientalo VTJ-PRT still same"
    (->> documents
         (util/find-by-id doc-id)
         :data :valtakunnallinenNumero :value) => "1234567881")
  (fact "primary-op VTJ-PRT updated to doc"
    (->> documents
         (util/find-first #(= (get-in % [:schema-info :op :id]) operation-id))
         :data :valtakunnallinenNumero :value) => "1234567892")
  (facts "buildings"
    (count buildings) => 2
    (fact "first (primaryOperation sisatila-muutos)"
      (-> buildings first :nationalId) => "1234567892"
      (-> buildings first :location) => [406216.0 6686856.0]
      (-> buildings first :location-wgs84) => (coord/convert "EPSG:3067" "WGS84" 5 [406216.0 6686856.0]))
    (fact "second (secondaryOperation pientalo"
      (-> buildings second :nationalId) => "1234567881"
      (-> buildings second :location) => [406216.0 6686856.0]
      (-> buildings second :location-wgs84) => (coord/convert "EPSG:3067" "WGS84" 5 [406216.0 6686856.0]))))

(defn add-review [& kvs]
  (add-repeating-setting sipoo sipoo-R-org-id :r :reviews :add-review kvs))

(defn add-plan [& kvs]
  (add-repeating-setting sipoo sipoo-R-org-id :r :plans :add-plan kvs))

(facts "Settings dependencies (reviews and plans)"
  (let [{template-id :id
         draft       :draft
         modified    :modified} (init-verdict-template sipoo sipoo-R-org-id :r)]
    (fact "Initially no reviews nor plans in template"
      draft => nil)
    (fact "Add review"
      (let [review-id (add-review :fi "Katselmus" :sv "Syn" :en "Review"
                                  :type "pohjakatselmus")]
        review-id => truthy
        (fact "Regularly opened template does not include review"
          (query sipoo :verdict-template
                 :organizationId sipoo-R-org-id
                 :template-id template-id)
          => (contains {:id       template-id
                        :modified modified
                        :draft    nil}))
        (fact "Update and open"
          (command sipoo :update-and-open-verdict-template
                   :organizationId sipoo-R-org-id
                   :template-id template-id)
          => (contains {:draft {:reviews {(keyword review-id) {:review-index 0
                                                               :text-fi      "Katselmus"
                                                               :text-sv      "Syn"
                                                               :text-en      "Review"}}}}))
        (fact "Include review in verdict"
          (command sipoo :save-verdict-template-draft-value
                   :organizationId sipoo-R-org-id
                   :template-id template-id
                   :path [:reviews review-id :included]
                   :value true)
          => ok?)
        (fact "Review text is read-only"
          (some-> (command sipoo :save-verdict-template-draft-value
                           :organizationId sipoo-R-org-id
                           :template-id template-id
                           :path [:reviews review-id :text-fi]
                           :value "foo")
                  :errors
                  flatten
                  last
                  keyword)
          => :error.read-only)
        (fact "Change review's name"
          (command sipoo :save-verdict-template-settings-value
                   :organizationId sipoo-R-org-id
                   :category :r
                   :path [:reviews review-id :fi]
                   :value "sumlestaK") => ok?)
        (fact "Name updated in the template after update and open"
          (command sipoo :update-and-open-verdict-template
                   :organizationId sipoo-R-org-id
                   :template-id template-id)
          => (contains {:draft {:reviews {(keyword review-id) {:review-index 0
                                                               :text-fi      "sumlestaK"
                                                               :text-sv      "Syn"
                                                               :text-en      "Review"
                                                               :included     true}}}}))
        (fact "Remove review"
          (command sipoo :save-verdict-template-settings-value
                   :organizationId sipoo-R-org-id
                   :category :r
                   :path [:reviews review-id :remove-review]
                   :value nil) => ok?)
        (fact "Add plan"
          (let [plan-id (add-plan :fi "Suunnitelma" :sv "Plan" :en "Plan")]
            (fact "Update and open: no review, but a plan"
              (command sipoo :update-and-open-verdict-template
                       :organizationId sipoo-R-org-id
                       :template-id template-id)
              => (contains {:draft (just {:plans {(keyword plan-id) {:plan-index 0
                                                                     :text-fi    "Suunnitelma"
                                                                     :text-sv    "Plan"
                                                                     :text-en    "Plan"}}})}))
            (fact "Remove plan"
              (command sipoo :save-verdict-template-settings-value
                       :organizationId sipoo-R-org-id
                       :category :r
                       :path [:plans plan-id :remove-plan]
                       :value nil) => ok?
              (:draft (command sipoo :update-and-open-verdict-template
                               :organizationId sipoo-R-org-id
                               :template-id template-id))
              => nil)))))))

(def space-saving-definitions
  (let [review1              (add-review :fi "K1" :sv "S1" :en "R1" :type :aloituskokous)
        review2              (add-review :fi "Helsinki" :sv "Stockholm" :en "London" :type :rakennekatselmus)
        review3              (add-review :fi "K3" :sv "S3" :en "R3" :type :pohjakatselmus)
        review4              (add-review :fi "K4" :sv "S4" :en "R4" :type :loppukatselmus)
        plan1                (add-plan :fi "S1" :sv "P1" :en "P1")
        plan2                (add-plan :fi "Tampere" :sv "Oslo" :en "Washington")
        plan3                (add-plan :fi "S3" :sv "P3" :en "P3")
        plan4                (add-plan :fi "S4" :sv "P4" :en "P4")
        {template-id :id
         draft       :draft} (init-verdict-template sipoo sipoo-R-org-id :r)
        good-condition       (add-template-condition sipoo sipoo-R-org-id template-id "Good condition")
        empty-condition      (add-template-condition sipoo sipoo-R-org-id template-id nil)
        blank-condition      (add-template-condition sipoo sipoo-R-org-id template-id "    ")
        other-condition      (add-template-condition sipoo sipoo-R-org-id template-id "Other condition")
        remove-condition     (add-template-condition sipoo sipoo-R-org-id template-id "Remove condition")
        {app-id :id}         (create-and-open-application pena
                                                          :propertyId sipoo-property-id
                                                          :operation  :sisatila-muutos
                                                          :address "Dongdaqiao Lu")]
    {:review1         review1         :review2          review2 :review3        review3 :review4 review4
     :plan1           plan1           :plan2            plan2   :plan3          plan3   :plan4   plan4
     :template-id     template-id     :draft            draft   :good-condition good-condition
     :empty-condition empty-condition :blank-condition  blank-condition
     :other-condition other-condition :remove-condition remove-condition
     :app-id app-id}))

(facts "Template conditions"
  (let [{:keys [plan1 plan2 plan3 remove-condition review1 review2 review3 template-id]} space-saving-definitions]
    (fact "Remove condition"
      (remove-template-condition sipoo sipoo-R-org-id template-id remove-condition))
    (fact "Full template without attachments"
      (command sipoo :set-verdict-template-name
               :organizationId sipoo-R-org-id
               :template-id template-id
               :name "Full template") => ok?)

    (set-template-draft-values sipoo sipoo-R-org-id template-id
                               :language "fi"
                               :verdict-dates ["julkipano" "anto"
                                               "muutoksenhaku" "lainvoimainen"
                                               "aloitettava" "voimassa"]
                               "giver" :viranhaltija
                               "verdict-code" :ehdollinen
                               "paatosteksti" "Verdict text."
                               :iv-tj true
                               :iv-tj-included true
                               :erityis-tj-included true
                               :vastaava-tj-included true
                               :vv-tj true
                               [:plans plan1 :included] true
                               [:plans plan1 :selected] true
                               [:plans plan2 :included] true
                               [:plans plan2 :selected] true
                               [:plans plan3 :included] true
                               [:reviews review1 :included] true
                               [:reviews review1 :selected] true
                               [:reviews review2 :included] true
                               [:reviews review2 :selected] true
                               [:reviews review3 :included] true
                               "appeal" "Humble appeal."
                               "complexity" "medium"
                               "complexity-text" "Complex explanation."
                               "autopaikat" true
                               "paloluokka" true
                               "vss-luokka" true
                               "kokoontumistilanHenkilomaara" true
                               [:removed-sections :attachments] true)
    (fact "Delete review 1"
      (command sipoo :save-verdict-template-settings-value
               :organizationId sipoo-R-org-id
               :category :r
               :path [:reviews review1 :remove-review]
               :value nil)=> ok?)
    (fact "Delete plan 1"
      (command sipoo :save-verdict-template-settings-value
               :organizationId sipoo-R-org-id
               :category :r
               :path [:plans plan1 :remove-plan]
               :value nil) => ok?)))

(defn verdict-fn-factory [app-id verdict-id]
  (let [open-fn #(query sonja :pate-verdict :id app-id
                        :verdict-id verdict-id)]
    {:open          open-fn
     :edit          #(command sonja :edit-pate-verdict :id app-id
                              :verdict-id verdict-id
                              :path (map name (flatten [%1]))
                              :value %2)
     :check-changes (fn [{:keys [changes]} expected]
                      (fact "Check changes"
                        changes => expected)
                      (fact "Check that verdict has been updated"
                        (-> (open-fn) :verdict :data)
                        => (contains (reduce (fn [acc [x y]]
                                               (assoc acc
                                                      (-> x first keyword)
                                                      y))
                                             {}
                                             changes))))}))

(defn check-suomifi [apikey app-id verdict-id success?]
  (facts {:midje/description (format "Suomi.fi check: %s, %s, %s -> %s"
                                     apikey app-id verdict-id success?)}
    (fact "Always fails if Suomi.fi not enabled"
      (command sipoo :toggle-organization-suomifi-messages
               :organizationId sipoo-R-org-id
               :section :verdict
               :enabled false) => ok?
      (query apikey :can-send-via-suomifi :id app-id
             :verdict-id verdict-id) => fail?)
    (fact {:midje/description (str "Pseudo-query should "
                                   (if success? "succeed" "fail"))}
      (command sipoo :toggle-organization-suomifi-messages
               :organizationId sipoo-R-org-id
               :section :verdict
               :enabled true) => ok?
      (query apikey :can-send-via-suomifi :id app-id
             :verdict-id verdict-id)
      => (if success? ok? fail?))))

(facts "Verdicts"
  (let [{:keys [good-condition other-condition template-id app-id]} space-saving-definitions]
    (facts "Pena fills and submits application"
      (fill-sisatila-muutos-application pena app-id)
      (command pena :submit-application :id app-id) => ok?

      (fact "Sonja approves application"
        (command sonja :update-app-bulletin-op-description
                 :id app-id
                 :description "Bullet the blue sky.") => ok?
        (command sonja :approve-application :id app-id :lang "fi") => ok?)
      (facts "New verdict using Full template"
        (let [application        (query-application sonja app-id)
              verdict-fn-factory (partial verdict-fn-factory app-id)]
          (fact "Error: unpublished template-id for verdict draft"
            (command sonja :new-pate-verdict-draft :id app-id :template-id template-id)
            => fail?)
          (fact "Publish Full template"
            (publish-verdict-template sipoo sipoo-R-org-id template-id)
            => ok?)
          (fact "Pena cannot create verdict"
            (command pena :new-pate-verdict-draft :id app-id :template-id template-id)
            => (err :error.unauthorized))
          (fact "Error: bad template-id for verdict draft"
            (command sonja :new-pate-verdict-draft :id app-id :template-id "bad")
            => fail?)
          (fact "Error: Pate disabled in Sipoo"
            (toggle-sipoo-pate false)
            (command sonja :new-pate-verdict-draft
                     :id app-id :template-id template-id)
            => (err :error.pate-disabled))
          (fact "Enable Pate in Sipoo"
            (toggle-sipoo-pate true)
            (query sonja :pate-verdict-tab :id app-id) => ok?)
          (fact "Sonja clears application handler"
            (let [{handlers :handlers} (query-application sonja app-id)]
              (command sonja :remove-application-handler :id app-id
                       :handlerId (-> handlers first :id))))
          (fact "Sonja creates verdict draft"
            (let [{verdict-id :verdict-id}       (command sonja :new-pate-verdict-draft
                                                          :id app-id :template-id template-id)
                  draft                          (query sonja :pate-verdict
                                                        :id app-id :verdict-id verdict-id)
                  data                           (-> draft :verdict :data)
                  references                     (-> draft :references)
                  op-id                          (-> data :buildings keys first keyword)
                  {open-verdict  :open
                   edit-verdict  :edit
                   check-changes :check-changes} (verdict-fn-factory verdict-id)

                  check-error                    (fn [{errors :errors} & [kw err-kw]]
                                (if kw
                                  (fact "Check errors"
                                    (some (fn [[x y]]
                                            (and (= kw (util/kw-path x))
                                                 (keyword y)))
                                          errors) => (or err-kw
                                                         :error.invalid-value))
                                  (fact "No errors"
                                    errors => nil)))]
              data => (contains {:language         "fi"
                                 :handler          ""
                                 :appeal           "Humble appeal."
                                 ;;:purpose          ""
                                 :verdict-text     "Verdict text."
                                 ;;:anto             ""
                                 :complexity       "medium"
                                 :foremen          ["iv-tj"]
                                 :verdict-code     "ehdollinen"
                                 ;;:collateral       ""
                                 ;;:rights           ""
                                 :plans-included   true
                                 :plans            [(find-plan-id references "Tampere")]
                                 :foremen-included true
                                 ;;:neighbors        ""
                                 :neighbor-states  []
                                 :reviews-included true
                                 :reviews          [(find-review-id references "Helsinki")]
                                 :deviations       "Deviation from mean."
                                 :address          "Dongdaqiao Lu"
                                 :operation        "Description"})
              (fact "Pena cannot see verdict draft"
                (query pena :pate-verdict
                       :id app-id :verdict-id verdict-id)
                => (err :error.verdict-not-found))

              (facts "Verdict list "
                (fact "Pena's list of verdicts is empty"
                  (:verdicts (query pena :pate-verdicts :id app-id))
                  => [])
                (fact "Sonja's list of verdicts contains draft, but no giver"
                  (:verdicts (query sonja :pate-verdicts :id app-id))
                  => (just [(just {:category      "r"
                                   :id            verdict-id
                                   :legacy?       false
                                   :modified      pos?
                                   :proposal?     false
                                   :replaced?     false
                                   :verdict-state "draft"
                                   :title         "Luonnos"})]))
                (fact "Handler is shown as giver"
                  (edit-verdict :handler "Sonja Handler")
                  (:verdicts (query sonja :pate-verdicts :id app-id))
                  => (just [(contains {:giver "Sonja Handler"})]))
                (fact "Giver overrides handler in the list"
                  (edit-verdict :giver "Sonja Giver")
                  (:verdicts (query sonja :pate-verdicts :id app-id))
                  => (just [(contains {:giver "Sonja Giver"})])))

              (fact "Attachments cannot be added to the verdict"
                (edit-verdict :attachments ["foobar"]) => fail?)
              (facts "Verdict draft conditions"
                (let [conditions (->> data :conditions
                                      (reduce-kv (fn [acc k v]
                                                   (assoc acc (:condition v) k))
                                                 {}))]
                  (fact "Two conditions"
                    (keys conditions)
                    => (just ["Good condition" "Other condition"] :in-any-order))
                  (fact "New ids"
                    (util/intersection-as-kw (vals conditions)
                                             [good-condition other-condition])
                    => empty?)
                  (let [good-id  (keyword (get conditions "Good condition"))
                        other-id (keyword (get conditions "Other condition"))
                        new-id   (add-verdict-condition sonja app-id verdict-id "New condition")]
                    (check-verdict-conditions sonja app-id verdict-id
                                              good-id "Good condition"
                                              other-id "Other condition"
                                              new-id "New condition")
                    (remove-verdict-condition sonja app-id verdict-id new-id)
                    (check-verdict-conditions sonja app-id verdict-id
                                              good-id "Good condition"
                                              other-id "Other condition"))))
              (fact "Statements in the verdict draft"
                (:statements data) => (just [(just {:text   "Paloviranomainen"
                                                    :given  pos?
                                                    :status "puollettu"})
                                             {:text   "Stakeholder"
                                              :given  nil
                                              :status nil}]
                                            :in-any-order))
              (fact "No section"
                (:section data) => nil?)
              (fact "New verdict is not filled"
                draft => (contains {:filled false}))
              (fact "Verdict cannot be published since required fields are missing"
                (command sonja :publish-pate-verdict :id app-id
                         :verdict-id verdict-id)
                => (err :pate.required-fields))
              (fact "Ditto for preview"
                (decode-body (raw sonja :preview-pate-verdict :id app-id
                                  :verdict-id verdict-id))
                => (err :pate.required-fields))
              (fact "Building info is mostly empty but contains the template fields"
                data => (contains {:buildings {op-id {:description   ""
                                                      :show-building true
                                                      ;;:vss-luokka             ""
                                                      ;;:kiinteiston-autopaikat ""
                                                      :building-id   "122334455R"
                                                      :operation     "sisatila-muutos"
                                                      ;;:rakennetut-autopaikat  ""
                                                      :tag           ""
                                                      ;;:autopaikat-yhteensa    ""
                                                      ;;:paloluokka             ""
                                                      :order         "0"}}}))
              (fact "Cannot set section for non-board verdict"
                (edit-verdict :verdict-section "8")
                => (err :error.invalid-value-path))
              (fact "Set handler"
                (edit-verdict "handler" "Sonja Sibbo") => no-errors?)
              (facts "Verdict references"
                (let [{:keys [foremen plans reviews]} (:references draft)]
                  (fact "Foremen"
                    foremen => (just ["vastaava-tj" "iv-tj" "erityis-tj"] :in-any-order))
                  (fact "Reviews"
                    reviews =>  (just [{:id   (find-review-id references "Helsinki")
                                        :fi   "Helsinki" :sv "Stockholm" :en "London"
                                        :type "rakennekatselmus"}
                                       {:id   (find-review-id references "K3") :fi "K3" :sv "S3" :en "R3"
                                        :type "pohjakatselmus"}] :in-any-order))
                  (fact "Plans"
                    plans => (just [{:id (find-plan-id references "Tampere") :fi "Tampere" :sv "Oslo" :en "Washington"}
                                    {:id (find-plan-id references "S3") :fi "S3" :sv "P3" :en "P3"}]
                                   :in-any-order))))
              (facts "Verdict dates"
                (fact "Set julkipano date"
                  (edit-verdict "julkipano" "20.9.2017") => no-errors?)
                (fact "Set verdict date"
                  (let [{:keys [modified changes]}
                        (edit-verdict "verdict-date" "27.9.2017")]
                    changes  => []
                    modified => pos?
                    (fact "Verdict data has been updated"
                      (let [data (:verdict (open-verdict))]
                        (:modified data) => modified
                        (:data data) => (contains {:verdict-date (date/timestamp "27.9.2017")
                                                   :julkipano    (date/timestamp "20.9.2017")})))))
                (fact "Set automatic dates"
                  (check-changes (edit-verdict "automatic-verdict-dates" true)
                                 [[["julkipano"] (date/timestamp "28.9.2017")]
                                  [["anto"] (date/timestamp "2.10.2017")]
                                  [["muutoksenhaku"] (date/timestamp "5.10.2017")]
                                  [["lainvoimainen"] (date/timestamp "9.10.2017")]
                                  [["aloitettava"] (date/timestamp "9.10.2018")]
                                  [["voimassa"] (date/timestamp "9.10.2020")]]))
                (fact "Clearing the verdict date does not clear automatically calculated dates"
                  (edit-verdict "verdict-date" "")
                  => (contains {:changes []}))
                (fact "Changing the verdict date recalculates others"
                  (check-changes (edit-verdict :verdict-date "6.10.2017")
                                 [[["julkipano"] (date/timestamp "9.10.2017")]
                                  [["anto"] (date/timestamp "11.10.2017")]
                                  [["muutoksenhaku"] (date/timestamp "16.10.2017")]
                                  [["lainvoimainen"] (date/timestamp "20.10.2017")]
                                  [["aloitettava"] (date/timestamp "20.10.2018")]
                                  [["voimassa"] (date/timestamp "20.10.2020")]])))
              (facts "Verdict foremen"
                (fact "vv-tj not in the template"
                  (check-error (edit-verdict :foremen ["vv-tj"]) :foremen))
                (fact "Vastaava-tj is OK"
                  (check-error (edit-verdict :foremen ["vastaava-tj"]))))
              (facts "Verdict plans"
                (fact "Bad plan not in the template"
                  (check-error (edit-verdict :plans ["bad"]) :plans))
                (fact "Empty plans is OK"
                  (check-error (edit-verdict :plans [])))
                (fact "Set good  plan"
                  (check-error (edit-verdict :plans [(find-plan-id references "S3")]))))
              (facts "Verdict reviews"
                (fact "Bad review not in the template"
                  (check-error (edit-verdict :reviews ["bad"]) :reviews))
                (fact "Empty reviews is OK"
                  (check-error (edit-verdict :reviews [])))
                (fact "Set good  review"
                  (check-error (edit-verdict :reviews [(find-review-id references "K3")]))))
              (verdict-neighbors app-id open-verdict)
              (facts "Verdict buildings"
                (let [{doc-id :id} (util/find-first (util/fn-> :schema-info :op :id
                                                               (util/=as-kw op-id))
                                                    (:documents application))]
                  (fact "Request changes to the application"
                    (command sonja :request-for-complement :id app-id) => ok?)
                  (fact "Select building for sisatila-muutos operation"
                    (command sonja :merge-details-from-krysp :id app-id
                             :documentId doc-id
                             :buildingId "199887766E"
                             :overwrite false) => ok?)
                  (fact "Operation description"
                    (command sonja :update-op-description :id app-id
                             :op-id op-id
                             :desc "Hello world!") => ok?)
                  (fact "Set vss-luokka for the building"
                    (check-error (edit-verdict [:buildings op-id :vss-luokka] "Foo")))
                  (fact "Set kokoontumistilanHenkilomaara for the building"
                    (check-error (edit-verdict [:buildings op-id :kokoontumistilanHenkilomaara] "15")))
                  (fact "Verdict building info updated"
                    (-> (open-verdict) :verdict :data :buildings op-id)
                    => {:description                  "Hello world!"
                        :show-building                true
                        :vss-luokka                   "Foo"
                        :kokoontumistilanHenkilomaara "15"
                        ;;:kiinteiston-autopaikat ""
                        :building-id                  "199887766E"
                        :operation                    "sisatila-muutos"
                        ;;:rakennetut-autopaikat  ""
                        :tag                          ""
                        ;;:autopaikat-yhteensa    ""
                        ;;:paloluokka             ""
                        :order                        "0"})
                  (fact "Change building id to manual"
                    (command sonja :update-doc :id app-id :doc doc-id :updates [["buildingId" "other"]
                                                                                ["valtakunnallinenNumero" ""]
                                                                                ["manuaalinen_rakennusnro" "789"]])
                    => ok?))
                (fact "Add pientalo operation to the application"
                  (command pena :add-operation :id app-id
                           :operation "pientalo") => ok?)
                (let [{:keys [documents
                              secondaryOperations]} (query-application sonja app-id)
                      op-id-pientalo                (-> secondaryOperations
                                                        first  :id keyword)
                      {doc-id :id}                  (util/find-first
                                                      (util/fn-> :schema-info :op :id
                                                                 (util/=as-kw op-id-pientalo))
                                                      documents)]
                  (fact "New empty building in verdict"
                    (-> (open-verdict) :verdict :data :buildings)
                    => {op-id          {:description                  "Hello world!"
                                        :show-building                true
                                        :vss-luokka                   "Foo"
                                        ;;:kiinteiston-autopaikat ""
                                        :building-id                  "789"
                                        :operation                    "sisatila-muutos"
                                        :kokoontumistilanHenkilomaara "15"
                                        ;;:rakennetut-autopaikat  ""
                                        :tag                          ""
                                        ;;:autopaikat-yhteensa    ""
                                        ;;:paloluokka             ""
                                        :order                        "0"}
                        op-id-pientalo {:description   ""
                                        :show-building true
                                        ;;:vss-luokka             ""
                                        ;;:kiinteiston-autopaikat ""
                                        :building-id   ""
                                        :operation     "pientalo"
                                        ;;:rakennetut-autopaikat  ""
                                        :tag           ""
                                        ;;:autopaikat-yhteensa    ""
                                        ;;:paloluokka             ""
                                        :order         "1"}})
                  (fact "Add tag and description to pientalo"
                    (command pena :update-doc
                             :id app-id
                             :doc doc-id
                             :updates [["tunnus" "Hao"]]) => ok?
                    (command pena :update-op-description
                             :id app-id
                             :op-id op-id-pientalo
                             :desc "Hen piaoliang!") => ok?)
                  (fact "national-id update pre-verdict"
                    (api-update-building-data-call app-id {:form-params  {:operationId        (name op-id-pientalo)
                                                                          :nationalBuildingId "1234567881"
                                                                          :location           {:x 406216.0 :y 6686856.0}}
                                                           :content-type :json
                                                           :as           :json
                                                           :basic-auth   ["sipoo-r-backend" "sipoo"]}) => http200?
                    (->> (query-application sonja app-id) :documents
                         (util/find-by-id doc-id)
                         :data :valtakunnallinenNumero :value) => "1234567881")
                  (fact "Set kiinteiston-autopaikat for pientalo"
                    (check-error (edit-verdict [:buildings op-id-pientalo :kiinteiston-autopaikat]
                                               "8")))
                  (fact "Cannot edit non-existent building"
                    (edit-verdict [:buildings "bad-building" :paloluokka] "foo")
                    => fail?)
                  (fact "Change primary operation"
                    (command sonja :change-primary-operation :id app-id
                             :secondaryOperationId op-id-pientalo) => ok?)
                  (fact "Buildings updated"
                    (-> (open-verdict) :verdict :data :buildings)
                    => {op-id          {:description                  "Hello world!"
                                        :show-building                true
                                        :vss-luokka                   "Foo"
                                        :kokoontumistilanHenkilomaara "15"
                                        ;;:kiinteiston-autopaikat ""
                                        :building-id                  "789"
                                        :operation                    "sisatila-muutos"
                                        ;;:rakennetut-autopaikat  ""
                                        :tag                          ""
                                        ;;:autopaikat-yhteensa    ""
                                        ;;:paloluokka             ""
                                        :order                        "1"}
                        op-id-pientalo {:description            "Hen piaoliang!"
                                        :show-building          true
                                        ;;:vss-luokka             ""
                                        :kiinteiston-autopaikat "8"
                                        :building-id            "1234567881"
                                        :operation              "pientalo"
                                        ;;:rakennetut-autopaikat  ""
                                        :tag                    "Hao"
                                        ;;:autopaikat-yhteensa    ""
                                        ;;:paloluokka             ""
                                        :order                  "0"}})
                  (fact "Cannot publish in the complementNeeded state"
                    (command sonja :publish-pate-verdict :id app-id
                             :verdict-id verdict-id)
                    => (partial expected-failure? :error.command-illegal-state))
                  (fact "Preview fetch works, though"
                    (:headers (raw sonja :preview-pate-verdict :id app-id
                                   :verdict-id verdict-id))
                    => (contains {"Content-Disposition" (contains "P\u00e4\u00e4t\u00f6sluonnos")}))
                  (fact "PDF language is the same as the verdict language"
                    (edit-verdict :language :sv) => ok?
                    (:headers (raw sonja :preview-pate-verdict :id app-id
                                   :verdict-id verdict-id))
                    => (contains {"Content-Disposition" (contains "Beslututkast")}))
                  (fact "Change primary operation back"
                    (command sonja :change-primary-operation :id app-id
                             :secondaryOperationId op-id) => ok?)
                  (fact "Sonja approves the application again"
                    (command sonja :approve-application :id app-id :lang :fi)
                    => ok?)
                  (fact "Verdict-section is not included"
                    (-> (open-verdict) :verdict :inclusions)
                    =not=> (contains ["verdict-section"]))
                  (fact "Proposal is not supported for non-board verdict"
                    (command sonja :publish-verdict-proposal :id app-id
                             :verdict-id verdict-id)
                    => (err :error.verdict.not-board))
                  (sent-emails) ;; Clear inbox
                  (check-suomifi sonja app-id verdict-id false)
                  (fact "Sonja can publish the verdict"
                    (command sonja :publish-pate-verdict :id app-id
                             :verdict-id verdict-id)=> ok?)

                  (fact "Published verdict details"
                    (query pena :published-pate-verdict
                           :id app-id
                           :verdict-id verdict-id)
                    => (just {:ok      true
                              :verdict (just {:attachment-id string?
                                              :published     pos?
                                              :tags          string?
                                              :id            verdict-id})}))
                  (facts "Verdict has been published"
                    (let [{:keys [data inclusions]} (:verdict (open-verdict))]
                      (fact "Verdict's section is one"
                        (:verdict-section data) => "1")
                      (fact "Verdict-section is still not included"
                        inclusions =not=> (contains ["verdict-section"]))
                      (fact "Only given statements are included"
                        (-> data :statements count) => 1
                        (-> data :statements first)
                        => (just {:text   "Paloviranomainen"
                                  :given  pos?
                                  :status "puollettu"})))
                    (check-verdict-date sonja app-id (date/timestamp "6.10.2017")))

                  (Thread/sleep 100) ; wait for email delivery
                  (fact "Email notification about application state change is sent"
                    (let [email (last-email)]
                      (:to email) => (contains (email-for-key pena))
                      (:subject email) => "Lupapiste: Dongdaqiao Lu, Sipoo - hankkeen tila on nyt P\u00e4\u00e4t\u00f6s annettu"))
                  (fact "Published verdict can no longer be previewed"
                    (raw sonja :preview-pate-verdict :id app-id
                         :verdict-id verdict-id)
                    => fail?)
                  (check-suomifi sonja app-id verdict-id true)
                  (fact "Pena can see  published verdict"
                    (query pena :pate-verdict
                           :id app-id :verdict-id verdict-id)
                    => ok?)
                  (check-suomifi pena app-id verdict-id false)
                  (fact "Pena's list of verdicts contains the published verdict"
                    (map :id (:verdicts (query pena :pate-verdicts :id app-id)))
                    => [verdict-id])
                  (facts "Applications search"
                    (fact "New application with backing system verdict"
                      (let [bs-app-id (:id (create-and-submit-application pena
                                                                          :address "Xingfucun Lu"
                                                                          :propertyId sipoo-property-id
                                                                          :operation :purkaminen))]
                        (command sonja :check-for-verdict :id bs-app-id) => ok?
                        (fact "Applications search includes verdict dates"
                          (->> (datatables pena :applications-search) :data :applications
                               (map #(select-keys % [:address :state :verdictDate :kuntalupatunnus])))
                          => (just [{:address         "Dongdaqiao Lu"
                                     :state           "verdictGiven"
                                     :verdictDate     (date/timestamp "6.10.2017")
                                     :kuntalupatunnus nil}
                                    {:address         "Jianguomen"
                                     :state           "submitted"
                                     :kuntalupatunnus nil}
                                    {:address         "Xingfucun Lu"
                                     :state           "verdictGiven"
                                     :verdictDate     (date/timestamp "1.9.2013")
                                     :kuntalupatunnus "2013-01"}]
                                   :in-any-order)))))

                  (facts "buildings"
                    (let [{:keys [buildings]} (query-application sonja app-id)]
                      (count buildings) => 2
                      (fact "first (primaryOperation sisatila-muutos)"
                        (-> buildings first :nationalId) => ""
                        (-> buildings first :location) => nil
                        (-> buildings first :location-wgs84) => nil)
                      (fact "second (secondaryOperation pientalo"
                        (-> buildings second :nationalId) => "1234567881"
                        (-> buildings second :location) => [406216.0 6686856.0]
                        (-> buildings second :location-wgs84) => (coord/convert "EPSG:3067" "WGS84" 5 [406216.0 6686856.0]))))
                  (facts "Modify template"
                    (letfn [(edit-template [path value]
                              (fact {:midje/description (format "Template draft %s -> %s" path value)}
                                (command sipoo :save-verdict-template-draft-value
                                         :organizationId sipoo-R-org-id
                                         :template-id template-id
                                         :path (map name (flatten [path]))
                                         :value value) => ok?))]
                      (fact "Disable julkipano, lainvoimainen and aloitettava"
                        (edit-template [:verdict-dates] ["anto" "muutoksenhaku" "voimassa"]))
                      (fact "Remove all the other sections except buildings and attachments (and verdict)"
                        (edit-template [:removed-sections :foremen] true)
                        (edit-template [:removed-sections :plans] true)
                        (edit-template [:removed-sections :reviews] true)
                        (edit-template [:removed-sections :conditions] true)
                        (edit-template [:removed-sections :neighbors] true)
                        (edit-template [:removed-sections :appeal] true)
                        (edit-template [:removed-sections :statements] true)
                        ;;(edit-template [:removed-sections :collateral] true)
                        (edit-template [:removed-sections :complexity] true)
                        (edit-template [:removed-sections :rights] true)
                        (edit-template [:removed-sections :purpose] true)
                        (edit-template [:removed-sections :extra-info] true)
                        (edit-template [:removed-sections :deviations] true)
                        (edit-template [:removed-sections :attachments] false))
                      (fact "Unselect autopaikat and vss-luokka"
                        (edit-template :autopaikat false)
                        (edit-template :vss-luokka false))
                      (fact "Board verdict template"
                        (edit-template :giver :lautakunta) => true)
                      (fact "Publish template"
                        (publish-verdict-template sipoo sipoo-R-org-id template-id) => ok?)))
                  (fact "Sonja sets Ronja as the handler for the application"
                    (let [{handlers :handlers} (query-application sonja app-id)]
                      (command sonja :remove-application-handler :id app-id
                               :handlerId (-> handlers first :id)) => ok?)
                    (command sonja :upsert-application-handler :id app-id
                             :userId ronja-id
                             :roleId sipoo-general-handler-id) => ok?)

                  (let [{:keys [state buildings id] {operation-id :id} :primaryOperation} (query-application sonja app-id)]
                    (fact "Application state is verdictGiven"
                      state => "verdictGiven")
                    (fact "Buildings array is created, primaryOperation gets index = 1"
                      (first buildings) => (contains {:index        "1"
                                                      :localShortId "002"
                                                      :description  "Hello world!"}))
                    (facts "building-data update post-verdict is reflected to buildings array as well"
                      (api-update-building-data-call id {:form-params  {:operationId        operation-id
                                                                        :nationalBuildingId "1234567892"
                                                                        :location           {:x 406216.0 :y 6686856.0}}
                                                         :content-type :json
                                                         :as           :json
                                                         :basic-auth   ["sipoo-r-backend" "sipoo"]}) => http200?
                      (check-post-verdict-building-data (query-application sonja app-id)
                                                        doc-id operation-id)))))
              (fact "Verdict draft to be deleted"
                (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                        :id app-id
                                                        :template-id template-id)
                      {:keys [attachment-id
                              file-id]}        (add-verdict-attachment sonja
                                                                       app-id
                                                                       verdict-id
                                                                       "Hello world!")]
                  (check-file app-id file-id true)
                  (check-draft-attachment app-id attachment-id verdict-id)
                  (fact "Deleting verdict does deletes its attachment"
                    (command sonja :delete-pate-verdict :id app-id
                             :verdict-id verdict-id) => ok?
                    (util/find-by-id attachment-id (:attachments (query-application sonja app-id)))
                    => nil
                    (check-file app-id file-id false)))))))))))

(fact "Pate HTTP configuration for Sipoo"
  (command admin :set-kuntagml-http-endpoint
           :partner "matti" :auth-type "basic" :path {:verdict "MyVerdictEndpoint"}
           :url (str (server-address) "/dev/krysp/receiver")
           :organization "753-R" :permitType "R"
           :username "kuntagml" :password "kryspi") => ok?
  (command admin :set-organization-boolean-path
           :path "krysp.R.http.enabled"
           :value true
           :organizationId "753-R") => ok?
  (fact "Configuration OK"
    (-> (query sipoo :organization-by-user :organizationId "753-R")
        :organization :krysp :R :http)
    => (contains {:enabled true
                  :url (str (server-address) "/dev/krysp/receiver")
                  :path {:verdict "MyVerdictEndpoint"}})))

(fact "New verdict"
  (let  [{:keys [app-id template-id]}                   space-saving-definitions
         {:keys [primaryOperation secondaryOperations]} (query-application sonja app-id)
         op-id                                          (-> primaryOperation :id keyword)
         op-id-pientalo                                 (-> secondaryOperations
                                                            first  :id keyword)

         {verdict-id :verdict-id}                       (command sonja :new-pate-verdict-draft
                                                                 :id app-id
                                                                 :template-id template-id)
         {:keys [verdict references]}                   (query sonja :pate-verdict
                                                               :id app-id
                                                               :verdict-id verdict-id)
         verdict-date                                   (date/timestamp "27.9.2017")
         {data :data}                                   verdict

         {open-verdict  :open
          edit-verdict  :edit
          check-changes :check-changes}                 (verdict-fn-factory app-id verdict-id)]
    data => {:language         "fi"
             :handler          "Ronja Sibbo"
             ;;:voimassa              ""
             :verdict-text     "Verdict text."
             ;;:anto                  ""
             ;;:muutoksenhaku         ""
             :foremen-included false
             :foremen          ["iv-tj"]
             :verdict-code     "ehdollinen"
             :plans-included   false
             :plans            [(find-plan-id references "Tampere")]
             :reviews-included false
             :reviews          [(find-review-id references "Helsinki")]
             :address          "Dongdaqiao Lu"
             :operation        "Description"
             ;;:bulletinOpDescription ""
             :buildings
             {op-id          {:description   "Hello world!"
                              :show-building true
                              :building-id   "1234567892"
                              :operation     "sisatila-muutos"
                              :tag           ""
                              ;;:paloluokka    ""
                              :order         "0"}
              op-id-pientalo {:description   "Hen piaoliang!"
                              :show-building true
                              :building-id   "1234567881"
                              :operation     "pientalo"
                              :tag           "Hao"
                              ;;:paloluokka    ""
                              :order         "1"}}
             ;;:attachments           []
             }
    (facts "Cannot edit verdict values not in the template"
      (let [check-fn (fn [kwp value]
                       (fact {:midje/description (str "Bad path " kwp)}
                         (edit-verdict (util/split-kw-path kwp)
                                       value)
                         => (err :error.invalid-value-path)))]

        (check-fn :julkipano (date/timestamp "29.9.2017"))
        (check-fn :buildings.vss-luokka "12")
        (check-fn :buildings.kiinteiston-autopaikat 34)
        (fact "Contact cannot be set for board verdict"
          (check-fn :contact "Authority Sonja Sibbo"))
        (fact "... but section can be edited"
          (edit-verdict :verdict-section "88") => no-errors?)))
    (facts "Muutoksenhaku calculation has changed"
      (fact "Set the verdict date"
        (edit-verdict :verdict-date (date/timestamp "8.1.2018")) => no-errors?)
      (fact "Calculate muutoksenhaku date automatically"
        (check-changes (edit-verdict :automatic-verdict-dates true)
                       [[["anto"] (date/timestamp "11.1.2018")]
                        [["muutoksenhaku"] (date/timestamp "22.1.2018")]
                        [["voimassa"] (date/timestamp "26.1.2021")]])))
    (facts "Include reviews in the verdict"
      (edit-verdict :reviews-included true) => no-errors?)
    (facts "Include plans in the verdict"
      (edit-verdict :plans-included true) => no-errors?)
    (facts "Include foremen in the verdict"
      (edit-verdict :foremen-included true) => no-errors?)
    (facts "Add attachment to verdict draft"
      (let [{:keys [attachment-id]} (add-verdict-attachment sonja
                                                            app-id
                                                            verdict-id
                                                            "Paatosote")]
        (fact "Attachment can be deleted"
          (command sonja :delete-attachment :id app-id
                   :attachmentId attachment-id)=> ok?)))
    (fact "No attachments"
      (:attachment (query-application sonja app-id))
      => empty?)
    (fact "Add required verdict date"
      (edit-verdict "verdict-date" verdict-date) => no-errors?)
    (facts "Add attachment to verdict draft again. Add regular attachment to the application, too. Add pseudo verdict attachment"
      (let [{:keys [attachment-id]}     (add-verdict-attachment sonja app-id verdict-id "Otepaatos")
            {regular-id :attachment-id} (add-attachment sonja app-id "Lupa lausua"
                                                        "ennakkoluvat_ja_lausunnot" "suunnittelutarveratkaisu")
            {pseudo-id :attachment-id}  (add-attachment sonja app-id "sotaaP"
                                                        "paatoksenteko" "paatos")]
        (check-draft-attachment app-id attachment-id verdict-id)
        (fact "Regular, pseudo and bogus-ids as application attachments"
          (edit-verdict "attachments" [regular-id "bogus-id" pseudo-id])
          => no-errors?)
        (fact "Unpublished verdict has only attachment ids"
          (-> (open-verdict) :verdict :data :attachments)
          => (just [regular-id "bogus-id" pseudo-id] :in-any-order))
        (fact "Verdict language is changed to English"
          (edit-verdict :language :en) => no-errors?)
        (fact "Board verdict cannot be published without checking the verdict-flag"
          (command sonja :publish-pate-verdict
                   :id app-id
                   :verdict-id verdict-id) => (err :error.proposal-not-finalized))
        (fact "Publish verdict"
          (edit-verdict :verdict-flag true)
          (command sonja :publish-pate-verdict
                   :id app-id
                   :verdict-id verdict-id) => ok?)
        (fact "Application verdict date is the latest"
          (check-verdict-date sonja app-id (date/timestamp "6.10.2017")))
        (facts "Published verdict"
          (let [data (-> (open-verdict) :verdict :data)]
            (fact "Section is 88"
              data => (contains {:verdict-section "88"}))
            (fact "No contact information"
              (:contact data) => empty?)
            (fact "Verdict has the correct attachment information"
              (:attachments data) => (just [{:type-group "paatoksenteko"
                                             :type-id    "paatosote"
                                             :amount     1}
                                            {:type-group "paatoksenteko"
                                             :type-id    "paatos"
                                             :amount     1}
                                            {:type-group "ennakkoluvat_ja_lausunnot"
                                             :type-id    "suunnittelutarveratkaisu"
                                             :amount     1}] :in-any-order))))
        (fact "Attachments can no longer be deleted"
          (command sonja :delete-attachment :id app-id
                   :attachmentId attachment-id)
          => fail?
          (command sonja :delete-attachment :id app-id
                   :attachmentId regular-id)
          => fail?)
        (facts "Attachments cannot be updated with new version"
          (fact {:midje/description (str "Verdict attachment: " attachment-id)}
            (new-attachment-version-fails sonja app-id attachment-id)
            => :error.attachment-is-locked
            (upload-file-and-bind sonja app-id nil
                                  :attachment-id attachment-id
                                  :fails :error.attachment-is-locked)
            (upload-file-and-bind sonja app-id nil
                                  :attachment-id attachment-id
                                  :fails :error.attachment-is-locked
                                  :draft? true))
          (fact {:midje/description (str "Regular (selected) attachment: " regular-id)}
            (new-attachment-version-fails sonja app-id regular-id)
            => :error.attachment-in-published-verdict
            (upload-file-and-bind sonja app-id nil
                                  :attachment-id regular-id
                                  :fails :error.attachment-in-published-verdict)
            (upload-file-and-bind sonja app-id nil
                                  :attachment-id regular-id
                                  :fails :error.attachment-in-published-verdict
                                  :draft? true)))

        (facts "Attachment details have changed"
          (let [details #(merge {:metadata (just {:nakyvyys "julkinen"})}
                                (when %
                                  {:target {:type %
                                            :id   verdict-id}}))
                atts    (:attachments (query-application sonja app-id))]
            (fact "Verdict draft attachment"
              (util/find-by-id attachment-id atts)
              => (contains (assoc (details "verdict")
                                  :locked true
                                  :readOnly true
                                  :id attachment-id
                                  :metadata (just {:nakyvyys    "julkinen"
                                                   :draftTarget false}))))
            (fact "Regular attachment that is referenced in the verdict"
              (util/find-by-id regular-id atts)
              => (contains (assoc (details nil) :id regular-id)))))

        (facts "KuntaGML"
          (let [xml                    (check-kuntagml (query-application sonja app-id) verdict-date)
                verdict-pdf-attachment (->> (query-application sonja app-id)
                                            :pate-verdicts
                                            (util/find-by-id verdict-id)
                                            :published
                                            :attachment-id)]
            (fact "verdict attachment has sent timestamp"
              (->> (query-application sonja app-id)
                   :attachments
                   (util/find-by-id verdict-pdf-attachment)
                   :sent) => pos?)

            (fact "Rakennekatselmus is London"
              (xml/get-text xml [:katselmuksenLaji])
              => "rakennekatselmus"
              (xml/get-text xml [:tarkastuksenTaiKatselmuksenNimi])
              => "London")
            (fact "Plan is Washington"
              (xml/get-text xml [:vaadittuErityissuunnitelma])
              => "Washington")
            (fact "Verdict attachment"
              (xml/get-text xml [:paatostieto :poytakirja :liite :kuvaus])
              => "Päätös: Permit text"
              (let [url                        (xml/get-text xml [:paatostieto :poytakirja
                                                                  :liite :linkkiliitteeseen])
                    {:keys [uri query-string]} (http-client/parse-url url)]
                url => (has-prefix (server-address))
                uri => "/rest/latest-attachment-version"
                (http-client/form-decode query-string)
                => {"attachment-id" verdict-pdf-attachment}
                (verdict-pdf-queue-test sonja {:app-id       app-id
                                               :verdict-id   verdict-id
                                               :verdict-name "Permit"
                                               :contents     "Permit text"})
                (facts "The attachment can be retrieved via REST API"
                  (fact "The url in KuntaGML"
                    (http-get url {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                   :throw-exceptions false})
                    => (contains {:length  pos?
                                  :status  200
                                  :headers (contains {"Content-Type"        "application/pdf"
                                                      "Content-Disposition" #"^filename=\"LP-.*\.pdf\""})}))
                  (fact "With download parameter"
                    (http-get (str url "&download=TrUe") {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                                          :throw-exceptions false})
                    => (contains {:length  pos?
                                  :status  200
                                  :headers (contains {"Content-Type"        "application/pdf"
                                                      "Content-Disposition" #"^attachment;filename=\"LP-.*\.pdf\""})}))
                  (fact "Unsupported parameters are ignored"
                    (http-get (str url "&ignore=me") {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                                      :throw-exceptions false})
                    => (contains {:length  pos?
                                  :status  200
                                  :headers (contains {"Content-Type"        "application/pdf"
                                                      "Content-Disposition" #"^filename=\"LP-.*\.pdf\""})}))
                  (facts "Bad parameters"
                    (http-get (str url "&download=bad") {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                                         :throw-exceptions false})
                    => (contains {:status 400
                                  :body   #"error\.input-validation-error"})
                    => (contains {:status 400
                                  :body   #"error\.input-validation-error"})
                    (http-get (str (server-address) uri "?attachment-id=bad") {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                                                                                   :throw-exceptions false})
                    => (contains {:status 400
                                  :body   #"error\.input-validation-error"})
                    (http-get (str url "&preview=bad") {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                                        :throw-exceptions false})
                    => (contains {:status 400
                                  :body   #"error\.input-validation-error"}))
                  (fact "Attachment not found"
                    (http-get (str (server-address) uri "?attachment-id=matches-schema-but-not-reality") {:basic-auth       ["sipoo-r-backend" "sipoo"]
                                                                                                                              :throw-exceptions false})
                    => (contains {:status 404}))
                  (fact "No authentication"
                    (http-get url {:throw-exceptions false})
                    => (contains {:status 401}))
                  (fact "Bad authentication"
                    (http-get url {:basic-auth       ["sipoo-r-backend" "bad password"]
                                   :throw-exceptions false})
                    => (contains {:status 401}))
                  (fact "Not authorized"
                    (http-get url {:basic-auth       ["matti-rest-api-user" "vantaa"]
                                   :throw-exceptions false})
                    => (contains {:status 404})))))))

        (fact "Editing no longer allowed"
          (edit-verdict :verdict-text "New verdict text")
          => (err :error.verdict.not-editable))
        (let [{vatt-id :id
               :as     v-attachment} (-> (query-application sonja app-id)
                                         :attachments
                                         last)]
          (fact "Verdict PDF has been generated"
            v-attachment => (contains {:source        {:type "verdicts"
                                                       :id   verdict-id}
                                       :latestVersion truthy})
            (fact "Published verdict attachment ids include all the verdict related and existing attachments."
              (query sonja :published-verdict-attachment-ids :id app-id
                     :verdict-id verdict-id)
              => (contains {:attachment-ids (just [regular-id pseudo-id attachment-id vatt-id]
                                                  :in-any-order)}))))

        (fact "Published verdict can be deleted"
          (command sonja :delete-pate-verdict :id app-id
                   :verdict-id verdict-id) => ok?)
        (fact "Verdict is gone"
          (open-verdict) => (err :error.verdict-not-found))
        (let [attachments (:attachments (query-application sonja app-id))
              selected    #{regular-id pseudo-id}]
          (fact "... verdict target attachments are gone"
            (filter (util/fn-> :target :id (= verdict-id))
                    attachments)
            => empty?)
          (fact "... but selected attachments remain."
            (->> (map :id attachments)
                 (filter selected))
            => (just [regular-id pseudo-id] :in-any-order))
          (fact "... the selected attachments are now released"
            (command sonja :delete-attachment :id app-id
                     :attachmentId regular-id) => ok?))))))

(defn verdict-source-attachments [app-id verdict-id]
  (filter (fn [{source :source}]
            (= (:id source) verdict-id))
          (:attachments (query sonja :attachments :id app-id))))

(defn publish-proposal [app-id verdict-id]
  (fact "Publish verdict proposal"
    (command sonja :publish-verdict-proposal :id app-id :verdict-id verdict-id)
    => ok?)

  (fact "There is a verdict proposal attachment"
    (proposal-pdf-queue-test sonja {:app-id     app-id
                                    :verdict-id verdict-id}))
  (let [verdict        (->> (query-application sonja app-id)
                            :pate-verdicts
                            (util/find-by-id verdict-id))
        [{att-id :id}] (verdict-source-attachments app-id verdict-id)]
    (fact "State is now proposal"
      (->> verdict
           :state
           :_value) => "proposal")

    (fact "Proposal vs. attachment"
      att-id => ss/not-blank?
      (:proposal verdict) => (just {:attachment-id att-id
                                    :tags          ss/not-blank?
                                    :proposed      pos?}))))

(defn revert-proposal [app-id verdict-id open-fn]
  (fact "Published proposal can be reverted"
    (command sonja :revert-verdict-proposal
             :id app-id
             :verdict-id verdict-id) => ok?)

  (fact "Verdict is draft"
    (let [{:keys [verdict]} (open-fn)]
      (:state verdict) => "draft"
      (:proposal verdict) => nil))

  (fact "No proposal pdf"
    (verdict-source-attachments app-id verdict-id) => empty?))

(facts "Verdict proposal"
  (let  [{:keys [app-id template-id]} space-saving-definitions]
  (fact "Board verdict template"
    (command sipoo :save-verdict-template-draft-value
             :organizationId sipoo-R-org-id
             :template-id template-id
             :path ["giver"]
             :value :lautakunta) => ok?)

  (let [{verdict-id :verdict-id} (command sonja :new-pate-verdict-draft
                                          :id app-id
                                          :template-id template-id)
        {edit-verdict :edit
         open-verdict :open}     (verdict-fn-factory app-id verdict-id)]

    (fact "Giver is the boardname in the verdict list"
      (:verdicts (query sonja :pate-verdicts :id app-id))
      => (contains [(contains {:id    verdict-id
                               :title "Luonnos"
                               :giver "Board of peers"})]))

    (fact "Verdict proposal cannot be published if not filled"
      (command sonja :publish-verdict-proposal :id app-id :verdict-id verdict-id) => (err :pate.required-fields))

    (fact "Edit verdict draft"
      (edit-verdict :verdict-date (date/timestamp "6.10.2017")) => ok?
      (edit-verdict :automatic-verdict-dates true) => ok?
      (edit-verdict :verdict-section "12") => ok?
      (edit-verdict :giver "Chairman") => ok?)

    (fact "Explict giver overrides the boardname in the verdict list"
      (:verdicts (query sonja :pate-verdicts :id app-id))
      => (contains [(contains {:id    verdict-id
                               :title "Luonnos"
                               :giver "Chairman"})]))

    (fact "Applicant cannot publish proposal"
      (command pena :publish-verdict-proposal :id app-id :verdict-id verdict-id) => (err :error.unauthorized))

    (fact "Unpublished proposal cannot be reverted"
      (command sonja :revert-verdict-proposal
               :id app-id
               :verdict-id verdict-id) => (err :error.verdict.not-proposal))

    (publish-proposal app-id verdict-id)

    (fact "Revert the proposal"
      (revert-proposal app-id verdict-id open-verdict))

    (fact "Verdict proposal can be published again"
      (publish-proposal app-id verdict-id))

    (fact "Proposal pdf cannot be deleted"
      (command sonja :delete-attachment
               :id app-id
               :attachmentId (-> (verdict-source-attachments app-id verdict-id)
                                 first :id))
      => unauthorized?)

    (fact "Revert the proposal again"
      (revert-proposal app-id verdict-id open-verdict))

    (fact "Publish proposal one last time"
      (publish-proposal app-id verdict-id))

    (fact "Proposal can be updated"
      (edit-verdict :proposal-text "Edited proposal") => ok?
      (command sonja :publish-verdict-proposal :id app-id :verdict-id verdict-id) => ok?)

    (fact "Proposal can be deleted.."
      (let [{proposal-for-delete-id :verdict-id} (command sonja :new-pate-verdict-draft
                                                          :id app-id
                                                          :template-id template-id)
            verdict-count-before                 (count (:pate-verdicts (query-application sonja app-id)))]
        (fact ".. not by applicant.."
          (command pena :delete-pate-verdict :id app-id :verdict-id proposal-for-delete-id) => (err :error.unauthorized))
        (fact " .. but by authority."
          (command sonja :delete-pate-verdict :id app-id :verdict-id proposal-for-delete-id) => ok?
          (- verdict-count-before (count (:pate-verdicts (query-application sonja app-id)))) => 1)))

    (fact "Verdict can be published from proposal"
      (edit-verdict :verdict-flag true)
      (command sonja :publish-pate-verdict :id app-id :verdict-id verdict-id) => ok?)

    (fact "State is now published"
      (->> (query-application sonja app-id)
           :pate-verdicts
           (util/find-by-id verdict-id)
           :state
           :_value) => "published")

    (fact "And there is a verdict pdf"
      (verdict-pdf-queue-test sonja {:app-id     app-id
                                     :verdict-id verdict-id}))

    (fact "Attachment is not missing after the generation"
      (let [{pub-att-id :attachment-id} (:verdict (query pena :published-pate-verdict
                                                         :id app-id
                                                         :verdict-id verdict-id))]
        (query sonja :published-verdict-attachment-ids
               :id app-id
               :verdict-id verdict-id)
        => (contains {:attachment-ids      (just [string? pub-att-id])})))

    (fact "Proposal can no longer be neither published nor reverted"
      (command sonja :publish-verdict-proposal :id app-id :verdict-id verdict-id) => fail?
      (command sonja :revert-verdict-proposal :id app-id :verdict-id verdict-id) => fail?))))
