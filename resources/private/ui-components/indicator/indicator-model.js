LUPAPISTE.IndicatorModel = function(params) {
  "use strict";
  var self = this;

  self.showIndicator = ko.observable().extend({notify: "always"});
  self.indicatorStyle = ko.observable("neutral");
  self.iconStyle = ko.observable("");
  self.message = ko.observable("");

  hub.subscribe("indicator", function(e) {
    if (e.style === "positive") {
      self.iconStyle("lupicon-circle-check");
      self.message("form.saved");
    }
    else if (e.style === "negative") {
      self.iconStyle("lupicon-circle-attention");
      self.message("form.err");
    }

    self.indicatorStyle(e.style);
    self.showIndicator(true);
    _.delay(function() {
      self.showIndicator(false);
    }, 1600);

  });
};
