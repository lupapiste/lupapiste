LUPAPISTE.RegisterCompanyInfoModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.companyRegistrationService;

  self.loggedIn = lupapisteApp.models.currentUser.id();

  self.userCell = self.loggedIn ? "span" : "text";

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
};
