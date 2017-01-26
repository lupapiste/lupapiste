LUPAPISTE.HandlerRolesModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  var service = lupapisteApp.services.handlerService;
  // var orgId = util.getIn( params, ["organization", "organizationId"]);
  // console.log( "orgId:", orgId );
  self.roles = ko.observableArray();
  var init = ko.observableArray();

  var orgId = self.disposedPureComputed( _.partial( util.getIn,
                                                    params,
                                                    ["organization", "organizationId"]));


  self.disposedComputed( function() {
    self.roles( _.map( service.organizationHandlerRoles( orgId())(),
                       function( role ) {
                         return _.assign( role,
                                          {name: _.reduce( role.name,
                                                           function( acc, v, k ) {
                                                             var value = ko.observable( v );
                                                             self.disposedSubscribe( value,
                                                                                     function( name) {
                                                                                       service.setOrganizationHandlerRole( orgId(),
                                                                                                                           role.id,
                                                                                                                           _.set( {}, k, name ) );
                                                                                     });
                                                             return _.set( acc, k, value );
                                                           }, {})});
                       }));    
  } );

  self.languages = self.disposedPureComputed( function() {
    return _.intersection( loc.getSupportedLanguages(),
                           _.keys( _.get( self.roles(),
                                        "0.name")));
  });

  self.nameHeader = function( lang ) {
    return sprintf( "%s (%s)",
                    loc( "users.table.name"),
                    loc( "lang." + lang));
  };

  self.removeRole = function( role ) {
    service.removeOrganizationHandlerRole( orgId(), role.id );    
  };

  self.addRole = function() {
    service.addOrganizationHandlerRole( orgId(), init );
  };
};
