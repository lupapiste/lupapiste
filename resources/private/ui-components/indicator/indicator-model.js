LUPAPISTE.IndicatorModel = function() {
  "use strict";
  var self = this;

  self.showIndicator = ko.observable(false).extend({notify: "always"});
  self.indicatorStyle = ko.observable("neutral");
  // keep indicator on screen
  self.sticky = ko.observable(false);

  // Message is lhtml.
  var html   = ko.observable( false );
  var message = ko.observable("");
  var timerId;

  self.showCloseButton = ko.pureComputed(function() {
    return self.indicatorStyle() === "negative" || self.sticky();
  });

  self.iconStyle = ko.pureComputed(function() {
    return _.get( {positive: "lupicon-circle-check",
                   negative: "lupicon-circle-attention",
                   primary: "lupicon-circle-info"},
                  self.indicatorStyle());
  });

  self.indicatorMessage = ko.pureComputed(function() {
    return html() ? message() : _.escape( message());
  });

  self.showIndicator.subscribe(function(val) {
    if (self.indicatorStyle() === "negative" && timerId) {
      // stop timer if indicator was set negative during positive indicator hide was delayed
      clearTimeout(timerId);
      timerId = undefined;
    } else if (val && self.indicatorStyle() !== "negative" && !self.sticky()) {
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
    var defaultMessages = {negative: "form.err",
                           positive: "form.saved"};
    message(loc( e.message  || defaultMessages[e.style] ));
    self.indicatorStyle(e.style);
    self.sticky(!!e.sticky);
    html( e.html);
    self.showIndicator(true);
  });
};
