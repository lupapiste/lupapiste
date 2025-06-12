var features = (function() {
  "use strict";

  function FeaturesModel() {
    var self = this;

    self.data = ko.observable({});

    self.enabled = function(path) {
      return self.data && self.data()[path];
    };

    // TODO: this seems unused?
    self.refresh = function() {
      ajax.query("features")
        .success(function(d) {
          self.data(d.features);
          hub.send("features-loaded");
        })
        .error(function(e) {
          self.data({});
          debug("Could not load features", e);
        })
        .call();
    };

    self.cljsDocgenInitialState = function() {
      var toggleValue = localStorage.getItem("cljsDocgenEnabled");
      return toggleValue ? toggleValue === "true" : self.enabled("enable-cljs-docgen");
    };
  }

  var model = new FeaturesModel();
  model.data(LUPAPISTE.config.features);

  return model;

})();
