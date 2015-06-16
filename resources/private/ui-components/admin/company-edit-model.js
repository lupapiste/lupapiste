LUPAPISTE.CompanyEditModel = function(params) {
  "use strict";

  var self = this;

  self.company = ko.mapping.fromJS(params.company);
  self.accountTypes = (LUPAPISTE.config.accountTypes).push("custom");


  self.saveCompany = function() {
  };
};
