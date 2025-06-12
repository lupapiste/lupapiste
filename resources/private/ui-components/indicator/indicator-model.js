// Bar indicator that is shown below the header. Serves indicators as queue.
// Indicator is triggered with hub.send( 'indicator', params),
// where params are: [optional]
// style: indicator style. Can be positive, negative or primary.
// [message]: ltext or lhtml to be shown. Negative (form.err) and
// positive (form.saved) have default ltexts.
// [rawMessage]: alternative for message, which is not localized
// [html]: If true then the message is interpreted as lhtml (default false)
// [sticky]: Usually indicator fades out after timeout. If sticky is true
// then the indicator is visible until closed by the user.
LUPAPISTE.IndicatorModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.indicators = ko.observableArray([]).extend({deferred: true});

  self.showIndicator = self.disposedPureComputed(function() {
    return self.indicators().length > 0;
  });

  var timerId;

  self.showCloseButton = function(item) {
    return item.sticky;
  };

  self.currentIndicator = self.disposedComputed(function() {
    var nonSticky = _.find(self.indicators(), function(m) { return !m.sticky; });
    if (nonSticky) {
      timerId = _.delay(function() {
        self.indicators.remove(nonSticky);
      }, 2000);
      return nonSticky;
    } else {
      var sticky = _.find(self.indicators(), function(m) { return m.sticky; });
      if (sticky) {
        clearTimeout(timerId);
        timerId = undefined;
      }
      return sticky;
    }
  });

  function getIconStyle(style) {
    return _.get( {positive: "lupicon-circle-check",
                   negative: "lupicon-circle-attention",
                   primary: "lupicon-circle-info"},
                  style);
  }

  self.closeIndicator = function(currentIndicator) {
    self.indicators.remove(currentIndicator);
    // Needed if html message contains link, for example.
    return true;
  };

  self.addHubListener( "indicator", function(e) {
    var defaultMessages = {negative: "form.err",
                           positive: "form.saved"};
    var isAlert = e.style === "negative";
    var msg = e.rawMessage ? e.rawMessage : loc(e.message  || defaultMessages[e.style]);
    var indicator = {style: e.style,
                     sticky: !!e.sticky || isAlert,
                     iconStyle: getIconStyle(e.style),
                     message:  e.html ? msg : _.escape( msg ),
                     attributes: isAlert ? {role: "alert"} : {"aria-live": "polite"}};

    // No sequential identical indicators.
    if( !_.isEqual( indicator, _.last ( self.indicators() ))) {
      self.indicators.push(indicator);
    }
  });

  self.addHubListener( "side-panel-esc-pressed", function() {
    self.closeIndicator( self.currentIndicator() );
  });
};
