(ns lupapalvelu.components.ui-components
  (:use [lupapalvelu.log])
  (:require [lupapalvelu.components.core :as c]
            [lupapalvelu.env :as env]))

(defn foo []
  (if (env/dev-mode?)
    (str "function dev() { warn('This is dev'); return true; };")
    (str "function dev() { warn('This is dev'); return false; };")))

(def oskari {:depends [:init :jquery]
             :js ["oskarimap.js" "map.js"]
             :css ["oskarimap.css"]
             :html ["map.html"]
             :name "oskari"})

(def dummymap {:depends [:init :jquery]
               :js ["openlayers.2.12.js" "gis.js" "map.js"]
               :name "dummymap"})

(def debugjs {:depends [:init :jquery]
              :js ["debug.js"]
              :name "common"})

(def ui-components
  {:jquery       {:css ["jquery.pnotify.default.css"]
                  :js ["jquery-1.8.0.min.js"
                       "jquery-ui-1.9.0.custom.min.js"
                       "jquery.ba-hashchange.js"
                       "jquery.pnotify.min.js"
                       "jquery.metadata-2.1.js"
                       "jquery.autocomplete.js"]}

   :knockout     {:js ["knockout-2.1.0.debug.js" "knockout.mapping-2.3.2.js" "knockout.validation.js"]}
   :underscore   {:js ["underscore.js"]}
   :init         {:js ["hub.js" "log.js" foo]}

   :map          (if (env/dev-mode?) dummymap oskari)

   :debug        (if (env/dev-mode?) debugjs {})

   :common       {:depends [:init :jquery :knockout :underscore :debug]
                  :js ["pageutil.js" "loc.js" "notify.js" "ajax.js" "app.js" "nav.js" "combobox.js" "ko.init.js" "dialog.js" "comment.js" "authorization.js" "lupapiste.application.js"]
                  :css ["css/main.css"]
                  :html ["error.html"]}

   :buildinfo    {:depends [:jquery]
                  :js ["buildinfo.js"]}

   :invites      {:depends [:common]
                  :js ["invites.js"]}

   :repository   {:depends [:common]
                  :js ["repository.js"]}

   :accordion    {:depends [:jquery]
                  :js ["accordion.js"]
                  :css ["accordion.css"]}

   :application  {:depends [:common :repository]
                  :js ["application.js"]
                  :html ["application.html" "inforequest.html"]}

   :tablesorter  {:depends [:jquery]
                  :js ["jquery.tablesorter-2.0.5b.js" "lupapiste.tablesorter.js"]
                  :css ["tablesorter.css"]}

   :applications-common {:depends [:tablesorter :invites]
                         :html ["inforequests.html" "all-applications.html"]
                         :js ["applications.js" "inforequests-config.js"]}

   :applications {:depends [:common :repository :applications-common]
                  :js ["applications-config.js"]
                  :css ["applications.css"]
                  :html ["applications.html" "all-applications.html"]}

   :authority-applications {:depends [:common :repository :applications-common]
                            :js ["applications-config.js"]
                            :html ["applications.html"]}

   :attachment   {:depends [:common :repository]
                  :js ["attachment.js"]
                  :html ["attachment.html" "upload.html"]}

   :register     {:depends [:common]
                  :css ["register.css"]
                  :js ["register.js"]
                  :html ["register.html" "register2.html"]}

   :docgen       {:depends [:accordion :common]
                  :js ["docgen.js"]
                  :css ["docgen.css"]}

   :create-application  {:js ["create-application.js"]
                         :html (map (partial format "create-application-%02d.html") (range 1 (inc 3)))}

   :create-inforequest  {:js ["create-inforequest.js"]
                         :depends [:common]
                         :html ["create-inforequest.html"]}

   :applicant    {:depends [:common :map :applications
                            :application :attachment :create-application :docgen
                            :create-inforequest :buildinfo]
                  :js ["applicant.js"]
                  :html ["index.html"]}

   :authority    {:depends [:common :map :application :authority-applications :attachment :buildinfo :docgen]
                  :js ["authority.js"]
                  :html ["index.html"]}

   :authority-admin {:depends [:common :buildinfo]
                     :js ["admin.js"]
                     :html ["index.html" "admin.html"]}

   :admin        {:depends [:common :map :buildinfo]
                  :js ["admin.js"]
                  :html ["index.html" "admin.html"]}

   :iframe       {:depends [:common]
                  :css ["iframe.css"]}

   :upload       {:depends [:iframe]
                  :js ["upload.js"]
                  :css ["upload.css"]}

   :welcome      {:depends [:common :register :buildinfo]
                  :js ["login.js"]
                  :html ["login.html" "index.html"]}})

; Make sure that all resources are available:

(doseq [c (keys ui-components)
        r (mapcat #(c/component-resources ui-components % c) [:js :html :css])]
  (if (not (fn? r))
    (let [resource (.getResourceAsStream (clojure.lang.RT/baseLoader) (c/path r))]
      (if resource
        (.close resource)
        (throw (Exception. (str "Resource missing: " r)))))))
