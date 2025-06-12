// Container for notice-form-configuration components
// Parameters:
//  organization: OrganizationModel instance
LUPAPISTE.NoticeFormConfigurationGroupModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.organization = params.organization;
};
