(ns lupapalvelu.pdf.libreoffice-pdfa-converter
  (:require [clojure.java.shell :as shell]
            [hiccup.core :as hiccup]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre :refer [trace tracef debug debugf info infof warn warnf error errorf fatal fatalf]]
            [sade.strings :as ss]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [sade.util :as util])
  (:import (java.io File)))

; See commanline parameters:
; https://help.libreoffice.org/Common/Starting_the_Software_With_Parameters
; http://kgsspot.blogspot.fi/2011/09/convert-doc-to-pdf-in-command-line.html

; wget http://download.documentfoundation.org/libreoffice/stable/5.0.3/rpm/x86_64/LibreOffice_5.0.3_Linux_x86-64_rpm.tar.gz
; tar -xzf LibreOffice_5.0.3_Linux_x86-64_rpm.tar.gz
; cd LibreOffice_5.0.2.2_Linux_x86_rpm/RPMS/
; yum localinstall *.rpm

; **** autostart service
; libreoffice --headless --accept="socket,host=127.0.0.1,port=8001,tcpNoDelay=1;urp" --nodefault --nofirststartwizard --nolockcheck --nologo --norestore --invisible &

; TODO: make a better solution using JAVA API http://api.libreoffice.org/examples/java/DocumentHandling/DocumentConverter.java which requires installing "libreoffice-sdk"

;;TODO: needs Libre Office SDK libtraries and more coding. See: http://api.libreoffice.org/examples/DevelopersGuide/examples.html
;;com.sun.star.uno.XComponentContext xContext
;;com.sun.star.lang.XMultiComponentFactory xMCF = xContext.getServiceManager();
;;(defn getUNOServiceManager [] (->
;;                                (com.sun.star.comp.helper.Bootstrap/bootstrap)
;;                                (.getServiceManager)))

(defn- connection-url [host port]
  (let [h (if (empty? host) "127.0.0.1" host)
        p (if (empty? port) "2220" port)]
    (str "\"socket,host=" h ",port=" p ",tcpNoDelay=1;urp\"")))

(defn libre-command-local [src dst-dir]
  (shell/sh "libreoffice" "--headless" "--convert-to" "pdf" "--outdir" dst-dir src))

(defn unoconv-command-local [type src dst-file]
  {:pre [(or (= :document type) (= :graphics type) (= :presentation type) (= :spreadsheet type))]}
  (shell/sh "/usr/bin/unoconv" "-d" (name type) "-f" "pdf" "-eSelectPdfVersion=1" "-o" dst-file src))

;;unoconv --connection 'socket, host=127.0.0.1, port=2220, tcpNoDelay=1 ;urp;StarOffice.ComponentContext' -f pdf <filename.rtf>
(defn unoconv-command-remote [type src dst-file host port]
  {:pre [(or (= :document type) (= :graphics type) (= :presentation type) (= :spreadsheet type))]}
  (shell/sh "/usr/bin/unoconv" "-c" (connection-url host port) "-d" (name type) "-f" "pdf" "-eSelectPdfVersion=1" "-o" dst-file src))

(defn- parse-app-operations [{:keys [primaryOperation secondaryOperations]}]
  (ss/join ", " (map (fn [[op c]] (str (if (> c 1) (str c " \u00D7 ")) op))
                     (frequencies (map :name (remove nil? (conj (seq secondaryOperations) primaryOperation)))))))

(defn- td [label value & attr]
  [:td (if (seq? attr) (apply assoc {:style "border: 1.2px solid black; padding: 5px;" :align "left"} attr) {:style "border: 1.2px solid black; padding: 5px;" :align "left"})
   [:h4 {:style "color: #000000;"} (loc label)] [:p {:style "margin-left: 10px;"} (if (ss/blank? value) (loc "application.export.empty") (str value))]])

(defn- th [label & [colspan]]
  [:th {:colspan (if (nil? colspan) "2" (str colspan)) :style "background-color: lightgrey; padding: 1em; border: 1.2px solid black;"} [:h2 {:style "color: #000000;"} label]])

(defn- render-common-html [title app data lang]
  (hiccup/html
    [:html {:lang (name lang)}
     [:head [:title "Lupapiste.fi"]]
     [:body
      [:div {:style "margin: 50px;"}]
      [:img {:src (.getAbsolutePath (File. (.toURI (io/resource "public/img/logo-v2-flat.png"))))}]
      [:h1 title]
      [:h2 (:title app)]
      [:hr]
      [:table {:width "100%"}
       [:tbody
        [:tr (td "application.muncipality" (:municipality app)) (td "application.export.state" (loc (:state app)))]
        [:tr (td "kiinteisto.kiinteisto.kiinteistotunnus" (:propertyId app)) (td "submitted" (:created app))]
        [:tr (td "application.id" (:id app)) (td "applications.authority" (str (get-in app [:authority :lastName]) (get-in app [:authority :firstName])))]
        [:tr (td "application.address" (:address app)) (td "applicant" (clojure.string/join ", " (:_applicantIndex app)))]
        [:tr (td "selectm.source.label.edit-selected-operations" (parse-app-operations app) :colspan "2")]]]]
     [:br]
     [:table {:width "100%"}
      data]]))

(defn- render-statement-html [stm]
  [:tbody
   [:tr (th (loc "application.statement.status"))]
   [:tr (td "statement.requested" (util/to-local-date (:requested stm))) (td "statement.giver" (str (get-in stm [:person :name])))]
   [:tr (td "export.statement.given" (util/to-local-date (:given stm))) (td "statement.title" (:status stm))]
   [:tr (td "statement.text" (:text stm) :colspan "2")]])

(defn- render-history-html [raw-data]
  (loop [data raw-data rows [:tbody
                              [:tr (th (loc "caseFile.heading") 4)]
                              [:tr {:style "background-color: whitesmoke;color: #000000; font-weight: bold;"}
                               [:td [:h3 (loc "caseFile.action")]] [:td  [:h3 (loc "document")]] [:td  [:h3 (loc "attachment")]] [:td  [:h3 (loc "caseFile.documentDate")]]]]]
    (let [action (first data)]
      (if (empty? action)
        rows
        (recur (rest data)
               (-> rows
                   (conj [:tr {:style (if (even? (count rows)) "background-color: lavenderblush;" "")} [:td (:action action)] [:td ""] [:td ""] [:td (:start action)]])
                   (into
                     (loop [docs (:documents action) rows2 []]
                       (let [doc (first docs)]
                         (if (empty? doc)
                           rows2
                           (recur (rest docs) (conj rows2 [:tr [:td ""] [:td (if (= (:category doc) :document) (:category doc) "")] [:td (if-not (= (:category doc) :document) (:category doc) "")] [:td (:ts doc)]]))))))))))))

(defn render-pdf [src-file dst-file]
  (debug " Redering PDF via local unoconv: " (unoconv-command-local :document (.getAbsolutePath src-file) (.getAbsolutePath dst-file))))

(defn render-statement-pdf [app stm lang]
  (let [src (File/createTempFile "test-libre-html-statement-" ".html")
        dst (File/createTempFile "test-libre-html-statement-" ".pdf")
        ]
    (debug " Rendering statement HTML to temp file: " (.getAbsolutePath src))
    (io/copy (with-lang lang (render-common-html (loc "application.statement.status") app (render-statement-html stm) lang)) src)
    (render-pdf src dst)
    (.delete src)
    dst))

(defn render-history-pdf [app history lang]
  (let [src (File/createTempFile "test-libre-html-history-" ".html")
        dst (File/createTempFile "test-libre-html-history-" ".pdf")]
    (debug " Rendering history HTML to temp file: " (.getAbsolutePath src))

      (io/copy (with-lang lang (render-common-html (loc "caseFile.heading") app (render-history-html history) lang)) src)
       (render-pdf src dst)
    ;(.delete src)
    dst))