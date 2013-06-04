var features = (function() {
  "use strict";

  function FeaturesModel() {
    var self = this;

    self.data = ko.observable({});

    self.enabled = function(path) {
      return self.data && self.data()[path];
    };

    self.refresh = function() {
      ajax.query("features")
        .success(function(d) {
          var features = _.map(_.filter(d.features, function(feature) { return feature[1]; }),
                          function(feature) { return feature[0].join("."); });
          var asMap = _.reduce(features,function(m,f) { m[f] = true; return m;} ,{});
          self.data(asMap);
        })
        .error(function(e) {
          self.data({});
          debug("Could not load features", e);
        })
        .call();
    };
  }

  var model = new FeaturesModel();
  model.refresh();

  return model;

})();
