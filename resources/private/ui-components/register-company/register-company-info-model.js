LUPAPISTE.RegisterCompanyInfoModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.loggedIn = lupapisteApp.models.currentUser.id;

  self.userCell = self.loggedIn() ? "span" : "text";

  self.field = function( fieldName, cell ) {
    var opts = _.defaults( service.field( fieldName),
                           {cell: cell || "text"});
    return opts.required && cell === "span"
         ? _.set( opts, "required", false )
         : opts;
  };

  self.languageField = function() {
    return  _.defaults( service.field( "language"),
                        {cell: "select",
                         options: loc.getSupportedLanguages(),
                         optionsText: loc });
  };
  self.userInfo = self.disposedPureComputed( function() {
    var reg = service.registration;
    return sprintf( "%s %s, %s", reg.firstName(), reg.lastName(), reg.email());
  });

  var emailWarning = service.field( "email" ).warning;

  self.showLogin = self.disposedComputed( function() {
    return  !self.loggedIn() && emailWarning() === "email-in-use";
  });


  self.loginCallback = function() {
    pageutil.showAjaxWait();
    var user = lupapisteApp.models.currentUser;
    var redirectFn = function() {
      window.location =
        sprintf( "/app/%s/%s#!/%s",
                 user.language(),
                 user.isAuthority() ? "authority" : "applicant",
                 user.isAuthority() ? "applications" : "register-company-account-type");
    };
    if( user.isAuthority() ) {
      hub.subscribe( "dialog-close", redirectFn, true);
      hub.send( "show-dialog",
                {ltitle: "authority",
                 size: "small",
                 component: "ok-dialog",
                 componentParams: {
                   ltext: "register.company.existing-user-is-authority"
                 }});
    } else {
      service.save();
      redirectFn();
    }
  };
};
