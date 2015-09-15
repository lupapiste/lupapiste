LUPAPISTE.IndicatorModel = function() {
  "use strict";
  var self = this;

  self.showIndicator = ko.observable().extend({notify: "always"});
  self.indicatorStyle = ko.observable("neutral");
  var message = ko.observable("");
  var timerId;

  self.showCloseButton = ko.pureComputed(function() {
    return self.indicatorStyle() === "negative";
  });

  self.iconStyle = ko.pureComputed(function() {
    if (self.indicatorStyle() === "positive") {
      return "lupicon-circle-check";
    }
    else if (self.indicatorStyle() === "negative") {
      return "lupicon-circle-attention";
    }
  });

  self.indicatorMessage = ko.pureComputed(function() {
    if (message()) {
      return message();
    }
    else if (self.indicatorStyle() === "positive") {
      return "form.saved";
    }
    else if (self.indicatorStyle() === "negative") {
      return "form.err";
    }
  });

  self.showIndicator.subscribe(function(val) {
    if (self.indicatorStyle() === "negative" && timerId) {
      // stop timer if indicator was set negative during positive indicator hide was delayed
      clearTimeout(timerId);
      timerId = undefined;
    } else if (val && self.indicatorStyle() !== "negative") {
      // automatically hide indicator if shown and not negative
      timerId = _.delay(function() {
        self.showIndicator(false);
        timerId = undefined;
      }, 2000);
    }
  });

  self.closeIndicator = function() {
    self.showIndicator(false);
  };

  hub.subscribe("indicator", function(e) {
    message(e.message);
    self.indicatorStyle(e.style);
    self.showIndicator(true);
  });
};
