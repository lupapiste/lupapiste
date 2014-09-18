(ns lupapalvelu.components.ui-components
  (:require [taoensso.timbre :as timbre :refer [trace debug info warn error fatal]]
            [lupapalvelu.components.core :as c]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.xml.krysp.validator :as validator]
            [sade.env :as env]
            [sade.util :as util]
            [cheshire.core :as json]
            [lupapalvelu.attachment :refer [attachment-types-osapuoli]]))

(def debugjs {:depends [:init :jquery]
              :js ["debug.js"]
              :name "common"})

(def mockjax {:depends [:init :jquery]
              :js ["jquery.mockjax.js"]
              :name "jquery"})

(defn- conf []
  (let [js-conf {:maps              (env/value :maps)
                 :fileExtensions    mime/allowed-extensions
                 :passwordMinLength (env/value :password :minlength)
                 :mode              env/mode
                 :build             (:build-number env/buildinfo)
                 :wannaJoinUrl      (env/value :oir :wanna-join-url)
                 :userAttachmentTypes (map #(str "osapuolet." (name %)) attachment-types-osapuoli)}]
    (str "var LUPAPISTE = LUPAPISTE || {};LUPAPISTE.config = " (json/generate-string js-conf) ";")))

(defn- loc->js []
  (str ";loc.setTerms(" (json/generate-string (i18n/get-localizations)) ");"))

(defn- schema-versions-by-permit-type []
  (str ";LUPAPISTE.config.kryspVersions = " (json/generate-string validator/supported-versions-by-permit-type) ";"))

(def ui-components
  {;; 3rd party libs
   :cdn-fallback   {:js ["jquery-1.8.3.min.js" "jquery-ui-1.10.2.min.js" "jquery.dataTables.min.js" "knockout-2.2.1.js"]}
   :jquery         {:js ["jquery.ba-hashchange.js" "jquery.metadata-2.1.js" "jquery.cookie.js" "jquery.caret.js"]
                    :css ["jquery-ui.css"]}
   :jquery-upload  {:js ["jquery.ui.widget.js" "jquery.iframe-transport.js" "jquery.fileupload.js"]}
   :knockout       {:js ["knockout.mapping-2.3.2.js" "knockout.validation.js" "knockout-repeat-1.4.2.js"]}
   :lo-dash        {:js ["lodash-1.3.1.min.js"]}
   :underscore     {:depends [:lo-dash]
                    :js ["underscore.string.min.js" "underscore.string.init.js"]}
   :moment         {:js ["moment.min.js"]}

   ;; Init can also be used as a standalone lib, see web.clj
   :init         {:depends [:underscore]
                  :js [conf "hub.js" "log.js"]}

   ;; Components to be included in a SPA

   :debug        (if (env/dev-mode?) debugjs {})

   :mockjax      (if (env/dev-mode?) mockjax {})

   :i18n         {:depends [:jquery :underscore]
                  :js ["loc.js" loc->js]}

   :selectm      {:js   ["selectm.js"]
                  :html ["selectm.html"]
                  :css  ["selectm.css"]}

   :licenses     {:html ["licenses.html"]}

   :screenmessages  {:js   ["screenmessage.js"]
                     :html ["screenmessage.html"]}

   :expanded-content  {:depends [:jquery]
                       :js ["expanded-content.js"]}

   :common       {:depends [:init :jquery :jquery-upload :knockout :underscore :moment :i18n :selectm
                            :licenses :expanded-content :mockjax]
                  :js ["util.js" "event.js" "pageutil.js" "notify.js" "ajax.js" "app.js" "nav.js"
                       "ko.init.js" "dialog.js" "datepicker.js" "requestcontext.js" "currentUser.js" "features.js"
                       "statuses.js" "statusmodel.js" "authorization.js" "vetuma.js"]
                  :css ["css/main.css"]
                  :html ["404.html" "footer.html"]}

   :map          {:depends [:common]
                  :js ["openlayers-2.13_20140619.min.lupapiste.js" "gis.js" "locationsearch.js"]}

   :mypage       {:depends [:common]
                  :js ["mypage.js"]
                  :html ["mypage.html"]
                  :css ["mypage.css"]}

   :user-menu     {:html ["nav.html"]}

   :modal-datepicker {:depends [:common]
                      :html ["modal-datepicker.html"]
                      :js   ["modal-datepicker.js"]}

   :authenticated {:depends [:init :jquery :knockout :moment :i18n :selectm :screenmessages]
                   :js ["comment.js"]
                   :html ["comments.html"]}

   :invites      {:depends [:common]
                  :js ["invites.js"]}

   :repository   {:depends [:common]
                  :js ["repository.js"]}

   :tree         {:depends [:jquery]
                  :js ["tree.js"]
                  :html ["tree.html"]
                  :css ["tree.css"]}

   :accordion    {:depends [:jquery]
                  :js ["accordion.js"]
                  :css ["accordion.css"]}

   :signing      {:depends [:common]
                  :html ["signing-dialogs.html"]
                  :js ["signing-model.js"]}

   :attachment   {:depends [:common :repository :signing :side-panel]
                  :js ["targeted-attachments-model.js" "attachment.js" "attachmentTypeSelect.js"]
                  :html ["targetted-attachments-template.html" "attachment.html" "upload.html"]}

   :task         {:depends [:common :attachment]
                  :js ["task.js"]
                  :html ["task.html"]}

   :create-task  {:js ["create-task.js"]
                  :html ["create-task.html"]}

   :application  {:depends [:common :repository :tree :task :create-task :modal-datepicker :signing :invites :side-panel]
                  :js ["add-link-permit.js" "map-model.js" "change-location.js" "invite.js" "verdicts-model.js"
                       "add-operation.js" "stamp-model.js" "request-statement-model.js" "add-party.js"
                       "application-model.js" "invite-company.js" "application.js"]
                  :html ["add-link-permit.html" "application.html" "inforequest.html" "add-operation.html"
                         "change-location.html" "invite-company.html"]}

   :applications {:depends [:common :repository :invites]
                  :html ["applications.html"]
                  :js ["applications.js"]}

   :statement    {:depends [:common :repository :side-panel]
                  :js ["statement.js"]
                  :html ["statement.html"]}

   :verdict      {:depends [:common :repository :attachment]
                  :js ["verdict.js"]
                  :html ["verdict.html"]}

   :neighbors    {:depends [:common :repository :side-panel]
                  :js ["neighbors.js"]
                  :html ["neighbors.html"]}

   :register     {:depends [:common]
                  :js ["registration-models.js" "register.js"
                       "company-registration.js"]
                  :html ["register.html" "register2.html" "register3.html"
                         "register-company.html" "register-company-success.html" "register-company-fail.html"]}

   :link-account {:depends [:register]
                  :js ["link-account.js"]
                  :html ["link-account-1.html" "link-account-2.html" "link-account-3.html"]}

   :docgen       {:depends [:accordion :common]
                  :js ["docmodel.js" "docgen.js"]}

   :create       {:depends [:common]
                  :js ["municipalities.js" "create.js"]
                  :html ["create.html"]
                  :css ["create.css"]}

   :iframe       {:depends [:common]
                  :css ["iframe.css"]}

   :login        {:depends [:common]
                  :js      ["login.js"]}

   :users        {:js ["users.js"]
                  :html ["users.html"]}

   :company      {:js ["company.js"]
                  :html ["company.html"]
                  :css ["company.css"]}

   :admins       {:depends [:users]}

   :notice       {:js ["notice.js"]
                  :html ["notice.html"]}
   
   :side-panel   {:js ["side-panel.js"]
                  :html ["side-panel.html"]}
   
   ;; Single Page Apps and standalone components:
   ;; (compare to auth-methods in web.clj)

   :hashbang     {:depends [:common]
                  :html ["index.html"]}

   :upload       {:depends [:iframe]
                  :js ["upload.js"]
                  :css ["upload.css"]}

   :applicant    {:depends [:common :authenticated :map :applications :application
                            :statement :docgen :create :mypage :user-menu :debug
                            :company]
                  :js ["applicant.js"]
                  :html ["index.html"]}

   :authority    {:depends [:common :authenticated :map :applications :notice :application
                            :statement :verdict :neighbors :docgen :create :mypage :user-menu :debug
                            :company]
                  :js ["authority.js" "integration-error.js"]
                  :html ["index.html" "integration-error.html"]}

   :oir          {:depends [:common :authenticated :map :application :attachment
                            :docgen :debug :notice]
                  :js ["oir.js"]
                  :css ["oir.css"]
                  :html ["index.html"]}

   :authority-admin {:depends [:common :authenticated :admins :mypage :user-menu :debug]
                     :js ["admin.js" schema-versions-by-permit-type]
                     :html ["index.html" "admin.html"]}

   :admin   {:depends [:common :authenticated :admins :map :mypage :user-menu :debug]
             :js ["admin.js"
                  "admin-users.js" "organizations.js" "fixtures.js" "features.js" "actions.js" "screenmessages-list.js"]
             :html ["index.html" "admin.html"
                    "admin-users.html" "organizations.html" "fixtures.html" "features.html" "actions.html"
                    "screenmessages-list.html"]}

   :login-frame {:depends [:login]
                 :html    ["login-frame.html"]
                 :js      ["login-frame.js"]
                 :css     ["login-frame.css"]}

   :welcome {:depends [:login :register :link-account :debug :user-menu :screenmessages]
             :js ["welcome.js"]
             :html ["index.html" "login.html"]}

   :oskari  {:css ["oskari.css"]}

   :neighbor {:depends [:common :map :debug :docgen :debug :user-menu :screenmessages]
              :html ["neighbor-show.html" "index.html"]
              :js ["neighbor-app.js" "neighbor-show.js"]}})

; Make sure all dependencies are resolvable:
(doseq [[component {dependencies :depends}] ui-components
        dependency dependencies]
  (if-not (contains? ui-components dependency)
    (throw (Exception. (format "Component '%s' has dependency to missing component '%s'" component dependency)))))

; Make sure that all resources are available:
(doseq [c (keys ui-components)
        r (mapcat #(c/component-resources ui-components % c) [:js :html :css])]
  (if (not (fn? r))
    (let [resource (.getResourceAsStream (clojure.lang.RT/baseLoader) (c/path r))]
      (if resource
        (.close resource)
        (throw (Exception. (str "Resource missing: " r)))))))
