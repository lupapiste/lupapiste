(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "lupapalvelu"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.zip "0.1.1"] ; Note: 0.1.2 breaks lupapalvelu.wfs
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/tools.reader "1.1.3.1"]
                 [org.clojure/tools.trace "0.7.9"]
                 [org.clojure/core.memoize "0.5.9"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/core.match "0.2.2"]
                 [org.clojure/test.check "0.9.0"]
                 [com.gfredericks/test.chuck "0.2.9"]
                 [prismatic/plumbing "0.5.5"]

                 ; State management
                 [mount "0.1.12"]

                 ; Web frameworks
                 [ring "1.6.2" :exclusions [commons-fileupload org.clojure/tools.reader]]
                 [noir "1.3.0" :exclusions [compojure clj-stacktrace org.clojure/tools.macro ring hiccup bultitude]]
                 [compojure "1.1.9" :exclusions [org.clojure/tools.macro ring]]
                 [metosin/ring-swagger "0.22.12"]
                 [metosin/ring-swagger-ui "2.2.5-0"]

                 ; Namespace finder library
                 [bultitude "0.2.8"] ; noir requires 0.2.0
                 [org.tcrawley/dynapath "1.0.0"]            ; bultitudes requires 0.2.3, but midje needs 1.0.0, should be compatible

                 ; MongoDB driver
                 [com.novemberain/monger "3.1.0" :exclusions [[com.google.guava/guava]]]

                 ; S3 Library
                 [com.amazonaws/aws-java-sdk-s3 "1.11.313"]

                 ; UUID Library
                 [danlentz/clj-uuid "0.1.7"]

                 ; Logging
                 [com.taoensso/timbre "4.10.0" :exclusions [[io.aviso/pretty]]]
                 [org.slf4j/slf4j-log4j12 "1.7.22"]
                 ; upgraded pretty to match Midje version, should work with Timbre
                 [io.aviso/pretty "0.1.34"]

                 ; markup processing
                 [enlive "1.1.6"]
                 [endophile "0.1.2" :exclusions [hiccup]]
                 [cljstache "2.0.1"]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.5.2"]
                 [org.freemarker/freemarker "2.3.23"]
                 ; CSS
                 [garden "1.3.3"]

                 ; Encryption and secure hashing
                 [org.jasypt/jasypt "1.9.2"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [crypto-random "1.2.0" :exclusions [commons-codec]]
                 [org.bouncycastle/bcprov-jdk15on "1.55"]
                 [pandect "0.6.1"]

                 ; JSON
                 [cheshire "5.7.0"]

                 ; HTTP client
                 [clj-http "3.4.1" :exclusions [commons-codec]]

                 ; Email client
                 [com.draines/postal "1.11.4" :exclusions [commons-codec/commons-codec]]

                 ; iCalendar
                 [org.mnode.ical4j/ical4j "1.0.7" :exclusions [commons-logging]]

                 ; Apache Commons
                 [commons-fileupload "1.3.3"] ; explicit requirement to catch version upgrades
                 [org.apache.commons/commons-lang3 "3.5"]
                 [commons-io "2.5"]
                 [commons-codec "1.10"]

                 ; Joda time wrapper
                 [clj-time "0.14.2"]

                 ; Country code manipulation
                 [iso-country-codes "1.0"]

                 ; String case manipulation
                 [camel-snake-kebab "0.4.0"]

                 ; Collection of arrow macros
                 [swiss-arrows "1.0.0"]

                 ; File system lib
                 [me.raynes/fs "1.4.6" :exclusions [org.apache.commons/commons-compress]] ; later version required by pantomime -> tika

                 ; Enhanced try and throw
                 [slingshot "0.12.2"]

                 ; A Clojure(Script) library for declarative data description and validation
                 [prismatic/schema "1.1.9"]
                 [prismatic/schema-generators "0.1.2"]

                 ; MIME type resolution
                 [com.novemberain/pantomime "2.8.0" :exclusions [org.opengis/geoapi org.bouncycastle/bcprov-jdk15on]]

                 ; RSS
                 [clj-rss "0.2.3"]

                 ; Image processing
                 [com.github.jai-imageio/jai-imageio-core "1.3.1"]
                 [com.github.jai-imageio/jai-imageio-jpeg2000 "1.3.0"]
                 [com.google.zxing/javase "2.2"] ; QR codes
                 [com.twelvemonkeys.imageio/imageio-jpeg "3.2.1"]

                 ; MS Office document processing
                 [ontodev/excel "0.2.4" :exclusions [xml-apis org.apache.poi/poi-ooxml]]
                 [org.apache.poi/poi-ooxml "3.15"]
                 [dk.ative/docjure "1.11.0"] ; this also depends on Apache POI v3.14
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.converter.docx.xwpf  "1.0.6"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.itext.extension  "1.0.6" :exclusions [com.lowagie/itext]]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.document.docx  "1.0.6"]
                 [fr.opensagres.xdocreport/fr.opensagres.xdocreport.template.freemarker "1.0.6" :exclusions [org.freemarker/freemarker]]

                 ; Apache pdfbox for PDF/A wrapper
                 [org.apache.pdfbox/pdfbox "2.0.6"]
                 [org.apache.pdfbox/xmpbox "2.0.6"]

                 ; JavaScript and CSS compression
                 [com.yahoo.platform.yui/yuicompressor "2.4.8" :exclusions [rhino/js org.mozilla/rhino]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523

                 ;; Geo location libs
                 ; WKT parser
                 [cljts "0.3.0-20150228.035522-2" :exclusions [xerces/xercesImpl]]
                 ; Coordinate conversions, shape file handling etc.
                 [org.geotools/gt-main "18.1"]
                 [org.geotools/gt-shapefile "18.1"]
                 [org.geotools/gt-geojson "18.1"]
                 [org.geotools/gt-referencing "18.1"]
                 [org.geotools/gt-epsg-wkt "18.1"]

                 ; Message Queue
                 [org.apache.activemq/artemis-jms-client "2.6.2"]
                 [lupapiste/jms-client "0.2.1"]
                 [com.taoensso/nippy "2.14.0"]

                 ;; Lupapiste libraries
                 ; Oskari map (https://github.com/lupapiste/oskari)
                 [lupapiste/oskari "1.0.0"]
                 ; Shared domain code (https://github.com/lupapiste/commons)
                 [lupapiste/commons "0.9.23"]
                 ; Smoke test lib (https://github.com/lupapiste/mongocheck)
                 [lupapiste/mongocheck "0.1.3"]
                 ; iText fork with bug fixes and upgraded dependencies (https://github.com/lupapiste/OpenPDF)
                 [lupapiste/openpdf "1.0.6"]
                 ; Wrapper for clj-pdf for PDF/A document generation
                 [lupapiste/pdfa-generator "1.0.2" :exclusions [org.clojure/tools.reader xalan]]
                 ; JMX-server with socket reuse
                 [lupapiste/jmx-server "0.1.0"]

                 ;; Used in the markup support.
                 [instaparse "1.4.8"]

                 [org.clojure/clojurescript "1.9.946"]
                 [rum "0.10.8"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 ; JS Pikaday for cljs datepicker (https://github.com/dbushell/Pikaday)
                 [cljs-pikaday "0.1.4"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-shell "0.5.0"]
            [deraen/lein-sass4clj "0.3.1"]
            [lein-pdo "0.1.1"]
            [lein-midje "3.2.1"]
            [jonase/eastwood "0.2.3" :exclusions [org.clojure/tools.namespace org.clojure/clojure]]
            [lupapiste/lein-buildid "0.4.2"]
            [lupapiste/lein-nitpicker "0.5.1"]
            [lein-figwheel "0.5.16"]]

  :clean-targets ^{:protect false} ["resources/public/lp-static/js/rum-app.js"
                                    "resources/public/lp-static/js/rum-app.js.map"
                                    "resources/public/lp-static/js/out"
                                    :target-path]
  :source-paths ["src" "src-cljc"]
  :java-source-paths ["java-src"]
  :cljsbuild {:builds {:rum {:source-paths ^:replace ["src-cljs" "src-cljc"]}}}
  :profiles {:dev      {:dependencies   [[midje "1.9.1"]
                                         [com.cemerick/pomegranate "1.0.0"]                                             ; midje.repl needs this
                                         [ring/ring-mock "0.3.0" :exclusions [ring/ring-codec]]
                                         [com.raspasov/clj-ssh "0.5.12"]
                                         [org.apache.activemq/artemis-jms-server "2.6.0"]
                                         [rhizome "0.2.7"]
                                         [pdfboxing "0.1.13"]
                                         [cider/piggieback "0.3.6"]
                                         [figwheel-sidecar "0.5.16"]
                                         ;; Better Chrome Dev Tools support
                                         [binaryage/devtools "0.9.4"]]
                        :resource-paths ["dev-resources"]
                        :source-paths   ["dev-src" "test-utils"]
                        :jvm-opts       ["-Djava.awt.headless=true" "-Xmx2G" "-Dfile.encoding=UTF-8"]
                        :eastwood       {:continue-on-exception true
                                         :source-paths          ["src"]
                                         :test-paths            []}
                        :sass           {:output-style :expanded
                                         :source-map   true}
                        :repl-options   {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]
                                         :timeout          200000}
                        :cljsbuild      {:builds {:rum {:figwheel {:websocket-host  :js-client-host
                                                                   :on-jsload        lupapalvelu.ui.ui-components/reload-hook
                                                                   ;; If the figwheel does not connect,
                                                                   ;; turn the heads-up display off.
                                                                   :heads-up-display true
                                                                   }
                                                        :compiler {:output-dir     "resources/public/lp-static/js/out"
                                                                   :output-to      "resources/public/lp-static/js/rum-app.js"
                                                                   :main           lupapalvelu.ui.ui-components
                                                                   :source-map     true
                                                                   :asset-path     "/lp-static/js/out"
                                                                   :parallel-build true
                                                                   :pretty-print   true
                                                                   :optimizations  :none
                                                                   :preloads       [devtools.preload]}}}}}
             :uberjar  {:main       lupapalvelu.main
                        :cljsbuild  {:builds {:rum {:compiler ^:replace {:output-dir     "resources/public/lp-static/js/out"
                                                                         :output-to      "resources/public/lp-static/js/rum-app.js"
                                                                         :asset-path     "/lp-static/js/out"
                                                                         :externs        ["src-cljs/lupapalvelu/ui/lupapiste-externs.js"
                                                                                          "src-cljs/lupapalvelu/ui/moment.ext.js"]
                                                                         :parallel-build true
                                                                         :pretty-print   false
                                                                         :optimizations  :advanced}}}}
                        :prep-tasks [["cljsbuild" "once" "rum"]
                                     "javac"
                                     "compile"]}
             :itest    {:test-paths ^:replace ["itest"]}
             :stest    {:test-paths ^:replace ["stest"]}
             :alltests {:source-paths ["test" "itest" "stest"]
                        :jvm-opts     ["-Djava.awt.headless=true" "-Xmx1G"]}
             :intellij {:test-paths ["itest" "stest"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=https://www-dev.lupapiste.fi" "-Djava.awt.headless=true"]}
             :lupatest {:jvm-opts ["-Dtarget_server=https://www-test.lupapiste.fi" "-Djava.awt.headless=true"]}}
  :figwheel {:server-port 3666
             :css-dirs    ["resources/public/lp-static/css/main.css"]
             :repl false}
  :sass {:target-path  "resources/public/lp-static/css/"
         :source-paths ["resources/private/common-html/sass/"]
         :output-style :compressed}
  :jvm-opts ["-Dfile.encoding=UTF-8"]
  :nitpicker {:exts     ["clj" "js" "html"]
              :excludes [#"jquery" #"underscore" #"terms\.html" #"\/email-templates\/" #"proj4" #".debug" #"lp-static/js/"]}
  :repositories [["osgeo" {:url "https://download.osgeo.org/webdav/geotools"}]]
  :aliases {"integration" ["with-profile" "dev,itest" ["midje" ":filter" "-ajanvaraus"]]
            "ajanvaraus"  ["with-profile" "dev,itest" ["midje" ":filter" "ajanvaraus"]]
            "stest"       ["with-profile" "dev,stest" "midje"]
            "verify"      ["with-profile" "dev,alltests" "do" "nitpicker," ["midje" ":filter" "-ajanvaraus"]]
            "sass"        ["do"
                           ["sass4clj" "once"]
                           ["shell" "blessc" "--force" "resources/public/lp-static/css/main.css"]]
            "front"       ["do"
                           ["clean"]
                           ["pdo"
                            ["sass4clj" "auto"]
                            ["figwheel"]]]}
  :aot [lupapalvelu.main clj-time.core]
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns lupapalvelu.server}
  :pom-plugins [[org.fusesource.mvnplugins/maven-graph-plugin "1.4"]
                [com.googlecode.maven-overview-plugin/maven-overview-plugin "1.6"]]
  :min-lein-version "2.5.0")
