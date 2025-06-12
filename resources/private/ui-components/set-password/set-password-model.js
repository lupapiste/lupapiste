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
  self.showPasswords = ko.observable(false);

  function text(score) {
    return sprintf( "mypage.quality.%s", score );
  }

  function isValid() {
    return self.password1() &&
      util.isValidPasswordLength( self.password1() ) &&
      util.isValidPasswordQuality( self.passwordQuality() );
  }

  function matches() {
    return (self.password1() === self.password2());
  }

  self.passwordQuality = ko.observable("poor");

  self.disposedSubscribe(self.password1, function (pw) {
    util.getPwQualityAsync(pw, self.passwordQuality,
      {"email": params.email(),
       "firstName": params.firstName(),
       "lastName": params.lastName()});
  });

  self.qualityMessage = self.disposedComputed( function() {
    return isValid() && self.password1() ? text(self.passwordQuality()) : null;
  });

  self.qualityWarning = self.disposedComputed( function() {
    return isValid() || !self.password1() ? null : text(self.passwordQuality());
  });

  self.matchWarning = self.disposedComputed( function() {
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
