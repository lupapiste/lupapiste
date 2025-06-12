(defproject lupapalvelu "0.1.0-SNAPSHOT"
  :description "Lupapiste permit service"
  :url "https://www.lupapiste.fi"
  :license {:name         "European Union Public Licence v. 1.2"
            :url          "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"
            :distribution :manual}
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/data.codec "0.2.1"]
                 [org.clojure/data.csv "1.1.1"]
                 [org.clojure/data.zip "1.1.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/core.async "1.9.865"]
                 [org.clojure/core.memoize "1.2.281"]
                 [org.clojure/core.match "1.1.1"]
                 [org.clojure/test.check "1.1.3"]
                 [org.clojure/tools.macro "0.2.2"]
                 [com.cognitect/transit-clj "1.1.357"]

                 ;; State management
                 [integrant "1.0.1"]

                 ;; Web frameworks
                 [ring "1.13.0" :exclusions [commons-fileupload org.clojure/tools.reader]]
                 [noir "1.3.0" :exclusions [compojure clj-stacktrace org.clojure/tools.macro ring hiccup bultitude]] ; last release 2012
                 [compojure "1.7.1" :exclusions [org.clojure/tools.macro ring]] ; force noir to use newer version
                 [metosin/ring-swagger "1.1.0"]
                 [cheshire "6.2.0"] ; force ring-swagger to use newer version
                 [metosin/ring-swagger-ui "5.32.1"]
                 [metosin/reitit-core "0.10.1"]
                 [fi.metosin/reitit-openapi "0.10.0"]
                 [metosin/reitit-schema "0.10.1"]
                 [metosin/reitit-malli "0.10.1"]
                 [metosin/reitit-ring "0.10.1"]
                 [metosin/reitit-middleware "0.10.1"]
                 [metosin/reitit-swagger "0.10.1"]
                 [metosin/reitit-swagger-ui "0.10.1"]

                 ;; Namespace finder library
                 [bultitude "0.2.8"] ; noir requires 0.2.0, last release 2015
                 [org.tcrawley/dynapath "1.0.0"] ; bultitudes requires 0.2.3, but midje needs 1.0.0, should be compatible, last release 2017

                 ;; MongoDB driver
                 ;; 3.x should be compatible with monger, 3.12 is also tested as being MongoDB 5.0 compatible
                 [org.mongodb/mongodb-driver "3.12.14"]
                 [com.novemberain/monger "3.6.0" :exclusions [com.google.guava/guava]] ; last release 2022

                 ;; GCS Library
                 [com.google.cloud/google-cloud-storage]
                 [commons-codec "1.17.1"] ; force GCS to use newer version
                 [fi.lupapiste/pubsub-client "2.5.3"]

                 ;; Logging
                 [com.taoensso/timbre "6.8.0"]
                 [viesti/timbre-json-appender "0.2.14"]
                 [org.apache.logging.log4j/log4j-to-slf4j "2.25.3"]
                 [org.slf4j/slf4j-api]
                 [org.slf4j/jcl-over-slf4j]
                 [org.slf4j/jul-to-slf4j]
                 [org.slf4j/log4j-over-slf4j]
                 [com.fzakaria/slf4j-timbre "0.4.1"]

                 ;; markup processing
                 ;; we only use the tagsoup parser so jsoup can be excluded completely
                 [enlive "1.1.6" :exclusions [org.jsoup/jsoup]] ; last release 2015 but used extensively
                 [com.vladsch.flexmark/flexmark "0.64.8"]
                 [com.vladsch.flexmark/flexmark-ext-tables "0.64.8"]
                 [com.vladsch.flexmark/flexmark-ext-autolink "0.64.8"]
                 [cljstache "2.0.6"]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.5.2"] ; last release 2011
                 [hiccup "1.0.5"]

                 ;; Html email templates
                 [selmer "1.12.61"]

                 ;; CSS
                 [com.lambdaisland/garden "1.5.569"]

                 ;; Encryption and secure hashing
                 [org.jasypt/jasypt "1.9.3"] ; last release 2019
                 [org.mindrot/jbcrypt "0.4"] ; last release 2017
                 [pandect "1.0.2"] ; last release 2021
                 [org.bouncycastle/bcprov-jdk18on "1.78.1"]
                 [com.nulab-inc/zxcvbn "1.9.0"]

                 ;; JSON
                 [metosin/jsonista "1.0.0"]

                 [luposlip/json-schema "0.4.6"]

                 ;; HTTP client
                 [clj-http "3.13.1" :exclusions [commons-codec]]
                 [hato "1.0.0"]

                 ;; Email validation, provides javax.mail packages
                 ;; When updating make sure non-ASCII characters (åäö) still work e.g. in `sade.validators/valid-email?`
                 [com.sun.mail/jakarta.mail "1.6.7"]

                 ;; Apache Commons
                 [org.apache.commons/commons-lang3 "3.18.0"]
                 [commons-io "2.17.0"]
                 [commons-discovery "0.5"] ; last release 2011

                 ;; Parallellism
                 [org.clj-commons/claypoole "1.2.2"]

                 ;; Country code manipulation
                 [iso-country-codes "1.0"] ; last release 2013

                 ;; String case manipulation
                 [camel-snake-kebab "0.4.3"] ; last release 2022

                 ;; Collection of arrow macros
                 [swiss-arrows "1.0.0"] ; last release 2013

                 ;; File system and command line parsing
                 [babashka/fs "0.5.33"]
                 [org.babashka/cli "0.8.67"]

                 ;; Enhanced try and throw
                 [slingshot "0.12.2"] ; last release 2015

                 ;; A Clojure(Script) library for declarative data description and validation
                 [prismatic/schema "1.4.1"] ; last release 2022
                 [prismatic/schema-generators "0.1.5"] ; last release 2022
                 [metosin/schema-tools "0.13.1"]

                 [metosin/malli "0.20.1"]

                 ;; MIME type resolution
                 [com.novemberain/pantomime "2.11.0" ; last release 2019
                  :exclusions [org.apache.tika/tika-parsers]]
                 [org.apache.tika/tika-core "3.2.3"] ; force pantomime to use newer version
                 [org.apache.commons/commons-compress "1.27.1"] ; force pantomime to use newer version

                 ;; MS Office document processing
                 [dk.ative/docjure "1.19.0"] ; last release 2022

                 ;; JavaScript and CSS compression
                 ;; http://jira.xwiki.org/browse/XWIKI-6148?focusedCommentId=59523#comment-59523
                 [com.yahoo.platform.yui/yuicompressor "2.4.8" :exclusions [rhino/js org.mozilla/rhino]] ; last release 2014

                 ;; Geo location libs
                 ;; WKT parser
                 [cljts "0.3.0-20150228.035522-2" :exclusions [xerces/xercesImpl]] ; last release 2015
                 ;; Coordinate conversions, shape file handling etc.
                 [org.geotools/gt-main "19.3"]
                 [org.geotools/gt-shapefile "19.3"]
                 [org.geotools/gt-geojson "19.3"]
                 [org.geotools/gt-referencing "19.3"]
                 [org.geotools/gt-epsg-wkt "19.3"]

                 ;; AD Login
                 [com.taoensso/nippy "3.1.1" :exclusions [org.clojure/tools.reader]]

                 ;; Lupapiste libraries
                 ;; Shared domain code (https://github.com/lupapiste/commons)
                 [lupapiste/commons "5.3.31"
                  :exclusions [org.clojure/core.memoize
                               prismatic/schema
                               commons-codec
                               org.clojure/data.priority-map]]
                 ;; Wrapper for clj-pdf for PDF/A document generation
                 [lupapiste/pdfa-generator "1.1.1" :exclusions [org.clojure/tools.reader
                                                                xalan
                                                                ;; We use bcprov-jdk18on
                                                                org.bouncycastle/bcprov-jdk15on]]

                 ;; SAML 2.0 -support
                 ;; consider metabase/saml20-clj?, last release 2020
                 [kirasystems/saml20-clj "0.1.16" :exclusions [org.apache.santuario/xmlsec
                                                               org.bouncycastle/bcprov-jdk15on
                                                               commons-collections
                                                               org.owasp.esapi/esapi]]
                 ;; required by opensaml, use newer version
                 [org.owasp.esapi/esapi "2.6.0.0" :exclusions [[commons-configuration]
                                                               [commons-beanutils/commons-beanutils-core]
                                                               [commons-fileupload]
                                                               [commons-io]
                                                               [commons-collections]
                                                               [log4j]
                                                               [xom]
                                                               [org.beanshell/bsh-core]
                                                               [org.owasp.antisamy/antisamy]]]
                 [org.apache.santuario/xmlsec "4.0.2"]

                 ;; XML Envelope signatures
                 [commons-fileupload "1.6.0"]
                 [org.apache.axis2/axis2-kernel "1.8.2"] ; last release 2022
                 [org.apache.axis2/axis2-saaj "1.8.2" :exclusions [org.apache.logging.log4j/log4j-jcl ; last release 2022
                                                                   commons-fileupload]]

                 [org.apache.ws.security/wss4j "1.6.19"] ; last release 2015
                 [org.glassfish/javax.xml.rpc "3.1.1"] ; last release 2011

                 ;; Tiedonohjaus, RSS, and SAP SBO features use JAXB
                 [org.glassfish.jaxb/jaxb-runtime "4.0.7"]
                 [jakarta.activation/jakarta.activation-api "2.1.4"]

                 ;; For SFTP
                 ;; Note: also provides a relatively new net.java.dev.jna/jna, compatible with Macos
                 [org.clj-commons/clj-ssh "0.6.6"]

                 ;; Fast Excel
                 [org.dhatim/fastexcel "0.18.4"]
                 [org.dhatim/fastexcel-reader "0.18.4"]

                 ;; Rum also used in backend
                 [rum "0.12.11" :exclusions [cljsjs/react cljsjs/react-dom]]

                 ;; Elasticsearch:
                 ;; Use newer version of core.async
                 [cc.qbits/spandex "0.8.2" :exclusions [cheshire org.clojure/core.async]]]

  ;; Large projects often publish multiple libraries/artifacts, which ought to be kept in sync, so that we don't
  ;; accidentally mix and match incompatible versions. To avoid the need to manually specify versions for each artifact,
  ;; some projects offer a Bill-of-Materials (BOM), which specifies which versions are compatible with each other. With
  ;; a BOM we don't need to worry about some dependency accidentally importing an incompatible version of a particular
  ;; library as the BOM will override the version. But of course this also works the other way around; the BOM will also
  ;; override if some dependency requires a newer version.

  ;; Note: Adding a BOM here only locks in the version numbers; which libraries are actually included in the project
  ;; is still handled with `:dependencies`. For top-level dependencies you can omit the version number.
  :bom {:import [[com.fasterxml.jackson/jackson-bom "2.21.2"]
                 ;; See https://cloud.google.com/java/docs/bom
                 ;; Note: `libraries-bom` specifies _a lot_ of libraries, it takes a minute or two for Leiningen
                 ;; to fetch the definitions for the first time (and again every time this is updated).
                 [com.google.cloud/libraries-bom "26.76.0"]
                 [org.slf4j/slf4j-bom "2.0.17"]]}

  :plugins [[lein-bom "0.2.0-SNAPSHOT"] ; last release 2019
            [lein-shell "0.5.0"] ; last release 2015
            [lupapiste/lein-midje "3.2.2"]
            [lupapiste/lein-buildid "0.4.2"]
            [lupapiste/lein-nitpicker "0.6.0"]]

  :source-paths ["src" "src-cljc"]
  :java-source-paths ["java-src"]
  :javac-options ["-source" "21" "-target" "21"]
  :test-paths ["test-utils" "test"]
  :profiles {:dev      {:dependencies   [[midje "1.10.10"]
                                         [nubank/matcher-combinators "3.10.0"]
                                         [ring/ring-mock "0.4.0" :exclusions [ring/ring-codec]] ; last release 2019
                                         [integrant/repl "0.5.0"]
                                         [rhizome "0.2.9"] ; last release 2017
                                         [pdfboxing "0.1.14"] ; last release 2019
                                         ;; For Ryhti OpenAPI to Malli
                                         [fipp/fipp "0.6.29"]]
                        :resource-paths ["dev-resources"]
                        :source-paths   ["dev-src"]
                        :jvm-opts       ["-Djava.awt.headless=true" "-Xmx2G" "-Dfile.encoding=UTF-8"]}
             :repl     {:test-paths ["itest-utils"]}
             :uberjar  {:main          lupapalvelu.main
                        :prep-tasks    ["javac" "compile"]}
             :itest    {:test-paths ^:replace ["test-utils" "itest-utils" "itest"]}
             :stest    {:test-paths   ^:replace ["test-utils" "itest-utils" "stest"]
                        :source-paths ["dev-src"]}
             :alltests {:source-paths ["test" "itest" "stest"]
                        :jvm-opts     ["-Djava.awt.headless=true" "-Xmx1G"]}
             :intellij {:source-paths ["src-cljs"]
                        :test-paths   ["test-utils" "itest" "stest"]}
             :lupadev  {:jvm-opts ["-Dtarget_server=https://www-dev.lupapiste.fi" "-Djava.awt.headless=true"]}
             :lupatest {:jvm-opts ["-Dtarget_server=https://www-test.lupapiste.fi" "-Djava.awt.headless=true"]}
             :shadow   {:source-paths ^:replace ["src-cljs" "src-cljc" "dev-src-cljs"]
                        :aot          ^:replace []
                        :dependencies [[thheller/shadow-cljs "3.4.4"]
                                       [cljs-ajax "0.8.4"] ; last release 2021
                                       [cljs-bean "1.9.0"]
                                       [reagent "2.0.1" :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
                                       [re-frame "1.4.3" :exclusions [reagent]]
                                       [day8.re-frame/test "0.1.5"]
                                       [com.7theta/re-frame-fx "0.2.1" :exclusions [re-frame]] ; last release 2018
                                       [org.clojure/tools.reader "1.6.0"] ; edn reader for CLJS
                                       [com.cognitect/transit-cljs "0.8.280"]
                                       [com.andrewmcveigh/cljs-time "0.5.2"] ; last release 2017
                                       [metosin/reitit-frontend "0.10.1"]
                                       [metosin/reitit-spec "0.10.1"]
                                       [funcool/promesa "11.0.678"]
                                       [binaryage/devtools "1.0.7"]
                                       ;; exclude old re-com 2.8.0 from 2020
                                       [re-frisk "1.7.1" :exclusion [re-com]]
                                       [metosin/reagent-dev-tools "1.2.0"]
                                       [re-com "2.24.1"]]}}
  ;; Not necessary anymore, as Java 18+ (See JEP 400) uses UTF-8 by default
  :jvm-opts ["-Dfile.encoding=UTF-8"]
  :nitpicker {:exts     ["clj" "js" "html"]
              :excludes [#"jquery" #"underscore" #"terms\.html" #"\/email-templates\/" #"proj4" #".debug" #"lp-static/js/"]}
  :repositories [["osgeo" {:url "https://repo.osgeo.org/repository/release/"}]]
  :aliases {"integration" ["midje"] ;; Called by CI job that sets other params
            "itest"       ["with-profile" "dev,itest" "midje"]
            "stest"       ["with-profile" "dev,stest" "midje"]
            "verify"      ["with-profile" "dev,alltests" "do" "nitpicker," "midje"]
            "sass"        ["shell" "npm" "run" "sass:prod:once"]
            "front"       ["shell" "npx" "shadow-cljs" "clj-run" "lupafront.dev/watch"]
            "front:prod"  ["do"
                           ["shell" "npm" "run" "sass:prod:once"]
                           ["shell" "npm" "run" "tailwind:prod:once"]
                           ["shell" "npx" "shadow-cljs" "release" "front"]]
            "front:test"  ["do"
                           ["with-profile" "shadow" "run" "-m" "shadow.cljs.devtools.cli" "--npm" "compile" "test"]
                           ["shell" "node" "out/node-tests.js"]]}
  :aot [lupapalvelu.main clj-time.core]
  :main ^:skip-aot lupapalvelu.server
  :repl-options {:init-ns user}
  :min-lein-version "2.5.0")
