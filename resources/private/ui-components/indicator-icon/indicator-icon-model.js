LUPAPISTE.IndicatorIconModel = function() {
  "use strict";
  var self = this;

  self.showIndicator = ko.observable(false).extend({notify: "always"});
  self.indicatorStyle = ko.observable("neutral");
  var message = ko.observable("");
  var waitTimerId;
  var showTimerId;

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
    if (self.indicatorStyle() === "negative" && showTimerId) {
      // stop timer if indicator was set negative during positive indicator hide was delayed
      clearTimeout(showTimerId);
      showTimerId = undefined;
    }
    if (val) {
      // automatically hide indicator, negative after 10 sec else 2 sec.
      showTimerId = _.delay(function() {
        self.showIndicator(false);
        showTimerId = undefined;
      }, self.indicatorStyle() === "negative" ? 10000 : 2000);
    }
  });

  hub.subscribe("indicator-icon", function(e) {
    if (e.clear) {
      clearTimeout(waitTimerId);
      waitTimerId = undefined;
    } else if(e.style === "negative") {
      clearTimeout(waitTimerId);
      waitTimerId = undefined;
      message(e.message);
      self.indicatorStyle(e.style);
      self.showIndicator(true);
    } else if(!waitTimerId) {
      waitTimerId = _.delay( function(e) {
        message(e.message);
        self.indicatorStyle(e.style);
        self.showIndicator(true);
        waitTimerId = undefined;
      }, 500, e);
    }
  });
};
