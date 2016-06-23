LUPAPISTE.SutiAdminModel = function( params ) {
  "use strict";
  var self = this;

  self.organization = params.organization;

  self.enabled = self.organization.sutiEnabled;

  self.www = self.organization.sutiWww;
};
