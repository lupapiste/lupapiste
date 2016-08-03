// Bar indicator that is shown below the header.
// Indicator is triggered with hub.send( 'indicator', params),
// where params are: [optional]
// style: indicator style. Can be positive, negative or primary.
// [message]: ltext or lhtml to be shown. Negative (form.err) and
// positive (form.saved) have default ltexts.
// [html]: If true then the message is interpreted as lhtml (default false)
// [sticky]: Usually indicator fades out after timeout. If sticky is true
// then the indicator is visible until closed by the user.
LUPAPISTE.IndicatorModel = function() {
  "use strict";
  var self = this;

  self.showIndicator = ko.observable(false).extend({notify: "always"});
  self.indicatorStyle = ko.observable("neutral");
  self.indicatorMessage = ko.observable();
  // keep indicator on screen
  self.sticky = ko.observable(false);

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
    var msg = loc( e.message  || defaultMessages[e.style] );
    self.indicatorMessage( e.html ? msg : _.escape( msg ));
    self.indicatorStyle(e.style);
    self.sticky(!!e.sticky);
    self.showIndicator(true);
  });
};
