LUPAPISTE.RegisterCompanyInfoModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.loggedIn = lupapisteApp.models.currentUser.id;

  self.field = function( fieldName, cell ) {
    return _.defaults( service.field( fieldName),
                       {cell: cell || "text"});
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

  self.showLogin = self.disposedPureComputed( function() {
    return  !self.loggedIn() && emailWarning() === "email-in-use";
  });


  self.loginCallback = function() {
    pageutil.showAjaxWait(loc("register.autologin"));
    var user = lupapisteApp.models.currentUser;
    var isAuth = user.isAuthority();
    var isCom = user.company.id();
    var redirectFn = function() {
      window.location =
        sprintf( "/app/%s/%s#!/%s",
                 user.language(),
                 isAuth ? "authority" : "applicant",
                 (isAuth || isCom) ? "applications" : "register-company-account-type");
    };
    if( isAuth || isCom ) {
      hub.subscribe( "dialog-close", redirectFn, true);
      hub.send( "show-dialog",
                {ltitle: "register.company.dialog-title",
                 size: "small",
                 component: "ok-dialog",
                 componentParams: {
                   ltext: sprintf( "register.company.existing-user-is-%s",
                                 isAuth ? "authority" : "company")
                 }});
    } else {
      service.save();
      redirectFn();
    }
  };
};
