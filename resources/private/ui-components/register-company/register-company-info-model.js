LUPAPISTE.RegisterCompanyInfoModel = function() {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.companyRegistrationService;

  self.field = function( fieldName, cell ) {
    return _.defaults( service.field( fieldName),
                     {cell: cell || "text"});
  };

  self.languageField = function() {
    return _.defaults( service.field( "language"),
                     {cell: "select",
                     options: loc.getSupportedLanguages(),
                     optionsText: loc });
  };
};
