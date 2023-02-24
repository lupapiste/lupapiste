;(function() {
  "use strict";

  function FeaturesModel() {
    var self = this;

    self.features = ko.observableArray([]);
    self.pending = ko.observable();
    
    self.load = function() {
      ajax
        .query("features")
        .pending(self.pending)
        .success(function(d) { self.features(d.features); })
        .call();
    };
  }

  var featuresModel = new FeaturesModel();

  hub.onPageLoad("features", featuresModel.load);

  $(function() {
    $("#features").applyBindings(featuresModel);
  });

})();
