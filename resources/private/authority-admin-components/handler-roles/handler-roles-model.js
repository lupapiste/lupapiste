LUPAPISTE.HandlerRolesModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  var service = lupapisteApp.services.handlerService;

  var orgId = self.disposedPureComputed( _.partial( util.getIn,
                                                    params,
                                                    ["organization", "organizationId"]));
  self.roles = self.disposedComputed( function() {
    return _.reject( service.organizationHandlerRoles( orgId())(),
                     {disabled: true});
  });

  self.languages = self.disposedPureComputed( _.partial( service.organizationLanguages, orgId()));

  self.nameHeader = function( lang ) {
    return sprintf( "%s (%s)",
                    loc( "users.table.name"),
                    loc( "lang." + lang));
  };

  self.removeRole = function( role ) {
    service.removeOrganizationHandlerRole( orgId(), role.id );    
  };

  self.addRole = function() {
    service.addOrganizationHandlerRole( orgId());
  };
};

