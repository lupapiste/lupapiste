// Rollup button with status icon and style.
// Parameters [optional]:
// [status]: Either ok or requires_user_action
// [style]: Default CSS object.
// [okClass]: Button class when status is ok (default positive).
// [rejectedClass]: Button class when status is requires_user_action
// (no default).
// text or ltext: Button text directly or via localization key.
// [extraText]: Optional text next to indicator.
LUPAPISTE.RollupStatusButtonModel = function( params ) {
  "use strict";
  var self = this;
  var APPROVED = "ok";
  var REJECTED = "requires_user_action";

  ko.utils.extend (self, new LUPAPISTE.ComponentBaseModel() );

  self.params = params;
  self.status = params.status;
  self.showIndicators = params.showIndicators;
  self.text = params.ltext ? loc( params.ltext ) : ko.unwrap(params.text);
  self.extraText = params.extraText;

  var okClass = params.okClass || "positive";

  self.isApproved = self.disposedPureComputed( function() {
    return self.status() === APPROVED;
  });

  self.isRejected = self.disposedPureComputed( function() {
    return self.status() === REJECTED;
  });

  self.statusStyles = self.disposedPureComputed( function() {
    if (self.showIndicators) {
      var css = params.style ? _.clone( params.style ) : {};
      _.set( css, okClass, self.isApproved );
      if( params.rejectedClass ) {
        _.set( css, params.rejectedClass, self.isRejected );
      }
      return css;
    }
    return params.style;
  });

  self.statusLtext = self.disposedPureComputed( function() {
    if( self.isApproved() ) {
      return "approved";
    }
    if( self.isRejected() ) {
      return "verdict.status.hylatty";
    }
  });
};
