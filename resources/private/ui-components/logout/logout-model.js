// Logout functionality that nukes both Lupapiste and Suomi.fi sessions (if needed).
LUPAPISTE.LogoutModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.vetumaLogout = ko.observable( false );

  // Lupapiste logout
  self.logoutPath = self.disposedPureComputed( function() {
    return "/app/" + loc.getCurrentLanguage() + "/logout";
  });

  // First Lupapiste and then Suomi.fi logout.
  self.logoutBoth = function() {
    ajax.get( self.logoutPath() )
      .complete( util.identLogoutRedirect )
      .call();
  };

  // Initialization
  vetuma.getUser( _.wrap( true, self.vetumaLogout),
                  _.wrap( false, self.vetumaLogout),
                  _.noop);
};
