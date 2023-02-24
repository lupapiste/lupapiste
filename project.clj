(defproject lupapalvelu "2023.1"
  :description "Lupapiste permit service"
  :url "https://www.lupapiste.fi"
  :license {:name         "European Union Public Licence v. 1.2"
            :url          "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"
            :distribution :manual}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.csv "1.0.0"]
                 [org.clojure/data.zip "0.1.1"] ; Note: 0.1.2 breaks lupapalvelu.wfs
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/core.match "1.0.0"]
                 [org.clojure/test.check "1.1.0"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [com.gfredericks/test.chuck "0.2.9"]
                 [prismatic/plumbing "0.5.5"]
                 [com.cognitect/transit-clj "1.0.324"]

                 ;; Older versions do not work on M1.
                 [net.java.dev.jna/jna "5.7.0"]
                 [net.java.dev.jna/jna-platform "5.7.0"]

                 ; State management
                 [mount "0.1.16"]

                 ; Web frameworks
                 [ring "1.6.2" :exclusions [commons-fileupload org.clojure/tools.reader]]
                 [noir "1.3.0" :exclusions [compojure clj-stacktrace org.clojure/tools.macro ring hiccup bultitude]]
                 [org.clojure/tools.macro "0.1.5"]
                 [compojure "1.1.9" :exclusions [org.clojure/tools.macro ring]] ; force noir to use newer version
                 [metosin/ring-swagger "0.26.2"]
                 [metosin/ring-swagger-ui "4.15.5"]
                 [metosin/reitit-core "0.5.13"]
                 [metosin/reitit-schema "0.5.13"]
                 [metosin/reitit-ring "0.5.13"]
                 [metosin/reitit-middleware "0.5.13"]
                 [metosin/spec-tools "0.10.5"] ; for reitit-middleware

                 ; Namespace finder library
                 [bultitude "0.2.8"] ; noir requires 0.2.0
                 [org.tcrawley/dynapath "1.0.0"] ; bultitudes requires 0.2.3, but midje needs 1.0.0, should be compatible

                 ; MongoDB driver
                 ;; 3.x should be compatible with monger, 3.12 is also tested as being MongoDB 5.0 compatible
                 [org.mongodb/mongodb-driver "3.12.11"]
                 [com.novemberain/monger "3.5.0" :exclusions [com.google.guava/guava]]

                 ; S3 Library
                 [com.amazonaws/aws-java-sdk-s3 "1.12.266"]

                 ; Define explicit version of guava for compatibility
                 [com.google.guava/guava "31.1-jre"]
                 ; GCS Library
                 [com.google.cloud/google-cloud-storage "2.6.0" :exclusions [[com.google.guava/guava]]]
                 [fi.lupapiste/pubsub-client "2.5.1"]

                 ; UUID Library
                 [danlentz/clj-uuid "0.1.7"]

                 ; Logging
                 [com.taoensso/timbre "5.2.1"]
                 [org.apache.logging.log4j/log4j-to-slf4j "2.19.0"]
                 [org.slf4j/slf4j-api "1.7.36"]
                 [org.slf4j/jcl-over-slf4j "1.7.36"]
                 [org.slf4j/jul-to-slf4j "1.7.36"]
                 [org.slf4j/log4j-over-slf4j "1.7.36"]
                 [ch.qos.logback/logback-classic "1.2.11" :exclusions [org.slf4j/slf4j-api]]

                 ; markup processing
                 [enlive "1.1.6"]
                 [com.vladsch.flexmark/flexmark "0.62.2"]
                 [com.vladsch.flexmark/flexmark-ext-tables "0.62.2"]
                 [com.vladsch.flexmark/flexmark-ext-autolink "0.62.2"]
                 [cljstache "2.0.1"]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.5.2"]
                 [hiccup "1.0.5"]

                 ;; Html email templates
                 [selmer "1.12.33"]

                 ; CSS
                 [garden "1.3.3"]

                 ; Encryption and secure hashing
                 [org.jasypt/jasypt "1.9.3"]
                 [org.mindrot/jbcrypt "0.4"]
                 [pandect "0.6.1"]
                 [lupapiste/crypto "0.1.1"]

                 ; JSON
                 [metosin/jsonista "0.3.4"]
                 [cheshire "5.8.1"]                         ; not used directly, but omitting seems to break everything

                 [luposlip/json-schema "0.1.8"]

                 ; HTTP client
                 [clj-http "3.4.1" :exclusions [commons-codec]]
                 [hato "0.9.0"]

                 ; Email client
                 [com.draines/postal "2.0.4" :exclusions [commons-codec/commons-codec]]

                 ; iCalendar
                 [org.mnode.ical4j/ical4j "1.0.7" :exclusions [commons-logging]]

                 ; Apache Commons
                 [org.apache.commons/commons-lang3 "3.12.0"]
                 [commons-io "2.11.0"]
                 ; only some of depedencies use commons-codec, it's mostly replaced by java.util.Base64
                 [commons-codec "1.15"]
                 [commons-discovery "0.5"]

                 ; Joda time wrapper
                 [clj-time "0.15.2"]

                 ; Country code manipulation
                 [iso-country-codes "1.0"]

                 ; String case manipulation
                 [camel-snake-kebab "0.4.2"]

                 ; Collection of arrow macros
                 [swiss-arrows "1.0.0"]

                 ; File system lib
                 [me.raynes/fs "1.4.6" :exclusions [org.apache.commons/commons-compress]] ; later version required by pantomime -> tika
                 [babashka/fs "0.2.15"]

                 ; Enhanced try and throw
                 [slingshot "0.12.2"]

                 ; A Clojure(Script) library for declarative data description and validation
                 [prismatic/schema "1.1.12"]
                 [prismatic/schema-generators "0.1.3"]

                 ; MIME type resolution
                 [org.apache.tika/tika-core "2.3.0"]
                 [com.novemberain/pantomime "2.11.0" :exclusions [org.apache.tika/tika-parsers]]

                 ; RSS
                 [clj-rss "0.2.3"]

                 ;Money, handling and keeping things clean
                 [clojurewerkz/money "1.10.0"]

                 ; MS Office document processing
                 [dk.ative/docjure "1.18.0"]

                 ; JavaScript and CSS compression
                 [com.yahoo.platform.yui/yuicompressor "2.4.8" :exclusions [rhino/js org.mozilla/rhino]] ; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523

                 ;; Geo location libs
                 ; WKT parser
                 [cljts "0.3.0-20150228.035522-2" :exclusions [xerces/xercesImpl]]
                 ; Coordinate conversions, shape file handling etc.
                 [org.geotools/gt-main "19.3"]
                 [org.geotools/gt-shapefile "19.3"]
                 [org.geotools/gt-geojson "19.3"]
                 [org.geotools/gt-referencing "19.3"]
                 [org.geotools/gt-epsg-wkt "19.3"]

                 ; Message Queue
                 [org.apache.activemq/artemis-jms-client "2.6.2"]
                 [lupapiste/jms-client "0.4.1"]
                 [com.taoensso/nippy "3.1.1" :exclusions [org.clojure/tools.reader]]

                 ;; Lupapiste libraries
                 ; Shared domain code (https://github.com/lupapiste/commons)
                 [lupapiste/commons "4.1.1"
                  :exclusions [org.clojure/core.memoize
                               prismatic/schema
                               commons-codec
                               org.clojure/data.priority-map]]
                 [lupapiste/invoice-commons "0.1.1" :exclusions [prismatic/schema]]
                 ; Smoke test lib (https://github.com/lupapiste/mongocheck)
                 [lupapiste/mongocheck "0.1.5"]
                 ; Wrapper for clj-pdf for PDF/A document generation
                 [lupapiste/pdfa-generator "1.1.0" :exclusions [org.clojure/tools.reader
                                                                xalan
                                                                org.slf4j/slf4j-log4j12]]
                 ; JMX-server with socket reuse
                 [lupapiste/jmx-server "0.1.0"]

                 ;; SAML 2.0 -support
                 ;; consider metabase/saml20-clj?
                 [kirasystems/saml20-clj "0.1.16" :exclusions [org.apache.santuario/xmlsec]]
                 [org.apache.santuario/xmlsec "2.3.0"]



                 ;; XML Envelope signatures
                 [org.apache.axis2/axis2-kernel "1.8.0"]
                 [org.apache.axis2/axis2-saaj "1.8.0" :exclusions [org.apache.logging.log4j/log4j-jcl]]

                 [org.apache.ws.security/wss4j "1.6.19"]
                 [org.glassfish/javax.xml.rpc "3.1.1"]

                 ; Tiedonohjaus classes use jaxb
                 [org.glassfish.jaxb/jaxb-runtime "2.4.0-b180830.0438"]

                 ;; For SFTP
                 [com.raspasov/clj-ssh "0.5.12"]

                 ;; Fast Excel
                 [org.dhatim/fastexcel "0.10.12"]
                 ;; shadow-cljs is very often several versions ahead on the com.google.javascript/closure-compiler-unshaded version
                 ;; Make sure the thheller/shadow-cljs version is picked over the version preferred by org.clojure/clojurescript.
                 [org.clojure/clojurescript "1.11.60"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library
                               org.clojure/google-closure-library-third-party]]

                 ;; Rum also used in backend
                 [rum "0.12.6" :exclusions [cljsjs/react cljsjs/react-dom]]]

  :plugins [[lein-shell "0.5.0"]
            [lein-pdo "0.1.1"]
            [lupapiste/lein-midje "3.2.2"]
            [jonase/eastwood "0.2.3" :exclusions [org.clojure/tools.namespace org.clojure/clojure]]
            [lupapiste/lein-buildid "0.4.2"]
            [lupapiste/lein-nitpicker "0.6.0"]
            [mvxcvi/whidbey "2.2.0"]]
  :middleware [whidbey.plugin/repl-pprint]

  :clean-targets ^{:protect false} ["resources/public/lp-static/js/"
                                    :target-path]
  :source-paths ["src" "src-cljc"]
  :java-source-paths ["java-src"]
  :javac-options ["-source" "11" "-target" "11"]
  :test-paths ["test-utils" "test"]
  :profiles {:dev      {:dependencies   [[midje "1.10.5"]
                                         [ring/ring-mock "0.4.0" :exclusions [ring/ring-codec]]
                                         [org.apache.activemq/artemis-jms-server "2.6.0"]
                                         [rhizome "0.2.7"]
                                         [pdfboxing "0.1.14"]]
                        :resource-paths ["dev-resources"]
                        :source-paths   ["dev-src"]
                        :jvm-opts       ["-Djava.awt.headless=true" "-Xmx2G" "-Dfile.encoding=UTF-8"]
                        :eastwood       {:continue-on-exception true
                                         :source-paths          ["src"]
                                         :test-paths            []}}
             :uberjar  {:main          lupapalvelu.main
                        :prep-tasks    ["javac" "compile"]
                        ;; the default will also wipe JS files from lp-static/js (by design)
                        ;; but when building uberjar we don't want that to happen
                        :clean-targets ^:replace [:target-path]}
             :itest    {:test-paths ^:replace ["test-utils" "itest"]}
             :stest    {:test-paths ^:replace ["test-utils" "stest"]}
             :alltests {:source-paths ["test" "itest" "stest"]
                        :jvm-opts     ["-Djava.awt.headless=true" "-Xmx1G"]}
             :intellij {:source-paths ["src-cljs"]
                        :test-paths ["test-utils" "itest" "stest"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=https://www-dev.lupapiste.fi" "-Djava.awt.headless=true"]}
             :lupatest {:jvm-opts ["-Dtarget_server=https://www-test.lupapiste.fi" "-Djava.awt.headless=true"]}
             :shadow   {:source-paths ^:replace ["src-cljs" "src-cljc" "dev-src-cljs"]
                        :aot          ^:replace []
                        :dependencies [[thheller/shadow-cljs "2.20.7"]
                                       [cljs-ajax "0.8.4"]
                                       [com.google.javascript/closure-compiler-unshaded "v20220803"]
                                       [org.clojure/google-closure-library "0.0-20211011-0726fdeb"]
                                       [org.clojure/google-closure-library-third-party "0.0-20211011-0726fdeb"]

                                       [reagent "1.1.1" :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
                                       [re-frame "1.3.0" :exclusions [reagent]]
                                       [re-frame-utils "0.1.0"]
                                       [com.7theta/re-frame-fx "0.2.1" :exclusions [re-frame]]
                                       [org.clojure/tools.reader "1.3.5"] ; edn reader for CLJS
                                       [com.andrewmcveigh/cljs-time "0.5.2"]
                                       [metosin/reitit-core "0.5.18"]
                                       [metosin/reitit-frontend "0.5.18"]
                                       [metosin/reitit-spec "0.5.18"]
                                       [metosin/reitit-schema "0.5.18"]
                                       [funcool/promesa "9.0.489"]

                                       [binaryage/devtools "1.0.3"]]}}
  :jvm-opts ["-Dfile.encoding=UTF-8"]
  :nitpicker {:exts     ["clj" "js" "html"]
              :excludes [#"jquery" #"underscore" #"terms\.html" #"\/email-templates\/" #"proj4" #".debug" #"lp-static/js/"]}
  :repositories [["osgeo" {:url "https://repo.osgeo.org/repository/release/"}]]
  :aliases {"integration"          ["midje"] ;; Called by CI job that sets other params
            "integration-parallel" ["trampoline" "run" "-m" "lupapalvelu.itest-main"]
            "itest"                ["with-profile" "dev,itest" "midje"]
            "stest"                ["with-profile" "dev,stest" "midje"]
            "verify"               ["with-profile" "dev,alltests" "do" "nitpicker," "midje"]
            "sass"                 ["shell" "npm" "run" "sass:prod:once"]
            "front"                ["do"
                                    ["clean"]
                                    ["shell" "npm" "run" "symlink-map"]
                                    ["pdo"
                                     ["shell" "npm" "run" "sass:dev:watch"]
                                     ["shell" "npx" "shadow-cljs" "watch" "front"]]]
            "front:prod"           ["do"
                                    ["shell" "npm" "run" "sass:prod:once"]
                                    ["shell" "npx" "shadow-cljs" "release" "front"]]}
  :aot [lupapalvelu.main clj-time.core]
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns user
                 :timeout 220000}
  :pom-plugins [[org.fusesource.mvnplugins/maven-graph-plugin "1.4"]
                [com.googlecode.maven-overview-plugin/maven-overview-plugin "1.6"]]
  :min-lein-version "2.5.0")
