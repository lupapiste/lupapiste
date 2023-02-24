(ns lupapalvelu.components.ui-components
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.attachment :refer [attachment-scales attachment-sizes]]
            [lupapalvelu.attachment.conversion :as conversion]
            [lupapalvelu.attachment.metadata :as attachment-meta]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.calendar :as cal]
            [lupapalvelu.company :as company]
            [lupapalvelu.components.core :as c]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.json :as json]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.states :as states]
            [lupapalvelu.xml.validator :as validator]
            [me.raynes.fs :as fs]
            [sade.env :as env]
            [sade.util :as util]
            [swiss.arrows :refer [-<>>]])
  (:import [java.io File]))

(def themes #{"louhi", "facta"})

(def debugjs {:depends [:jquery]
              :js ["debug.js"]
              :name "common"})

(def mockjax {:depends [:jquery]
              :js ["jquery.mockjax.js"]
              :name "jquery"})

(defn- ^StringBuilder breaked-json-map [m, ^StringBuilder sb, ^Long break-at]
  (.append sb "{")
  (loop [kvs m, line-length (.length sb)]
    (if (seq kvs)
      (let [[k v] (first kvs)
            map-entry (str (json/encode k) ":" (json/encode v))
            new-length (+ line-length (.length map-entry) 1) ; +1 for the comma or ending brace
            tail  (rest kvs)]
        (when (> new-length break-at) (.append sb \newline))
        (.append sb map-entry)
        (when (seq tail) (.append sb \,))
        (recur tail (if (>= new-length break-at) (.length map-entry) new-length)))
      (.append sb "}"))))

(declare cljs-app-url)

(defn- conf []
  (let [js-conf {:maps                  (env/value :maps)
                 :frontpage             (env/value :frontpage)
                 :searchTextMaxLength   (env/value :search-text-max-length)
                 :fileExtensions        mime/allowed-extensions
                 :passwordMinLength     (env/value :password :minlength)
                 :mode                  env/mode
                 :build                 env/build-number
                 :cookie                (env/value :cookie)
                 :wannaJoinUrl          (env/value :oir :wanna-join-url)
                 :rumURL                cljs-app-url
                 :userAttachmentTypes   (map #(str "osapuolet." (name %)) att-type/osapuolet)
                 :attachmentScales      attachment-scales
                 :attachmentSizes       attachment-sizes
                 :accountTypes          company/account-types
                 ;; Hardcoded campaign code for April 2017
                 ;; :campaignCode          :huhtikuu2017
                 :eInvoiceOperators     schemas/e-invoice-operators
                 :postVerdictStates     states/post-verdict-states
                 :loggedInUploadMaxSize (env/value :file-upload :max-size :logged-in)
                 :anonymousUploadMaxSize (env/value :file-upload :max-size :anonymous)
                 :writerRoles           domain/write-roles
                 :stampableMimes        ["application/pdf" "image/tiff" "image/png" "image/jpeg"]
                 :foremanRoles          (:body (first lupapalvelu.document.schemas/kuntaroolikoodi-tyonjohtaja))
                 :foremanReadonlyFields ["luvanNumero", "katuosoite", "rakennustoimenpide", "kokonaisala"]
                 :asianhallintaVersions (util/convert-values ; asianhallinta versions have "ah-" prefix
                                          validator/supported-asianhallinta-versions-by-permit-type
                                          (partial map #(sade.strings/suffix % "ah-")))
                 :degrees               (map :name (:body schemas/koulutusvalinta))
                 :fiseKelpoisuusValues  (map :name schemas/fise-kelpoisuus-lajit)
                 :bulletinStates        bulletins/bulletin-state-seq
                 :attachmentVisibilities attachment-meta/visibilities
                 :features              (into {} (filter second (env/features)))
                 :inputMaxLength        model/default-max-len
                 :mimeTypePattern       (.toString mime/mime-type-pattern)
                 :supportedLangs        i18n/supported-langs
                 :urgencyStates         ["normal" "urgent" "pending"]
                 :calendars             (cal/ui-params)
                 :identMethods          (into {}
                                          (remove nil?
                                            [(when (env/feature? :suomifi-ident)
                                               {:loginUrl  (str (env/value :host) "/api/saml/login")
                                                :logoutUrl (str (env/value :host) "/Shibboleth.sso/Logout")})
                                             (when (env/feature? :dummy-ident)
                                               {:loginUrl  (str (env/value :host) "/dev/saml-login")
                                                :logoutUrl (str (env/value :host) "/dev/saml-logout")})]))
                 :convertableTypes      conversion/all-convertable-mime-types
                 :footerLinkPrefix      (env/value :host)}]
    (str "var LUPAPISTE = LUPAPISTE || {};LUPAPISTE.config = " (json/encode js-conf) ";")))

(defn- loc->js []
  (-> (breaked-json-map (i18n/get-terms i18n/*lang*) (StringBuilder. ";loc.setTerms(") 32000)
      (.append ");")
      (.toString)))

(defn- schema-versions-by-permit-type []
  (str ";LUPAPISTE.config.kryspVersions = " (json/encode validator/supported-krysp-versions-by-permit-type) ";"))

(defn- read-component-list-from-fs [path pattern]
  (let [files (fs/find-files path (re-pattern pattern))
        mapped-files (map #(-<>> ^File %
                                 .getPath
                                 (s/replace <> env/file-separator "/")
                                 (re-matches (re-pattern (str "^.*/" path "/(.*)")))
                                 last)
                          files)]
    mapped-files))

(defn- in-jar?
  [jar]
  (re-find (re-pattern ".jar$") jar))

(defn- read-component-list-from-jar [jar path pattern]
  (let [files (util/list-jar jar path)
        filtered-files (filter #(re-find (re-pattern pattern) %) files)]
    filtered-files))

(defn- get-ui-components [component type]
  (let [jar (util/this-jar lupapalvelu.main)
        pattern (case type
                  :models ".*-model.js$"
                  :templates ".*-template.html$")]
    (if (in-jar? jar)
      (read-component-list-from-jar jar (str "private/" (name component)) pattern)
      (read-component-list-from-fs (str "resources/private/" (name component)) pattern))))


(def ui-components
  {;; 3rd party libs
   :jquery            {:js ["jquery.ba-hashchange.js" "jquery.metadata-2.1.js" "js.cookie.js" "jquery.caret.js"]}
   :jquery-upload     {:js ["jquery.iframe-transport.js" "jquery.fileupload.js"]}
   :knockout          {:js ["knockout-3.5.1.min.js" "knockout.mapping-2.4.1.js" "knockout.validation.min.js" "knockout-repeat-2.0.0.js" "knockout.dragdrop.js""register-lupapiste-components.js"]}
   :lo-dash           {:js ["lodash.min.js"]}
   :underscore        {:depends [:lo-dash]
                       :js      ["underscore.string.min.js" "underscore.string.init.js"]}
   :sprintf           {:js ["sprintf.min.js"]}
   :moment            {:js ["moment.min.js"]}
   :open-layers       {:js ["openlayers-2.13.1.min.lupapiste_1.js" "LupapisteEditingToolbar-2.13.1.js"]}
                                        ;:open-layers    {:js ["openlayers-2.13_20140619.min.lupapiste.js"]}
                                        ;:open-layers    {:js ["OpenLayers.debug.js" ]}
   :ol                {:js  ["openlayers-3.8.2.min.js" "ol3-popup.js"]
                       :css ["openlayers-3.8.2.css" "ol3-popup.css"]}
   :proj4             {:js ["proj4-2.3.3.min.js"]}
   :stickyfill        {:js ["stickyfill.min.js"]}
   :waypoints         {:js ["jquery.waypoints.min.js"]}

   ;; Init can also be used as a standalone lib, see web.clj
   :init {:depends [:underscore :sprintf]
          :js      [conf "hub.js" "notify.js" "ajax.js" "log.js"]}

   ;; Common components

   :debug (if (env/dev-mode?) debugjs {})

   :mockjax (if (env/dev-mode?) mockjax {})

   :i18n {:depends [:jquery :underscore]
          :js      ["loc.js" loc->js]}

   :selectm {:js ["selectm.js"]}

   :selectm-html {:html ["selectm.html"]}

   :expanded-content {:depends [:jquery]
                      :js      ["expanded-content.js"]}

   :common
   {:depends [:init :jquery :jquery-upload :knockout :underscore :sprintf :moment :i18n :selectm
              :expanded-content :mockjax :open-layers :stickyfill :waypoints]
            :js      ["register-components.js" "util.js" "event.js" "pageutil.js" "app.js" "nav.js" "window.js"
                      "ko.init.js" "dialog.js" "datepicker.js" "requestcontext.js" "currentUser.js" "perfmon.js" "features.js"
                      "statuses.js" "authorization.js" "vetuma.js" "location-model-base.js"]}

   :cljs-component {:js ["cljs-component.js" "cljs-loader.js"]}

   :common-html {:depends [:selectm-html :cljs-component]
                 :css     ["jquery-ui.css"]
                 :html    ["404.html"]}

   ;; Components to be included in a SPA

   :services {:js ["area-filter-service.js"
                   "comment-service.js"
                   "tag-filter-service.js"
                   "operation-filter-service.js"
                   "organization-filter-service.js"
                   "organization-tags-service.js"
                   "company-tags-service.js"
                   "handler-filter-service.js"
                   "publish-bulletin-service.js"
                   "application-filters-service.js"
                   "document-data-service.js"
                   "fileupload-service.js"
                   "side-panel-service.js"
                   "accordion-service.js"
                   "scroll-service.js"
                   "ram-service.js"
                   "calendar-service.js"
                   "attachments-service.js"
                   "suti-service.js"
                   "info-service.js"
                   "context-service.js"
                   "building-service.js"
                   "assignment-service.js"
                   "assignment-recipient-filter-service.js"
                   "assignment-target-filter-service.js"
                   "event-filter-service.js"
                   "inspection-summary-service.js"
                   "handler-service.js"
                   "card-service.js"
                   "campaign-service.js"
                   "company-registration-service.js"
                   "triggers-target-service.js"
                   "suomifi-service.js"
                   "navi-sidebar-service.js"
                   "batch-service.js"
                   "simple-filter-service.js"
                   "organizations-users-service.js"
                   "organizations-handling-times-service.js"
                   "notice-forms-service.js"
                   "task-service.js"
                   "schema-flags-service.js"
                   "summary-service.js"]}

   :global-models {:depends [:services]
                   :js      ["root-model.js" "application-model.js" "register-models.js" "register-services.js"]}

   :screenmessages {:js   ["screenmessage.js"]
                    :html ["screenmessage.html"]}

   :map {:depends [:common-html]
         :js      [ "gis.js" "locationsearch.js"]}

   :mypage {:depends [:common-html]
            :js      [ "mypage.js"]
            :html    ["mypage.html"]}

   :header {:html ["header.html"], :js ["header.js"]}

   :footer {:html ["footer.html"]}

   :modal-datepicker {:depends [:common-html]
                      :html    ["modal-datepicker.html"]
                      :js      ["modal-datepicker.js"]}

   :authenticated {:depends [:screenmessages]}

   :invites {:depends [:common-html]
             :js      ["invites-model.js" "invites.js"]}

   :attachment-utils {:js ["attachment-utils.js"]}

   :repository {:depends [:common-html :attachment-utils]
                :js      ["repository.js"]}

   :accordion {:js ["accordion.js"]}

   :signing {:depends [:common-html]
             :html    ["signing-dialogs.html"]
             :js      ["signing-model.js"]}

   :metadata-editor {:depends [:common-html]
                     :html    ["metadata-editor.html"]
                     :js      ["metadata-editor.js"]}

   :stamp {:depends [:common-html]
           :html    ["stamp-template.html"]
           :js      ["stamp-model.js" "stamp.js"]}

   :external-api {:js (remove nil?
                              (cons (when (env/dev-mode?)
                                      "dummy-api-client.js")
                                    ["external-api-service.js" "external-api-tools.js"]))}

   :verdict-attachment-prints {:depends [:common-html :ui-components]
                               :html    ["verdict-attachment-prints-order-template.html"
                                         "verdict-attachment-prints-order-history-template.html"
                                         "verdict-attachment-prints-multiselect.html"]
                               :js      ["verdict-attachment-prints-order-model.js"
                                         "verdict-attachment-prints-order-history-model.js"
                                         "verdict-attachment-prints-multiselect-model.js"]}


   :attachment {:depends [:services :common-html :repository :signing]
                :js      ["attachment-multi-select.js"
                          "attachment-model.js"
                          "attachment.js"
                          "send-attachments.js"]
                :html    ["attachment.html"
                          "send-attachments.html"]}

   :task {:depends [:common-html :attachment]
          :js      ["task.js"]
          :html    ["task.html"]}

   :create-task {:js   ["create-task.js"]
                 :html ["create-task.html"]}

   :calendar-view {:depends [:common-html]
                   :js      ["calendar-view.js" "reservation-slot-edit-bubble-model.js"
                             "reservation-slot-create-bubble-model.js" "calendar-view-model.js"
                             "authority-calendar-model.js" "applicant-calendar-model.js"
                             "reservation-slot-reserve-bubble-model.js" "reserved-slot-bubble-model.js" "calendar-notification-list.js"
                             "book-appointment-filter.js" "base-calendar-model.js"]
                   :html    ["reserved-slot-bubble-template.html" "reservation-slot-edit-bubble-template.html"
                             "reservation-slot-create-bubble-template.html" "calendar-view-template.html"
                             "authority-calendar-template.html" "applicant-calendar-template.html"
                             "calendar-notification-list-template.html"
                             "reservation-slot-reserve-bubble-template.html"
                             "book-appointment-filter-template.html"]}


   :printing-order {:depends [:common-html]
                    :js      ["printing-order.js"]
                    :html    ["printing-order-template.html"]}

   :application {:depends [:common-html :global-models :repository :task :create-task :modal-datepicker
                           :signing :invites :verdict-attachment-prints :calendar-view :printing-order]
                 :js      ["map-model.js" "change-location.js" "verdicts-model.js"
                           "add-operation.js" "foreman-model.js"
                           "add-party.js" "archival-summary.js" "case-file.js"
                           "create-digging-permit.js"
                           "parties-model.js"
                           "application.js"
                           "backend-id-manager.js"
                           "replace-operation.js"]
                 :html    ["application.html" "inforequest.html" "add-operation.html" "change-location.html"
                           "foreman-template.html" "archival-summary-template.html"
                           "required-fields-summary-tab-template.html" "parties-tab-template.html"
                           "case-file-template.html" "create-digging-permit.html"
                           "backend-id-manager-template.html"
                           "replace-operation.html"]}

   :applications {:depends [:common-html :repository :invites :global-models]
                  :html    ["applications-list.html"]
                  :js      ["applications-list.js"]}

   :statement {:depends [:common-html :repository]
               :js      ["statement-service.js" "statement.js"]
               :html    ["statement.html"]}

   :neighbors {:depends [:common-html :repository]
               :js      ["neighbors.js"]
               :html    ["neighbors.html"]}

   :register {:depends [:common-html]
              :js      ["registration-models.js" "register.js"]
              :html    ["register.html" "register2.html" "register3.html" "eidas.html"]}

   :register-company {:depends [:common-html]
                      :js      ["company-registration.js"]
                      :html    ["register-company-success.html"
                                "register-company-fail.html"
                                "register-company-account-type.html"
                                "register-company-existing-user-success.html"]}

   :link-account {:depends [:register]
                  :js      ["link-account.js"]
                  :html    ["link-account-1.html" "link-account-2.html" "link-account-3.html"]}

   :docgen {:depends [:accordion :common-html]
            :js      ["docmodel.js" "docgen.js" "docutils.js" "document-approval-model.js"]}

   :create {:depends [:common-html :map]
            :js      ["locationmodel.js" "municipalities.js" "create.js"]
            :html    ["map-popup.html" "create.html"]}

   :copy {:depends [:common-html :map :create]
          :html    ["copy.html"]
          :js      ["copy.js"]}

   :property-formation-app {:depends [:common-html]
                            :html    ["property-formation-app.html"]
                            :js      ["property-formation-app.js"]}

   :digitizer {:depends [:common-html :map :create]
               :html    ["digitizer.html"]
               :js      ["digitizer.js"]}

   :login {:depends [:common-html]
           :js      ["login.js"]}

   :users {:js   ["users.js"]
           :html ["users.html"]}

   :company {:js   ["company.js"]
             :html ["company.html"]}

   :admins {:depends [:users]}

   :password-reset {:depends [:common-html]
                    :js      ["password-reset.js"]
                    :html    ["password-reset.html"]}

   :change-email {:depends [:common-html]
                  :js      ["change-email.js"]
                  :html    ["init-email-change.html" "change-email.html" "change-email-simple.html"]}

   :integration-message-monitor {:js   [ "integration-message-monitor-model.js"]
                                 :html ["integration-message-monitor-template.html"]}

   :ui-components {:js      (distinct (conj (get-ui-components :ui-components :models) "docgen/ui-components.js"))
                   :html    (get-ui-components :ui-components :templates)}

   :authority-admin-components {:depends [:common-html]
                                :js      (distinct (conj (get-ui-components :authority-admin-components :models) "register-authority-admin-components.js"))
                                :html    (get-ui-components :authority-admin-components :templates)}

   :pate {:depends [:common-html]
          :html    ["pate-verdict.html"]
          :js      ["pate-verdict.js"]}

   :invoices {:depends [:common-html]
              :html    ["invoices.html"]
              :js      ["invoices.js"]}

   :filebank {:depends [:common-html]
              :html    ["filebank.html"]
              :js      ["filebank.js"]}

   ;; Single Page Apps and standalone components:
   ;; (compare to auth-methods in web.clj)

   :hashbang {:depends [:common-html :ui-components :header :footer]
              :js      ["hashbang.js"]
              :html    ["index.html"]}

   :new-appointment {:depends [:calendar-view]
                     :js      ["new-appointment.js"]
                     :html    ["new-appointment.html"]}

   :applicant-app {:depends []
                   :js      ["applicant.js"]}

   :applicant {:depends [:applicant-app
                         :common-html :authenticated :map :applications :application
                         :statement :docgen :create :copy :mypage :header :debug
                         :company :register-company :footer :new-appointment :ui-components
                         :pate :property-formation-app]}

   :mycalendar {:depends [:calendar-view]
                :js      ["mycalendar.js"]
                :html    ["mycalendar.html"]}

   :navi-toolbar {:js ["navi-bindings.js"] :html ["template.html"]}

   :authority-app {:depends [] :js ["authority.js"]}
   :authority     {:depends [:pate :authority-app :common-html :external-api :authenticated :map :applications
                             :integration-message-monitor :application
                             :statement :neighbors :docgen :create :copy :digitizer :mypage :header :debug
                             :company :stamp :metadata-editor :footer :mycalendar :ui-components
                             :property-formation-app]}

   :oir-app {:depends [] :js ["oir.js"]}
   :oir     {:depends [:oir-app :common-html :authenticated :map :application :attachment
                       :docgen :debug :header :footer :ui-components]
             :css     ["oir.css"]}

   :authority-admin-app {:depends []
                         :js      ["authority-admin-app.js"]}
   :authority-admin     {:depends [:authority-admin-app :global-models :common-html :authenticated :admins
                                   :accordion :mypage :calendar-view :header :debug :proj4 :ol :footer
                                   :ui-components :authority-admin-components :navi-toolbar]
                         :js [schema-versions-by-permit-type "organization-model.js" "wfsmodel.js" "organization-user.js"
                              "organization-reports.js" "automatic-assignments.js"
                              "calendars-model.js" "organization-reservation-types-model.js"
                              "organization-reservation-properties-model.js"
                              "municipality-maps-service.js" "authority-admin.js"]
                         :html ["organization-users.html" "applications-settings.html" "selected-attachments.html" "selected-operations.html" "organization-areas.html" "organization-backends.html"
                                "organization-reports.html" "organization-calendars.html" "calendar-admin.html" "assignments.html" "stamp-editor.html" "automatic-emails.html" "price-catalogue.html"
                                "pate-verdict-templates.html" "archiving.html" "organization-bulletins.html"
                                "organization-store.html" "organization-terminal.html" "edit-authority.html" "ad-login-settings.html"
                                "reviews.html"]}

   :admin-app {:depends []
               :js      ["admin.js"]}
   :admin     {:depends [:admin-app :global-models :common-html :authenticated :admins :accordion :map :mypage :header
                         :debug :footer :ui-components :authority-admin-components :navi-toolbar]
               :js      ["admin-users.js" "organization.js" "organizations.js" "companies.js"
                         "features.js" "actions.js" "sso-keys.js" "screenmessages-list.js" "notifications.js"
                         "create-scope-model.js" "logs.js" "reports.js" "campaigns.js"]
               :html    ["admin.html" "organization.html"
                         "admin-users.html" "organizations.html" "companies.html"
                         "features.html" "actions.html" "sso-keys.html"
                         "screenmessages-list.html" "notifications.html"
                         "create-scope-template.html" "logs.html" "reports.html"
                         "campaigns.html"]}

   :wordpress {:depends [:login :password-reset]}

   :welcome-app {:depends []
                 :js      ["welcome.js"]}

   :welcome {:depends [:welcome-app  :global-models :ui-components :login :register :register-company
                       :link-account :debug :header :screenmessages :password-reset :change-email :footer]
             :js      ["company-user.js"]
             :html    ["index.html" "login.html" "company-user.html"]}

   :neighbor-app {:depends []
                  :js      ["neighbor-app.js"]}
   :neighbor     {:depends [:neighbor-app :common-html :global-models :map :debug :docgen :debug :header :screenmessages :footer :ui-components]
                  :html    ["neighbor-show.html"]
                  :js      ["neighbor-show.js"]}

   :bulletins-common {:depends []
                      :js      ["header.js" "vetuma-service.js"
                                "application-bulletin/application-bulletin-model.js"]
                      :html    ["header.html" "footer.html"
                                "application-bulletin/application-bulletin-template.html"]}

   :bulletins {:depends [:bulletins-common :ui-components :map :docgen :services]
               :html    ["bulletins.html" "bulletins-template.html"
                         "application-ymp-bulletin/application-ymp-bulletin-template.html"
                         "application-ymp-bulletin/bulletin-comment/bulletin-comment-template.html"
                         "application-ymp-bulletin/tabs/attachments/bulletin-attachments-tab-template.html"
                         "application-ymp-bulletin/tabs/attachments/bulletin-attachments-table-template.html"
                         "application-ymp-bulletin/bulletin-comment/bulletin-comment-template.html"
                         "application-ymp-bulletin/tabs/info/bulletin-info-tab-template.html"
                         "application-ymp-bulletin/tabs/verdicts/verdicts-template.html"
                         "application-ymp-bulletin/tabs/verdicts/bulletin-verdicts-tab-template.html"
                         "application-ymp-bulletin/tabs/instructions/bulletin-instructions-tab-template.html"
                         "application-ymp-bulletin/bulletin-comment/bulletin-comment-box/bulletin-comment-box-template.html"
                         "application-bulletins/application-bulletins-template.html"
                         "application-bulletins/application-bulletins-list/application-bulletins-list-template.html"
                         "application-bulletins/load-more-application-bulletins/load-more-application-bulletins-template.html"
                         "application-bulletins/bulletins-search/bulletins-search-template.html"
                         "application-bulletins/bulletins-search/autocomplete/autocomplete-municipalities-template.html"
                         "application-bulletins/bulletins-search/autocomplete/autocomplete-states-template.html"]
               :js      ["bulletins.js" "bulletins-model.js"
                         "application-bulletins-service.js"
                         "application-ymp-bulletin/application-ymp-bulletin-model.js"
                         "application-ymp-bulletin/bulletin-comment/bulletin-comment-model.js"
                         "application-ymp-bulletin/bulletin-comment/bulletin-comment-box/bulletin-comment-box-model.js"
                         "application-ymp-bulletin/tabs/attachments/bulletin-attachments-tab-model.js"
                         "application-ymp-bulletin/tabs/verdicts/bulletin-verdicts-tab-model.js"
                         "application-ymp-bulletin/tabs/instructions/bulletin-instructions-tab-model.js"
                         "application-bulletins/application-bulletins-model.js"
                         "application-bulletins/application-bulletins-list/application-bulletins-list-model.js"
                         "application-bulletins/load-more-application-bulletins/load-more-application-bulletins-model.js"
                         "application-bulletins/bulletins-search/bulletins-search-model.js"
                         "application-bulletins/bulletins-search/autocomplete/autocomplete-municipalities-model.js"
                         "application-bulletins/bulletins-search/autocomplete/autocomplete-states-model.js"]}

   :financial-authority-app {:depends []
                             :js      ["financial-authority-app.js"]}
   :financial-authority     {:depends [:financial-authority-app :common-html :authenticated :applications :application :mypage
                                       :global-models :map :debug :docgen :debug :header :screenmessages :footer :ui-components :statement]}

   :local-bulletins-app {:depends []
                         :html    ["local-bulletins.html"]
                         :js      ["local-bulletins-app.js"]}

   :local-bulletins {:depends [:bulletins-common :local-bulletins-app :ui-components :map :docgen :services]
                     :html    ["local-bulletins-template.html"]
                     :js      ["local-bulletins-model.js"]}
   })

; Make sure all dependencies are resolvable:
(doseq [[component {dependencies :depends}] ui-components
        dependency dependencies]
  (if-not (contains? ui-components dependency)
    (throw (Exception. (format "Component '%s' has dependency to missing component '%s'" component dependency)))))

; Make sure that all resources are available:
(doseq [c (keys ui-components)
        r (mapcat #(c/component-resources ui-components % c) [:js :html :css :scss])]
  (when-not (or (fn? r) (io/resource (c/path r)))
    (throw (Exception. (str "Resource missing: " r)))))

(def cljs-app-url "/lp-static/js/cljs-main.js")
; TODO: replace with bundle hash to support better caching compared to env build number
(def map-component-url "/lp-static/js/map.js")
