// Organization handler roles management.
// Parameters:
//   organization: OrganizationModel instance.
LUPAPISTE.HandlerRolesModel = function( params ) {
  "use strict";
  var self = this;
 
  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( _.defaults( params,
                                                                         {enable: Boolean(lupapisteApp.models.globalAuthModel.ok("upsert-handler-role"))}) ));

  var service = lupapisteApp.services.handlerService;

  self.roles = service.organizationHandlerRoles( params.organization );

  self.languages = self.disposedPureComputed(  service.organizationLanguages );

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

  self.isRequired = function( name, lang ) {
    return !_.trim( util.getIn( name, [lang]))
        && _.some( _.values( ko.mapping.toJS( name )), _.trim);
  }; 
};

