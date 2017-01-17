(ns lupapalvelu.components.ui-components
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [swiss.arrows :refer [-<>>]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [sade.env :as env]
            [sade.util :as util]
            [cheshire.core :as json]
            [lupapalvelu.action :as action]
            [lupapalvelu.application-bulletins :as bulletins]
            [lupapalvelu.attachment :refer [attachment-scales, attachment-sizes]]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.metadata :as attachment-meta]
            [lupapalvelu.calendar :as cal]
            [lupapalvelu.company :as company]
            [lupapalvelu.components.core :as c]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.stamper :refer [file-types]]
            [lupapalvelu.states :as states]
            [lupapalvelu.xml.validator :as validator]
            [lupapalvelu.attachment.conversion :as conversion]))

(def themes #{"louhi", "facta"})

(def debugjs {:depends [:jquery]
              :js ["debug.js"]
              :name "common"})

(def mockjax {:depends [:jquery]
              :js ["jquery.mockjax.js"]
              :name "jquery"})

(defn- breaked-json-map [m, ^StringBuilder sb, ^Long break-at]
  (.append sb "{")
  (loop [kvs m, line-length (.length sb)]
    (if (seq kvs)
      (let [[k v] (first kvs)
            map-entry (str (json/generate-string k) ":" (json/generate-string v))
            new-length (+ line-length (.length map-entry) 1) ; +1 for the comma or ending brace
            tail  (rest kvs)]
        (when (> new-length break-at) (.append sb \newline))
        (.append sb map-entry)
        (when (seq tail) (.append sb \,))
        (recur tail (if (>= new-length break-at) (.length map-entry) new-length)))
      (.append sb "}"))))

(defn- conf []
  (let [js-conf {:maps                  (env/value :maps)
                 :analytics             (env/value :analytics)
                 :gtm                   (env/value :gtm)
                 :facebook              (env/value :facebook)
                 :frontpage             (env/value :frontpage)
                 :searchTextMaxLength   (env/value :search-text-max-length)
                 :fileExtensions        mime/allowed-extensions
                 :passwordMinLength     (env/value :password :minlength)
                 :mode                  env/mode
                 :build                 (:build-number env/buildinfo)
                 :cookie                (env/value :cookie)
                 :wannaJoinUrl          (env/value :oir :wanna-join-url)
                 :userAttachmentTypes   (map #(str "osapuolet." (name %)) att-type/osapuolet)
                 :attachmentScales      attachment-scales
                 :attachmentSizes       attachment-sizes
                 :accountTypes          company/account-types
                 :eInvoiceOperators     schemas/e-invoice-operators
                 :postVerdictStates     states/post-verdict-states
                 :writerRoles           domain/owner-or-write-roles
                 :stampableMimes        (filter identity (map mime/mime-types file-types))
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
                                               {:logoutUrl (str (env/value :host) "/Shibboleth.sso/Logout")})
                                             (when (env/feature? :dummy-ident)
                                               {:logoutUrl (str (env/value :host) "/dev/saml-logout")})]))
                 :convertableTypes      (conj conversion/libre-conversion-file-types :image/jpeg)}]
    (str "var LUPAPISTE = LUPAPISTE || {};LUPAPISTE.config = " (json/generate-string js-conf) ";")))

(defn- loc->js []
  (-> (breaked-json-map (i18n/get-terms i18n/*lang*) (StringBuilder. ";loc.setTerms(") 32000)
    (.append ");")
    (.toString)))

(defn- schema-versions-by-permit-type []
  (str ";LUPAPISTE.config.kryspVersions = " (json/generate-string validator/supported-krysp-versions-by-permit-type) ";"))



(defn- read-component-list-from-fs [path pattern]
  (let [files (fs/find-files path (re-pattern pattern))
        mapped-files (map #(-<>> % .getPath (s/replace <> env/file-separator "/")  (re-matches (re-pattern (str "^.*/" path "/(.*)"))) last) files)]
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
   :cdn-fallback   {:js ["jquery-1.11.3.min.js" "jquery-ui-1.10.2.min.js" "jquery.dataTables.min.js"]}
   :jquery         {:js ["jquery.ba-hashchange.js" "jquery.metadata-2.1.js" "jquery.cookie.js" "jquery.caret.js"]}
   :jquery-upload  {:js ["jquery.ui.widget.js" "jquery.iframe-transport.js" "jquery.fileupload.js"]}
   :knockout       {:js ["knockout-3.4.1.min.js" "knockout.mapping-2.4.1.js" "knockout.validation.min.js" "knockout-repeat-2.0.0.js" "knockout.dragdrop.js""register-lupapiste-components.js"]}
   :lo-dash        {:js ["lodash.min.js"]}
   :underscore     {:depends [:lo-dash]
                    :js ["underscore.string.min.js" "underscore.string.init.js"]}
   :sprintf        {:js ["sprintf.min.js"]}
   :moment         {:js ["moment.min.js" "moment-timezone-with-data-2010-2020.min.js"]}
   :open-layers    {:js ["openlayers-2.13.1.min.lupapiste_1.js" "LupapisteEditingToolbar-2.13.1.js"]}
   ;:open-layers    {:js ["openlayers-2.13_20140619.min.lupapiste.js"]}
   ;:open-layers    {:js ["OpenLayers.debug.js" ]}
   :ol             {:js ["openlayers-3.8.2.min.js" "ol3-popup.js"]
                    :css ["openlayers-3.8.2.css" "ol3-popup.css"]}
   :proj4          {:js ["proj4-2.3.3.min.js"]}
   :stickyfill     {:js ["stickyfill.min.js"]}
   :waypoints      {:js ["jquery.waypoints.min.js"]}

   ;; Init can also be used as a standalone lib, see web.clj
   :init         {:depends [:underscore :sprintf]
                  :js [conf "hub.js" "notify.js" "ajax.js" "log.js"]}

   ;; Common components

   :debug        (if (env/dev-mode?) debugjs {})

   :mockjax      (if (env/dev-mode?) mockjax {})

   :i18n         {:depends [:jquery :underscore]
                  :js ["loc.js" loc->js]}

   :selectm      {:js ["selectm.js"]}

   :selectm-html {:html ["selectm.html"]}

   :expanded-content  {:depends [:jquery]
                       :js ["expanded-content.js"]}

   :common       {:depends [:init :jquery :jquery-upload :knockout :underscore :sprintf :moment :i18n :selectm
                            :expanded-content :mockjax :open-layers :stickyfill :waypoints]
                  :js ["register-components.js" "util.js" "event.js" "pageutil.js" "app.js" "nav.js" "window.js"
                       "ko.init.js" "dialog.js" "datepicker.js" "requestcontext.js" "currentUser.js" "perfmon.js" "features.js"
                       "statuses.js" "authorization.js" "vetuma.js" "location-model-base.js"]}

   :common-html  {:depends [:selectm-html]
                  :css ["jquery-ui.css"]
                  :html ["404.html"]}

   ;; Components to be included in a SPA

   :analytics    {:js ["analytics.js"]}

   :services {:js ["area-filter-service.js"
                   "comment-service.js"
                   "tag-filter-service.js"
                   "operation-filter-service.js"
                   "organization-filter-service.js"
                   "organization-tags-service.js"
                   "handler-filter-service.js"
                   "publish-bulletin-service.js"
                   "application-filters-service.js"
                   "document-data-service.js"
                   "fileupload-service.js"
                   "side-panel-service.js"
                   "accordion-service.js"
                   "verdict-appeal-service.js"
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
                   "event-filter-service.js"]}

   :global-models {:depends [:services]
                   :js ["root-model.js" "application-model.js" "register-models.js" "register-services.js"]}

   :screenmessages  {:js   ["screenmessage.js"]
                     :html ["screenmessage.html"]}

   :map          {:depends [:common-html]
                  :js [ "gis.js" "locationsearch.js"]}

   :mypage       {:depends [:common-html]
                  :js [ "mypage.js"]
                  :html ["mypage.html"]}

   :header     {:html ["header.html"], :js ["header.js"]}

   :footer     {:html ["footer.html"]}

   :modal-datepicker {:depends [:common-html]
                      :html ["modal-datepicker.html"]
                      :js   ["modal-datepicker.js"]}

   :authenticated {:depends [:screenmessages :analytics]}

   :invites      {:depends [:common-html]
                  :js ["invites-model.js" "invites.js"]}

   :attachment-utils   {:js ["attachment-utils.js"]}

   :repository   {:depends [:common-html :attachment-utils]
                  :js ["repository.js"]}

   :tree         {:js ["tree.js"]
                  :html ["tree.html"]}

   :accordion    {:js ["accordion.js"]}

   :signing      {:depends [:common-html]
                  :html ["signing-dialogs.html"]
                  :js ["signing-model.js" "verdict-signing-model.js"]}

   :metadata-editor {:depends [:common-html]
                     :html ["metadata-editor.html"]
                     :js ["metadata-editor.js"]}

   :stamp        {:depends [:common-html]
                  :html ["stamp-template.html"]
                  :js ["stamp-model.js" "stamp.js"]}

   :external-api {:js (remove nil?
                              (cons (when (env/dev-mode?)
                                      "dummy-api-client.js")
                                    ["external-api-service.js" "external-api-tools.js"]))}

   :verdict-attachment-prints {:depends [:common-html :ui-components]
                               :html ["verdict-attachment-prints-order-template.html"
                                      "verdict-attachment-prints-order-history-template.html"
                                      "verdict-attachment-prints-multiselect.html"]
                               :js ["verdict-attachment-prints-order-model.js"
                                    "verdict-attachment-prints-order-history-model.js"
                                    "verdict-attachment-prints-multiselect-model.js"]}


   :attachment   {:depends [:services :common-html :repository :signing]
                  :js ["attachment-multi-select.js"
                       "attachment-model.js"
                       "attachment.js"
                       "move-attachment-to-backing-system.js"
                       "move-attachment-to-case-management.js"]
                  :html ["attachment.html"
                         "upload.html"
                         "move-attachment-to-backing-system.html"
                         "move-attachment-to-case-management.html"]}

   :task         {:depends [:common-html :attachment]
                  :js ["task.js"]
                  :html ["task.html"]}

   :create-task  {:js ["create-task.js"]
                  :html ["create-task.html"]}

   :calendar-view {:depends [:common-html]
                   :js ["calendar-view.js" "reservation-slot-edit-bubble-model.js"
                        "reservation-slot-create-bubble-model.js" "calendar-view-model.js"
                        "authority-calendar-model.js" "applicant-calendar-model.js"
                        "reservation-slot-reserve-bubble-model.js" "reserved-slot-bubble-model.js" "calendar-notification-list.js"
                        "book-appointment-filter.js" "base-calendar-model.js"]
                   :html ["reserved-slot-bubble-template.html" "reservation-slot-edit-bubble-template.html"
                          "reservation-slot-create-bubble-template.html" "calendar-view-template.html"
                          "authority-calendar-template.html" "applicant-calendar-template.html"
                          "calendar-notification-list-template.html"
                          "reservation-slot-reserve-bubble-template.html"
                          "book-appointment-filter-template.html"]}

   :application  {:depends [:common-html :global-models :repository :tree :task :create-task :modal-datepicker
                            :signing :invites :verdict-attachment-prints :calendar-view]
                  :js ["add-link-permit.js" "map-model.js" "change-location.js" "invite.js" "verdicts-model.js"
                       "add-operation.js" "foreman-model.js"
                       "add-party.js" "archival-summary.js" "case-file.js"
                       "application.js"]
                  :html ["add-link-permit.html"
                         "application.html" "inforequest.html" "add-operation.html" "change-location.html"
                         "foreman-template.html" "archival-summary-template.html"
                         "required-fields-summary-tab-template.html" "parties-tab-template.html"
                         "case-file-template.html" "application-actions-template.html"]}

   :applications {:depends [:common-html :repository :invites :global-models]
                  :html ["applications-list.html"]
                  :js ["applications-list.js"]}

   :statement    {:depends [:common-html :repository]
                  :js ["statement-service.js" "statement.js"]
                  :html ["statement.html"]}

   :verdict      {:depends [:common-html :repository :attachment]
                  :js ["verdict.js"]
                  :html ["verdict.html"]}

   :neighbors    {:depends [:common-html :repository]
                  :js ["neighbors.js"]
                  :html ["neighbors.html"]}

   :register     {:depends [:common-html]
                  :js ["registration-models.js" "register.js"]
                  :html ["register.html" "register2.html" "register3.html"]}

   :register-company {:depends [:common-html]
                      :js ["company-registration.js"]
                      :html [
                             "register-company.html" "register-company-success.html" "register-company-fail.html"
                             "register-company-account-type.html" "register-company-signing.html"
                             "register-company-existing-user-success.html"]}

   :link-account {:depends [:register]
                  :js ["link-account.js"]
                  :html ["link-account-1.html" "link-account-2.html" "link-account-3.html"]}

   :docgen       {:depends [:accordion :common-html]
                  :js ["docmodel.js" "docgen.js" "docutils.js" "document-approval-model.js"]}

   :create       {:depends [:common-html :map]
                  :js ["locationmodel.js" "municipalities.js" "create.js"]
                  :html ["map-popup.html" "create.html"]}

   :iframe       {:depends [:common-html]
                  :css ["iframe.css"]}

   :login        {:depends [:common-html]
                  :js      ["login.js"]}

   :users        {:js ["users.js"]
                  :html ["users.html"]}

   :company      {:js ["company.js"]
                  :html ["company.html"]}

   :admins       {:depends [:users]}

   :password-reset {:depends [:common-html]
                    :js ["password-reset.js"]
                    :html ["password-reset.html"]}

   :change-email {:depends [:common-html]
                  :js ["change-email.js"]
                  :html ["init-email-change.html" "change-email.html"]}

   :integration-error {:js [ "integration-error.js"]
                       :html ["integration-error.html"]}

   :integration-message-monitor {:js [ "integration-message-monitor-model.js"]
                       :html ["integration-message-monitor-template.html"]}

   :ui-components {:depends [:common-html]
                   :js (distinct (conj (get-ui-components :ui-components :models) "docgen/ui-components.js"))
                   :html (get-ui-components :ui-components :templates)}

   :authority-admin-components {:depends [:common-html]
                   :js (distinct (conj (get-ui-components :authority-admin-components :models) "register-authority-admin-components.js"))
                   :html (get-ui-components :authority-admin-components :templates)}

   ;; Single Page Apps and standalone components:
   ;; (compare to auth-methods in web.clj)

   :hashbang     {:depends [:common-html :ui-components :header :footer]
                  :js ["hashbang.js"]
                  :html ["index.html"]}

   :upload       {:depends [:iframe :attachment-utils]
                  :js ["upload.js"]
                  :css ["upload.css"]}

   :new-appointment {:depends [:calendar-view]
                     :js ["new-appointment.js"]
                     :html ["new-appointment.html"]}

   :applicant-app {:depends []
                   :js ["applicant.js"]}

   :applicant     {:depends [:applicant-app
                             :common-html :authenticated :map :applications :application
                             :statement :docgen :create :mypage :header :debug
                             :company :analytics :register-company :footer :new-appointment :ui-components]}

   :mycalendar   {:depends [:calendar-view]
                  :js ["mycalendar.js"]
                  :html ["mycalendar.html"]}

   :authority-app {:depends [] :js ["authority.js"]}
   :authority     {:depends [:authority-app :common-html :external-api :authenticated :map :applications
                             :integration-message-monitor :application
                             :statement :verdict :neighbors :docgen :create :mypage :header :debug
                             :company :stamp :integration-error :analytics :metadata-editor :footer :mycalendar :ui-components]}

   :oir-app {:depends [] :js ["oir.js"]}
   :oir     {:depends [:oir-app :common-html :authenticated :map :application :attachment
                       :docgen :debug :analytics :header :footer :ui-components]
             :css ["oir.css"]}

   :authority-admin-app {:depends []
                         :js ["authority-admin-app.js"]}
   :authority-admin     {:depends [:authority-admin-app :global-models :common-html :authenticated :admins
                                   :accordion :mypage :calendar-view :header :debug :analytics :proj4 :ol :footer
                                   :ui-components :authority-admin-components]
                         :js [schema-versions-by-permit-type "organization-model.js" "wfsmodel.js" "organization-user.js"
                              "organization-reports.js" "edit-roles-dialog-model.js"
                              "calendars-model.js" "organization-reservation-types-model.js"
                              "organization-reservation-properties-model.js"
                              "municipality-maps-service.js" "authority-admin.js"]
                         :html ["index.html" "organization-users.html" "applications-settings.html" "selected-attachments.html" "selected-operations.html" "organization-areas.html" "organization-backends.html"
                                "organization-reports.html" "organization-calendars.html" "calendar-admin.html"]}

   :admin-app {:depends []
               :js ["admin.js"]}
   :admin     {:depends [:admin-app :global-models :common-html :authenticated :admins :accordion :map :mypage :header :debug :footer
                         :ui-components :authority-admin-components]
               :js ["admin-users.js" "organization.js" "organizations.js" "companies.js"
                    "features.js" "actions.js" "sso-keys.js" "screenmessages-list.js" "notifications.js"
                    "create-scope-model.js" "logs.js" "reports.js"]
               :html ["index.html" "admin.html" "organization.html"
                      "admin-users.html" "organizations.html" "companies.html"
                      "features.html" "actions.html" "sso-keys.html"
                      "screenmessages-list.html" "notifications.html"
                      "create-scope-template.html" "logs.html" "reports.html"]}

   :wordpress {:depends [:login :password-reset]}

   :welcome-app {:depends []
                 :js ["welcome.js"]}

   :welcome {:depends [:welcome-app  :analytics :global-models :ui-components :login :register :register-company
                       :link-account :debug :header :screenmessages :password-reset :change-email :footer]
             :js ["company-user.js"]
             :html ["index.html" "login.html" "company-user.html" "gtm.html"]}

   :oskari  {:css ["oskari.css"]}

   :neighbor-app {:depends []
                  :js ["neighbor-app.js"]}
   :neighbor {:depends [:neighbor-app :common-html :global-models :map :debug :docgen :debug :header :screenmessages :analytics :footer :ui-components]
              :html ["neighbor-show.html"]
              :js ["neighbor-show.js"]}

   :bulletins {:depends [:ui-components :map :docgen :services]
               :html ["header.html" "footer.html"
                      "bulletins.html" "bulletins-template.html"
                      "application-bulletin/application-bulletin-template.html"
                      "application-bulletin/bulletin-comment/bulletin-comment-template.html"
                      "application-bulletin/tabs/attachments/bulletin-attachments-tab-template.html"
                      "application-bulletin/tabs/attachments/bulletin-attachments-table-template.html"
                      "application-bulletin/bulletin-comment/bulletin-comment-template.html"
                      "application-bulletin/tabs/info/bulletin-info-tab-template.html"
                      "application-bulletin/tabs/verdicts/verdicts-template.html"
                      "application-bulletin/tabs/verdicts/bulletin-verdicts-tab-template.html"
                      "application-bulletin/tabs/instructions/bulletin-instructions-tab-template.html"
                      "application-bulletin/bulletin-comment/bulletin-comment-box/bulletin-comment-box-template.html"
                      "application-bulletins/application-bulletins-template.html"
                      "application-bulletins/application-bulletins-list/application-bulletins-list-template.html"
                      "application-bulletins/load-more-application-bulletins/load-more-application-bulletins-template.html"
                      "application-bulletins/bulletins-search/bulletins-search-template.html"
                      "application-bulletins/bulletins-search/autocomplete/autocomplete-municipalities-template.html"
                      "application-bulletins/bulletins-search/autocomplete/autocomplete-states-template.html"]
               :js ["header.js"
                    "bulletins.js" "bulletins-model.js"
                    "application-bulletins-service.js"
                    "vetuma-service.js"
                    "application-bulletin/application-bulletin-model.js"
                    "application-bulletin/bulletin-comment/bulletin-comment-model.js"
                    "application-bulletin/bulletin-comment/bulletin-comment-box/bulletin-comment-box-model.js"
                    "application-bulletin/tabs/attachments/bulletin-attachments-tab-model.js"
                    "application-bulletin/tabs/verdicts/bulletin-verdicts-tab-model.js"
                    "application-bulletin/tabs/instructions/bulletin-instructions-tab-model.js"
                    "application-bulletins/application-bulletins-model.js"
                    "application-bulletins/application-bulletins-list/application-bulletins-list-model.js"
                    "application-bulletins/load-more-application-bulletins/load-more-application-bulletins-model.js"
                    "application-bulletins/bulletins-search/bulletins-search-model.js"
                    "application-bulletins/bulletins-search/autocomplete/autocomplete-municipalities-model.js"
                    "application-bulletins/bulletins-search/autocomplete/autocomplete-states-model.js"]}
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
