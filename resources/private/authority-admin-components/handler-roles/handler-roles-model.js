// Organization handler roles management.
// Note: There is always at least one handler role (general).
// Parameters:
//   organization: OrganizationModel instance.
LUPAPISTE.HandlerRolesModel = function( params ) {
  "use strict";
  var self = this;

  var enable = ko.observable( true );
 
  ko.utils.extend( self,
                   new LUPAPISTE.EnableComponentModel( _.defaults( params,
                                                                   {enable: enable}) ));

  self.disposedComputed( function() {
    enable( Boolean(lupapisteApp.models.globalAuthModel.ok("upsert-handler-role")));
  });
  
  var service = lupapisteApp.services.handlerService;

  self.roles = service.organizationHandlerRoles( params.organization );

  self.languages = service.organizationLanguages;

  self.nameHeader = function( lang ) {
    return sprintf( "%s (%s)",
                    loc( "users.table.name"),
                    loc( "lang." + lang));
  };

  self.removeRole = function( role ) {
    service.removeOrganizationHandlerRole( role.id );    
  };

  self.addRole = function() {
    service.addOrganizationHandlerRole();
  };

  // Empty names on partly filled row are required.
  self.isRequired = function( name, lang ) {
    return !_.trim( util.getIn( name, [lang]))
        && _.some( _.values( ko.mapping.toJS( name )), _.trim);
  }; 
};

