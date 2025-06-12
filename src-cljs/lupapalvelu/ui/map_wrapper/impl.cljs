(ns lupapalvelu.ui.map-wrapper.impl
  "Wrapper to use shadow-lazy/loadable to load the JS module"
  (:require ["@cloudpermit/map-component/dist/application-wrappers/lupapiste/ApplicationMap" :rename {ApplicationMap A}]))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(def ApplicationMap A)
