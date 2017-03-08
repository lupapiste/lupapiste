// Password editor.
// Params:
// password: Observable that is filled with new valid password after
// successful editing.
LUPAPISTE.SetPasswordModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.password1 = ko.observable();
  self.password2 = ko.observable();

  function text() {
    return sprintf( "mypage.quality.%s", util.getPwQuality( self.password1()));
  }

  function isValid() {
    return self.password1() && util.isValidPassword( self.password1());
  }

  function matches() {
    return (self.password1() === self.password2());
  }

  self.qualityMessage = self.disposedComputed( function() {
    return isValid() && self.password1() ? text() : null;
  });

  self.qualityWarning = self.disposedComputed( function() {
    return isValid() || !self.password1() ? null : text();
  });

  self.matchWarning =self.disposedComputed( function() {
    return self.password2() && !matches() ? "setpw.notSame" : null;
  });

  // Guard is used to make sure that two input fields are not cleared
  // by accident, when the password is cleared.
  var guard = ko.observable();

  self.disposedComputed( function() {
    if( isValid() && matches()) {
      params.password( self.password1());
    } else {
      guard( true );
      params.password( "" );
      guard( false );
    }
  });

  self.disposedSubscribe( params.password, function( pw ) {
    if( !pw && !guard()) {
      self.password1("");
      self.password2("");
    }
});
};
