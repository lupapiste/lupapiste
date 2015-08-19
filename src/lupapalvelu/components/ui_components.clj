(ns lupapalvelu.components.ui-components
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [swiss.arrows :refer [-<>>]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :as action]
            [lupapalvelu.components.core :as c]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.xml.validator :as validator]
            [lupapalvelu.document.schemas :as schemas]
            [sade.env :as env]
            [sade.util :as util]
            [cheshire.core :as json]
            [lupapalvelu.attachment :refer [attachment-types-osapuoli, attachment-scales, attachment-sizes]]
            [lupapalvelu.company :as company]
            [lupapalvelu.stamper :refer [file-types]]
            [lupapalvelu.states :as states]
            [scss-compiler.core :as scss]
            [me.raynes.fs :as fs]))

(def debugjs {:depends [:jquery]
              :js ["debug.js"]
              :name "common"})

(def mockjax {:depends [:jquery]
              :js ["jquery.mockjax.js"]
              :name "jquery"})

(defn- conf []
  (let [js-conf {:maps                  (env/value :maps)
                 :analytics             (env/value :analytics)
                 :frontpage             (env/value :frontpage)
                 :fileExtensions        mime/allowed-extensions
                 :passwordMinLength     (env/value :password :minlength)
                 :mode                  env/mode
                 :build                 (:build-number env/buildinfo)
                 :cookie                (env/value :cookie)
                 :wannaJoinUrl          (env/value :oir :wanna-join-url)
                 :userAttachmentTypes   (map #(str "osapuolet." (name %)) attachment-types-osapuoli)
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
                 :degrees               (map :name (:body schemas/koulutusvalinta))}]
    (str "var LUPAPISTE = LUPAPISTE || {};LUPAPISTE.config = " (json/generate-string js-conf) ";")))

(defn- loc->js []
  (str ";loc.setTerms(" (json/generate-string (i18n/get-localizations)) ");"))

(defn- schema-versions-by-permit-type []
  (str ";LUPAPISTE.config.kryspVersions = " (json/generate-string validator/supported-krysp-versions-by-permit-type) ";"))

(defn- main-style-file [css-file-path scss-file-path]
  (if-let [main-css-file (io/resource (c/path css-file-path))]
    (slurp main-css-file)
    (scss/scss->css (.getPath (-> scss-file-path c/path io/resource)))))

(defn- read-component-list-from-fs [component pattern]
  (let [path (str "resources/private/" (name component))
        files (fs/find-files path (re-pattern pattern))
        mapped-files (map #(-<>> % .getPath (s/replace <> env/file-separator "/")  (re-matches (re-pattern (str "^.*/" path "/(.*)"))) last) files)]
    mapped-files))

(defn- in-jar?
  [jar]
  (re-find (re-pattern ".jar$") jar))

(defn- read-component-list-from-jar [jar component pattern]
  (let [path (str "private/" (name component))
        files (util/list-jar jar path)
        filtered-files (filter #(re-find (re-pattern pattern) %) files)]
    filtered-files))

(defn- get-ui-components [component type]
  (let [jar (util/this-jar lupapalvelu.main)
        pattern (case type
                  :models ".*-model.js$"
                  :templates ".*-template.html$")]
    (if (in-jar? jar)
      (read-component-list-from-jar jar component pattern)
      (read-component-list-from-fs component pattern))))

(def ui-components
  {;; 3rd party libs
   :cdn-fallback   {:js ["jquery-1.11.3.min.js" "jquery-ui-1.10.2.min.js" "jquery.dataTables.min.js"]}
   :jquery         {:js ["jquery.ba-hashchange.js" "jquery.metadata-2.1.js" "jquery.cookie.js" "jquery.caret.js"]}
   :jquery-upload  {:js ["jquery.ui.widget.js" "jquery.iframe-transport.js" "jquery.fileupload.js"]}
   :knockout       {:js ["knockout-3.3.0.min.js" "knockout.mapping-2.4.1.js" "knockout.validation.min.js" "knockout-repeat-2.0.0.js"]}
   :lo-dash        {:js ["lodash.min.js"]}
   :underscore     {:depends [:lo-dash]
                    :js ["underscore.string.min.js" "underscore.string.init.js"]}
   :moment         {:js ["moment.min.js"]}
   :open-layers    {:js ["openlayers-2.13.1_20150817.min.lupapiste.js"]}
   :ol             {:js ["openlayers-3.8.2.debug.js"]
                    :css ["openlayers-3.8.2.css"]}
   :proj4          {:js ["proj4.js"]}
   :leaflet        {:js ["leaflet.min.js"]
                    :css ["leaflet.css"]}
   :stickyfill     {:js ["stickyfill.min.js"]}

   ;; Init can also be used as a standalone lib, see web.clj
   :init         {:depends [:underscore]
                  :js [conf "hub.js" "log.js" ]}

   ;; Common components

   :debug        (if (env/dev-mode?) debugjs {})

   :mockjax      (if (env/dev-mode?) mockjax {})

   :i18n         {:depends [:jquery :underscore]
                  :js ["loc.js" loc->js]}

   :selectm      {:js ["selectm.js"]}

   :selectm-html {:html ["selectm.html"]}

   :expanded-content  {:depends [:jquery]
                       :js ["expanded-content.js"]}

   :common       {:depends [:init :jquery :jquery-upload :knockout :underscore :moment :i18n :selectm
                            :expanded-content :mockjax :open-layers :stickyfill]
                  :js ["register-components.js" "util.js" "event.js" "pageutil.js" "notify.js" "ajax.js" "app.js" "nav.js"
                       "ko.init.js" "dialog.js" "datepicker.js" "requestcontext.js" "currentUser.js" "perfmon.js" "features.js"
                       "statuses.js" "authorization.js" "vetuma.js"]}

   :common-html  {:depends [:selectm-html]
                  :css [(partial main-style-file "common-html/css/main.css" "common-html/sass/main.scss") "jquery-ui.css"]
                  :html ["404.html" "footer.html"]}

   ;; Components to be included in a SPA

   :analytics    {:js ["analytics.js"]}

   :global-models {:js ["root-model.js" "application-model.js" "register-models.js"]}

   :screenmessages  {:js   ["screenmessage.js"]
                     :html ["screenmessage.html"]}

   :map          {:depends [:common-html]
                  :js [ "gis.js" "locationsearch.js"]}

   :mypage       {:depends [:common-html]
                  :js ["mypage.js"]
                  :html ["mypage.html"]}

   :header     {:html ["header.html"], :js ["header.js"]}

   :modal-datepicker {:depends [:common-html]
                      :html ["modal-datepicker.html"]
                      :js   ["modal-datepicker.js"]}

   :authenticated {:depends [:screenmessages :analytics]
                   :js ["comment.js"]
                   :html ["comments.html"]}

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

   :verdict-attachment-prints {:depends [:common-html]
                               :html ["verdict-attachment-prints-order-template.html"
                                      "verdict-attachment-prints-order-history-template.html"
                                      "verdict-attachment-prints-multiselect.html"]
                               :js ["verdict-attachment-prints-order-model.js"
                                    "verdict-attachment-prints-order-history-model.js"
                                    "verdict-attachment-prints-multiselect-model.js"]}


   :attachment   {:depends [:common-html :repository :signing :side-panel]
                  :js ["attachment-multi-select.js"
                       "targeted-attachments-model.js"
                       "attachment.js"
                       "move-attachment-to-backing-system.js"
                       "move-attachment-to-case-management.js"]
                  :html ["targetted-attachments-template.html"
                         "attachment.html"
                         "upload.html"
                         "move-attachment-to-backing-system.html"
                         "move-attachment-to-case-management.html"]}

   :task         {:depends [:common-html :attachment]
                  :js ["task.js"]
                  :html ["task.html"]}

   :create-task  {:js ["create-task.js"]
                  :html ["create-task.html"]}

   :application  {:depends [:common-html :global-models :repository :tree :task :create-task :modal-datepicker :signing :invites :side-panel :verdict-attachment-prints]
                  :js ["add-link-permit.js" "map-model.js" "change-location.js" "invite.js" "verdicts-model.js"
                       "add-operation.js" "foreman-model.js"
                       "request-statement-model.js" "add-party.js" "attachments-tab-model.js"
                       "application.js"]
                  :html ["attachment-actions-template.html" "attachments-template.html" "add-link-permit.html" "application.html" "inforequest.html" "add-operation.html"
                         "change-location.html" "foreman-template.html"]}

   :applications {:depends [:common-html :repository :invites :global-models]
                  :html ["applications-list.html"]
                  :js ["applications-list.js"]}

   :statement    {:depends [:common-html :repository :side-panel]
                  :js ["statement.js"]
                  :html ["statement.html"]}

   :verdict      {:depends [:common-html :repository :attachment]
                  :js ["verdict.js"]
                  :html ["verdict.html"]}

   :neighbors    {:depends [:common-html :repository :side-panel]
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
                  :js ["docmodel.js" "docgen.js"]}

   :create       {:depends [:common-html :map]
                  :js ["municipalities.js" "create.js"]
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

   :notice       {:js ["notice.js"]
                  :html ["notice.html"]}

   :side-panel   {:js ["side-panel.js"]
                  :html ["side-panel.html"]}

   :password-reset {:depends [:common-html]
                    :js ["password-reset.js"]
                    :html ["password-reset.html"]}

   :integration-error {:js [ "integration-error.js"]
                       :html ["integration-error.html"]}

   :ui-components {:depends [:common-html]
                   :js (distinct (conj (get-ui-components :ui-components :models) "ui-components.js" "input-model.js"))
                   :html (get-ui-components :ui-components :templates)}

   ;; Single Page Apps and standalone components:
   ;; (compare to auth-methods in web.clj)

   :hashbang     {:depends [:common-html]
                  :html ["index.html"]}

   :upload       {:depends [:iframe :attachment-utils]
                  :js ["upload.js"]
                  :css ["upload.css"]}

   :applicant-app {:depends [:ui-components]
                   :js ["applicant.js"]}
   :applicant     {:depends [:applicant-app
                             :common-html :authenticated :map :applications :application
                             :statement :docgen :create :mypage :header :debug
                             :company :analytics :register-company]}

   :authority-app {:depends [:ui-components] :js ["authority.js"]}
   :authority     {:depends [:ui-components :authority-app :common-html :authenticated :map :applications :notice :application
                             :statement :verdict :neighbors :docgen :create :mypage :header :debug
                             :company :stamp :integration-error :analytics :metadata-editor]}

   :oir-app {:depends [:ui-components] :js ["oir.js"]}
   :oir     {:depends [:oir-app :common-html :authenticated :map :application :attachment
                       :docgen :debug :notice :analytics :header]
             :css ["oir.css"]}

   :authority-admin-app {:depends [:ui-components]
                         :js ["authority-admin-app.js" "register-authority-admin-models.js"]}
   :authority-admin     {:depends [:authority-admin-app :common-html :authenticated :admins :mypage :header :debug :analytics :leaflet :proj4 :ol]
                         :js [schema-versions-by-permit-type "organization-user.js" "edit-roles-dialog-model.js" "authority-admin.js"]
                         :html ["authority-admin.html"]}

   :admin-app {:depends [:ui-components]
               :js ["admin.js" "register-admin-models.js"]}
   :admin     {:depends [:admin-app :common-html :authenticated :admins :map :mypage :header :debug]
               :css ["admin.css"]
               :js ["admin-users.js" "organizations.js" "companies.js" "features.js" "actions.js" "screenmessages-list.js"]
               :html ["index.html" "admin.html" "organization.html"
                      "admin-users.html" "organizations.html" "companies.html" "features.html" "actions.html"
                      "screenmessages-list.html"]}

   :wordpress {:depends [:login :password-reset]}

   :welcome-app {:depends [:ui-components]
                 :js ["welcome.js"]}

   :welcome {:depends [:welcome-app :login :register :register-company :link-account :debug :header :screenmessages :password-reset :analytics]
             :js ["company-user.js"]

             :html ["index.html" "login.html" "company-user.html"]}

   :oskari  {:css ["oskari.css"]}

   :neighbor-app {:depends [:ui-components]
                  :js ["neighbor-app.js"]}
   :neighbor {:depends [:neighbor-app :common-html :global-models :map :debug :docgen :debug :header :screenmessages :analytics]
              :html ["neighbor-show.html"]
              :js ["neighbor-show.js"]}})

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
