(ns lupapalvelu.components.ui-components
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn error fatal)]
            [lupapalvelu.components.core :as c]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mime :as mime]
            [sade.env :as env]
            [sade.util :as util]
            [cheshire.core :as json]))

(def debugjs {:depends [:init :jquery]
              :js ["debug.js"]
              :name "common"})

(defn- conf []
  (let [js-conf {:maps              (env/value :maps)
                 :fileExtensions    mime/allowed-extensions
                 :passwordMinLength (env/value :password :minlength)
                 :mode              env/mode}]
    (str "var LUPAPISTE = LUPAPISTE || {};LUPAPISTE.config = " (json/generate-string js-conf) ";")))

(defn loc->js []
  (str ";loc.setTerms(" (json/generate-string (i18n/get-localizations)) ");"))

(def ui-components
  {:cdn-fallback   {:js ["jquery-1.8.0.min.js" "jquery-ui-1.10.2.min.js" "jquery.dataTables.min.js" "knockout-2.2.1.js"]}
   :jquery         {:js ["jquery.ba-hashchange.js" "jquery.metadata-2.1.js" "jquery.cookie.js" "jquery.caret.js"]
                    :css ["jquery-ui.css"]}
   :jquery-upload  {:js ["jquery.ui.widget.js" "jquery.iframe-transport.js" "jquery.fileupload.js"]}
   :knockout       {:js ["knockout.mapping-2.3.2.js" "knockout.validation.js" "knockout-repeat-1.4.2.js"]}
   :lo-dash        {:js ["lodash-1.2.1.min.js"]}
   :underscore     {:depends [:lo-dash]
                    :js ["underscore.string.min.js" "underscore.string.init.js"]}
   :moment         {:js ["moment.min.js"]}

   :init         {:js [conf "hub.js" "log.js"]
                  :depends [:underscore]}

   :debug        (if (env/dev-mode?) debugjs {})

   :i18n         {:depends [:jquery :underscore]
                  :js ["loc.js" loc->js]}

   :selectm      {:js   ["selectm.js"]
                  :html ["selectm.html"]
                  :css  ["selectm.css"]}

   :licenses     {:html ["licenses.html"]}
   
   :common       {:depends [:init :jquery :jquery-upload :knockout :underscore :moment :i18n :selectm :licenses]
                  :js ["util.js" "event.js" "pageutil.js" "notify.js" "ajax.js" "app.js" "nav.js"
                       "ko.init.js" "dialog.js" "datepicker.js" "requestcontext.js" "currentUser.js" "features.js"
                       "authorization.js" "vetuma.js"]
                  :css ["css/main.css"]
                  :html ["404.html" "nav.html"]}

   :map          {:depends [:common]
                  :js ["openlayers.2.13_20130911.min.lupapiste.js" "gis.js" "locationsearch.js"]}

   :authenticated {:depends [:init :jquery :knockout :underscore :moment :i18n :selectm]
                   :js ["comment.js" "municipalities.js" "organizations.js"]
                   :html ["comments.html"]}

   :invites      {:depends [:common]
                  :js ["invites.js"]}

   :repository   {:depends [:common]
                  :js ["repository.js"]}

   :accordion    {:depends [:jquery]
                  :js ["accordion.js"]
                  :css ["accordion.css"]}

   :application  {:depends [:common :repository :tree]
                  :js ["change-location.js" "invite.js" "application.js" "add-operation.js"]
                  :html ["application.html" "inforequest.html" "add-operation.html" "change-location.html"]}

   :applications {:depends [:common :repository :invites]
                  :html ["applications.html"]
                  :js ["applications.js"]}

   :attachment   {:depends [:common :repository]
                  :js ["attachment.js" "attachmentTypeSelect.js"]
                  :html ["attachment.html" "upload.html"]}

   :statement    {:depends [:common :repository]
                  :js ["statement.js"]
                  :html ["statement.html"]}

   :verdict      {:depends [:common :repository]
                  :js ["verdict.js"]
                  :html ["verdict.html"]}

   :neighbors    {:depends [:common :repository]
                  :js ["neighbors.js"]
                  :html ["neighbors.html"]}

   :register     {:depends [:common]
                  :css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html" "register3.html"]}

   :docgen       {:depends [:accordion :common]
                  :js ["docgen.js"]}

   :create       {:depends [:common]
                  :js ["create.js"]
                  :html ["create.html"]
                  :css ["create.css"]}

   :applicant    {:depends [:common :authenticated :map :applications :application :attachment
                            :statement :docgen :create :mypage :debug]
                  :js ["applicant.js"]
                  :html ["index.html"]}

   :authority    {:depends [:common :authenticated :map :applications :application :attachment
                            :statement :verdict :neighbors :docgen :create :mypage :debug]
                  :js ["authority.js"]
                  :html ["index.html"]}

   :admins   {:js ["user.js" "users.js"]
              :html ["admin-user-list.html" "user-modification-dialogs.html"]}

   :authority-admin {:depends [:common :authenticated :admins :mypage :debug]
                     :js ["admin.js"]
                     :html ["index.html" "admin.html"]}

   :admin   {:depends [:common :authenticated :admins :map :mypage :debug]
             :js ["admin.js"]
             :html ["index.html" "admin.html"]}

   :tree    {:depends [:jquery]
             :js ["tree.js"]
             :html ["tree.html"]
             :css ["tree.css"]}

   :iframe  {:depends [:common]
             :css ["iframe.css"]}

   :upload  {:depends [:iframe]
             :js ["upload.js"]
             :css ["upload.css"]}

   :login   {:depends [:common]
             :js      ["login.js"]}

   :login-frame {:depends [:login]
                 :html    ["login-frame.html"]
                 :js      ["login-frame.js"]}

   :welcome {:depends [:login :register :debug]
             :js ["welcome.js"]
             :html ["index.html" "login.html"]}

   :oskari  {:css ["oskari.css"]}

   :mypage  {:depends [:common]
             :js ["mypage.js"]
             :html ["mypage.html"]
             :css ["mypage.css"]}

   :about {:depends [:common :debug]
           :js ["about.js"]
           :html ["terms.html" "index.html"]}

   :neighbor {:depends [:common :map :debug :docgen :debug]
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
